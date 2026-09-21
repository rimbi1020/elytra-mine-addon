package ru.elytra.addon.flight;

import org.junit.jupiter.api.Test;
import ru.elytra.addon.detection.BreakObservation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightControllerTest {
    private static final FlightConfig CONFIG = new FlightConfig(3, 3, 2, 0.2, 8, true);
    private static final ScanConfig SCAN = new ScanConfig(3, 30, 30, 0, true, 10);
    private static final String DIMENSION = "minecraft:overworld";

    private final List<Vec3d> applied = new ArrayList<>();
    private final List<String> pipeline = new ArrayList<>();
    private int diveCalls;
    private int recoverCalls;
    private int vector1StartCalls;

    private Optional<TargetSurface> target = Optional.of(new TargetSurface(new BlockPosI(0, 10, 0), 11.0, true));
    private AbortReason gateResult;
    private boolean manualInput;
    private Vec3d playerPos = new Vec3d(0.5, 10.0, 0.5);
    private String playerDimension = DIMENSION;

    private FlightController controller() {
        Gate gate = () -> gateResult == null ? Optional.empty() : Optional.of(gateResult);
        Motion motion = new Motion() {
            @Override
            public void applyDirection(Vec3d direction, double speedLimit) {
                applied.add(direction);
            }

            @Override
            public void setDivePitch() {
                diveCalls++;
            }

            @Override
            public void recover() {
                recoverCalls++;
            }
        };
        TargetFinder finder = (feet, cfg) -> target;
        return new FlightController(gate, motion, finder, trace -> {
        }, () -> vector1StartCalls++, pipeline::add);
    }

    private void advance(FlightController controller, int ticks, long startTime) {
        long t = startTime;
        for (int i = 0; i < ticks && controller.state() != FlightState.IDLE; i++) {
            t++;
            controller.tick(player(t), CONFIG, SCAN);
        }
    }

    private PlayerState player(long time) {
        return new PlayerState(time, playerPos, new Vec3d(0, 0, 0), false, true, 0,
            false, false, manualInput, true, playerDimension);
    }

    private BreakObservation observation(long time) {
        return new BreakObservation(new BlockPosI(0, 9, 0), time, DIMENSION, "test");
    }

    @Test
    void fullCycleRunsBothVectorsAndReturnsToIdle() {
        FlightController controller = controller();
        controller.observe(observation(100));
        assertTrue(controller.isActive());

        advance(controller, 30, 100);

        assertEquals(FlightState.IDLE, controller.state());
        assertEquals(AbortReason.NONE, controller.abortReason());
        assertEquals(5, applied.size());
        assertEquals(1, diveCalls);
        assertEquals(1, recoverCalls);
        assertFalse(controller.isActive());
    }

    @Test
    void secondObservationWhileActiveIsIgnored() {
        FlightController controller = controller();
        controller.observe(observation(100));
        controller.observe(observation(101));

        advance(controller, 30, 100);

        assertEquals(5, applied.size(), "second observation must not restart the cycle");
        assertEquals(1, recoverCalls);
    }

    @Test
    void manualInputAbortsDuringV1() {
        FlightController controller = controller();
        controller.observe(observation(100));

        manualInput = false;
        advance(controller, 5, 100);

        manualInput = true;
        controller.tick(player(106), CONFIG, SCAN);

        assertEquals(FlightState.IDLE, controller.state());
        assertEquals(AbortReason.MANUAL_INPUT, controller.abortReason());
    }

    @Test
    void vector1StartHookFiresOncePerCycle() {
        FlightController controller = controller();
        controller.observe(observation(100));

        advance(controller, 30, 100);

        assertEquals(FlightState.IDLE, controller.state());
        assertEquals(1, vector1StartCalls, "firework hook must fire exactly once at the start of V1");
    }

    @Test
    void pipelineLogsStateTransitionsAndAborts() {
        target = Optional.empty();
        FlightController controller = controller();
        controller.observe(observation(100));

        advance(controller, 5, 100);

        assertTrue(pipeline.stream().anyMatch(line -> line.contains("enter ARMED")), "must log observe->ARMED, got: " + pipeline);
        assertTrue(pipeline.stream().anyMatch(line -> line.startsWith("abort reason=")), "must log the abort reason, got: " + pipeline);
    }

    @Test
    void missingTargetAborts() {
        target = Optional.empty();
        FlightController controller = controller();
        controller.observe(observation(100));

        advance(controller, 5, 100);

        assertEquals(FlightState.IDLE, controller.state());
        assertEquals(AbortReason.NO_TARGET, controller.abortReason());
    }

    @Test
    void noElytraGateAbortsArm() {
        gateResult = AbortReason.NO_ELYTRA;
        FlightController controller = controller();
        controller.observe(observation(100));

        advance(controller, 5, 100);

        assertEquals(FlightState.IDLE, controller.state());
        assertEquals(AbortReason.NO_ELYTRA, controller.abortReason());
    }

    @Test
    void serverCorrectionDuringFlightAborts() {
        FlightController controller = controller();
        controller.observe(observation(100));

        advance(controller, 5, 100);
        assertTrue(controller.isActive());

        controller.onServerCorrection();

        assertEquals(FlightState.IDLE, controller.state());
        assertEquals(AbortReason.SERVER_CORRECTION, controller.abortReason());
    }

    @Test
    void dimensionChangeAborts() {
        FlightController controller = controller();
        controller.observe(observation(100));

        playerDimension = "minecraft:the_nether";
        advance(controller, 5, 100);

        assertEquals(FlightState.IDLE, controller.state());
        assertEquals(AbortReason.WORLD_CHANGED, controller.abortReason());
    }

    @Test
    void cycleTimeoutAborts() {
        FlightController controller = controller();
        controller.observe(observation(100));

        long t = 100;
        for (int i = 0; i < 5 && controller.state() != FlightState.IDLE; i++) {
            t += 100;
            controller.tick(player(t), CONFIG, SCAN);
        }

        assertEquals(FlightState.IDLE, controller.state());
        assertEquals(AbortReason.TIMEOUT, controller.abortReason());
    }

    @Test
    void conflictingModuleGateAborts() {
        gateResult = AbortReason.CONFLICTING_MODULE;
        FlightController controller = controller();
        controller.observe(observation(100));

        advance(controller, 5, 100);

        assertEquals(FlightState.IDLE, controller.state());
        assertEquals(AbortReason.CONFLICTING_MODULE, controller.abortReason());
    }
}