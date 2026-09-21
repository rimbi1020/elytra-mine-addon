package ru.elytra.addon.flight;

import ru.elytra.addon.debug.FlightTrace;
import ru.elytra.addon.detection.BreakObservation;

import java.util.Optional;

public final class FlightController {
    public static final int WAIT_SYNC_TICKS = 10;
    public static final int TARGET_SEARCH_TICKS = 20;
    public static final int ARMED_MAX_TICKS = 12;

    private final Gate gate;
    private final Motion motion;
    private final TargetFinder targetFinder;
    private final TraceSink traceSink;
    private final Runnable onVector1Start;
    private final PipelineLog pipelineLog;

    private FlightState state = FlightState.IDLE;
    private AbortReason abortReason = AbortReason.NONE;
    private String lastNote = "";
    private VectorPlan plan;
    private BreakObservation source;
    private PlayerState lastPlayer;
    private long tick;
    private int stateTicks;

    public FlightController(Gate gate, Motion motion, TargetFinder targetFinder, TraceSink traceSink, Runnable onVector1Start, PipelineLog pipelineLog) {
        this.gate = gate;
        this.motion = motion;
        this.targetFinder = targetFinder;
        this.traceSink = traceSink;
        this.onVector1Start = onVector1Start;
        this.pipelineLog = pipelineLog;
    }

    public void observe(BreakObservation observation) {
        if (state != FlightState.IDLE) return;
        this.source = observation;
        pipelineLog.log(String.format("observe break=(%d,%d,%d) tick=%d source=%s", observation.pos().x(), observation.pos().y(), observation.pos().z(), observation.worldTime(), observation.source()));
        enter(FlightState.ARMED);
    }

    public void tick(PlayerState player, FlightConfig flight, ScanConfig scan) {
        this.tick++;
        this.lastPlayer = player;

        if (state == FlightState.IDLE) {
            trace("IDLE", "waiting");
            return;
        }

        if (!player.dimension().equals(source.dimension())) {
            abort(AbortReason.WORLD_CHANGED, "dimension changed");
            return;
        }

        switch (state) {
            case ARMED -> armTick();
            case TARGET -> targetTick(flight, scan);
            case VECTOR_1 -> vector1Tick(flight);
            case VECTOR_2 -> vector2Tick(flight);
            case RECOVER -> recoverTick();
            case WAIT_SERVER_SYNC -> waitSyncTick();
            default -> abort(AbortReason.MODULE_DISABLED, "unexpected state " + state);
        }

        stateTicks++;
        trace(phaseLabel(), "stateTicks=" + stateTicks);
    }

    public void onServerCorrection() {
        if (state == FlightState.IDLE) return;
        abort(AbortReason.SERVER_CORRECTION, "server position correction received");
    }

    public void onWorldChanged() {
        reset();
    }

    public void reset() {
        if (state != FlightState.IDLE) motion.recover();
        state = FlightState.IDLE;
        abortReason = AbortReason.NONE;
        lastNote = "";
        plan = null;
        source = null;
        stateTicks = 0;
    }

    public boolean isActive() {
        return state != FlightState.IDLE;
    }

    public FlightState state() {
        return state;
    }

    public AbortReason abortReason() {
        return abortReason;
    }

    public VectorPlan plan() {
        return plan;
    }

    private void armTick() {
        Optional<AbortReason> gateResult = gate.check();
        if (gateResult.isPresent()) {
            abort(gateResult.get(), "gate check failed");
            return;
        }
        if (!lastPlayer.fallFlying()) {
            if (stateTicks >= ARMED_MAX_TICKS) {
                abort(AbortReason.NO_ELYTRA, "glide did not start in time");
            }
            return;
        }
        enter(FlightState.TARGET);
    }

    private void targetTick(FlightConfig flight, ScanConfig scan) {
        if (stateTicks >= TARGET_SEARCH_TICKS) {
            abort(AbortReason.TIMEOUT, "target search exceeded limit");
            return;
        }
        Optional<TargetSurface> target = targetFinder.find(lastPlayer.pos(), scan);
        if (target.isEmpty()) {
            abort(AbortReason.NO_TARGET, "no target found (safe drop or nothing above/below)");
            return;
        }
        plan = VectorPlan.create(lastPlayer.pos(), target.get(), flight.offset(), flight.vector1Ticks(), flight.vector2Ticks());
        if (plan.isDegenerate()) {
            abort(AbortReason.NO_TARGET, "degenerate plan");
            return;
        }
        pipelineLog.log(String.format("target kind=%s pos=(%d,%d,%d) topY=%.1f p1=(%.2f,%.2f,%.2f)",
            target.get().kind(), target.get().pos().x(), target.get().pos().y(), target.get().pos().z(),
            target.get().topY(), plan.p1().x(), plan.p1().y(), plan.p1().z()));
        plan.begin();
        stateTicks = 0;
        enter(FlightState.VECTOR_1);
    }

    private void vector1Tick(FlightConfig flight) {
        if (plan.inV1() && plan.tick() == 0) onVector1Start.run();
        if (flight.abortOnManualInput() && lastPlayer.manualInput()) {
            abort(AbortReason.MANUAL_INPUT, "manual input during V1");
            return;
        }
        if (checkAbortCommon(flight)) return;
        motion.applyDirection(plan.v1(), flight.speedLimit());
        if (plan.tickV1()) {
            motion.setDivePitch();
            plan.startV2();
            stateTicks = 0;
            enter(FlightState.VECTOR_2);
        }
    }

    private void vector2Tick(FlightConfig flight) {
        if (flight.abortOnManualInput() && lastPlayer.manualInput()) {
            abort(AbortReason.MANUAL_INPUT, "manual input during V2");
            return;
        }
        if (checkAbortCommon(flight)) return;
        motion.applyDirection(plan.v2(), flight.speedLimit());
        if (plan.tickV2()) {
            motion.recover();
            stateTicks = 0;
            enter(FlightState.RECOVER);
        }
    }

    private boolean checkAbortCommon(FlightConfig flight) {
        if (!lastPlayer.fallFlying() || !lastPlayer.elytraEquipped()) {
            abort(AbortReason.NO_ELYTRA, "elytra state lost during flight");
            return true;
        }
        if (lastPlayer.horizontalCollision() || lastPlayer.verticalCollision()) {
            abort(AbortReason.COLLISION, "collision during vector phase");
            return true;
        }
        if (lastPlayer.worldTime() - source.worldTime() > flight.maxCycleTicks()) {
            abort(AbortReason.TIMEOUT, "cycle exceeded max-cycle-ticks");
            return true;
        }
        return false;
    }

    private void recoverTick() {
        if (stateTicks >= 1) {
            stateTicks = 0;
            enter(FlightState.WAIT_SERVER_SYNC);
        }
    }

    private void waitSyncTick() {
        if (stateTicks >= WAIT_SYNC_TICKS) {
            enter(FlightState.IDLE);
        }
    }

    private void abort(AbortReason reason, String note) {
        pipelineLog.log("abort reason=" + reason + " note=" + note);
        abortReason = reason;
        lastNote = note;
        motion.recover();
        plan = null;
        source = null;
        stateTicks = 0;
        enter(FlightState.IDLE);
    }

    private void enter(FlightState next) {
        state = next;
        stateTicks = 0;
        pipelineLog.log("enter " + next);
    }

    private String phaseLabel() {
        if (plan == null) return state.name();
        if (plan.inV1()) return "VECTOR_1/" + plan.tick();
        if (plan.inV2()) return "VECTOR_2/" + plan.tick();
        return state.name();
    }

    private void trace(String phase, String note) {
        if (traceSink == null) return;
        traceSink.accept(new FlightTrace(tick, state, phase, abortReason, plan, lastPlayer, lastNote.isEmpty() ? note : lastNote + " | " + note));
    }
}
