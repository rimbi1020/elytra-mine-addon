package ru.elytra.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import ru.elytra.addon.ElytraAddon;
import ru.elytra.addon.debug.FlightTrace;
import ru.elytra.addon.debug.PacketTrace;
import ru.elytra.addon.detection.BlockBreakDetector;
import ru.elytra.addon.detection.BreakObservation;
import ru.elytra.addon.event.ServerPositionCorrectionEvent;
import ru.elytra.addon.flight.AbortReason;
import ru.elytra.addon.flight.BlockPosI;
import ru.elytra.addon.flight.FlightConfig;
import ru.elytra.addon.flight.FlightController;
import ru.elytra.addon.flight.Motion;
import ru.elytra.addon.flight.PlayerState;
import ru.elytra.addon.flight.ScanConfig;
import ru.elytra.addon.flight.TargetSurface;
import ru.elytra.addon.flight.Vec3d;
import ru.elytra.addon.integration.MeteorModuleBridge;
import ru.elytra.addon.integration.MusheorBridge;

import java.util.List;
import java.util.Optional;

public class NukerElytraAssist extends Module {
    public enum ConflictPolicy {
        Block,
        Warn
    }

    private static final List<String> CONFLICTING_MODULES = List.of("elytrafly", "elytra-fly", "kekfly", "kekbounce");

    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgVector = settings.createGroup("Vector");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    private final Setting<Boolean> triggerOnlyWhenKekNukerActive = sgGeneral.add(new BoolSetting.Builder()
        .name("trigger-only-when-kek-nuker-active")
        .description("Only react while the Musheor KekNuker module is active.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> targetHeightOffset = sgTarget.add(new IntSetting.Builder()
        .name("target-height-offset")
        .description("Height of the point P1 above the top surface of the target block.")
        .defaultValue(3)
        .range(3, 4)
        .sliderRange(3, 4)
        .build()
    );

    private final Setting<Integer> scanUpDistance = sgTarget.add(new IntSetting.Builder()
        .name("scan-up-distance")
        .description("Max distance to scan upward for a target block.")
        .defaultValue(16)
        .range(8, 32)
        .build()
    );

    private final Setting<Integer> scanRadius = sgTarget.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("Horizontal radius around the current column when no block is found directly above.")
        .defaultValue(0)
        .range(0, 8)
        .build()
    );

    private final Setting<Boolean> targetStandableOnly = sgTarget.add(new BoolSetting.Builder()
        .name("target-standable-only")
        .description("Only accept targets with a standable top surface.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> vector1Ticks = sgVector.add(new IntSetting.Builder()
        .name("vector-1-ticks")
        .description("Number of ticks the first vector is applied.")
        .defaultValue(3)
        .range(2, 8)
        .build()
    );

    private final Setting<Integer> vector2Ticks = sgVector.add(new IntSetting.Builder()
        .name("vector-2-ticks")
        .description("Number of ticks the second vertical down vector is applied.")
        .defaultValue(2)
        .range(1, 4)
        .build()
    );

    private final Setting<Double> vectorSpeedLimit = sgVector.add(new DoubleSetting.Builder()
        .name("vector-speed-limit")
        .description("Speed limit for both vectors, in blocks per tick.")
        .defaultValue(0.2)
        .min(0.05)
        .max(0.6)
        .build()
    );

    private final Setting<Integer> maxCycleTicks = sgSafety.add(new IntSetting.Builder()
        .name("max-cycle-ticks")
        .description("Overall timeout of one short flight cycle in ticks.")
        .defaultValue(8)
        .range(6, 20)
        .build()
    );

    private final Setting<Boolean> abortOnManualInput = sgSafety.add(new BoolSetting.Builder()
        .name("abort-on-manual-input")
        .description("Abort immediately when the player provides manual input.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ConflictPolicy> conflictPolicy = sgSafety.add(new EnumSetting.Builder<ConflictPolicy>()
        .name("conflict-policy")
        .description("What to do when another flight module is active.")
        .defaultValue(ConflictPolicy.Block)
        .build()
    );

    private final Setting<Boolean> debugTrace = sgDebug.add(new BoolSetting.Builder()
        .name("debug-trace")
        .description("Trace every state transition and movement snapshot to the log.")
        .defaultValue(false)
        .build()
    );

    private final MeteorModuleBridge moduleBridge = new MeteorModuleBridge();
    private final MusheorBridge musheorBridge = new MusheorBridge(moduleBridge);
    private final BlockBreakDetector detector = new BlockBreakDetector();
    private final FlightController controller;

    private float savedXRot;
    private float savedYRot;
    private boolean savedRotations;
    private long lastTick;

    public NukerElytraAssist() {
        super(ElytraAddon.CATEGORY, "nuker-elytra-assist", "Runs a short two-vector elytra flight cycle after a confirmed block break under the player while KekNuker is active.");
        this.controller = new FlightController(
            this::gateCheck,
            new Motion() {
                @Override
                public void applyDirection(Vec3d direction, double speedLimit) {
                    motion(direction, speedLimit);
                }

                @Override
                public void setDivePitch() {
                    motionDive();
                }

                @Override
                public void recover() {
                    motionRecover();
                }
            },
            this::targetFinder,
            this::traceSink
        );
    }

    @Override
    public void onActivate() {
        if (musheorBridge.kekNukerExists()) {
            info("KekNuker module found: %s", musheorBridge.resolvedName());
        } else {
            warning("KekNuker module not found. kek-like=%s nuker-like=%s", moduleBridge.listModulesMatching("kek"), moduleBridge.listModulesMatching("nuker"));
        }
        controller.reset();
    }

    @Override
    public void onDeactivate() {
        controller.reset();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!Utils.canUpdate()) {
            controller.reset();
            return;
        }
        ClientPlayerEntity player = mc.player;
        ClientWorld level = mc.world;
        if (player == null || level == null) {
            controller.reset();
            return;
        }

        long worldTick = level.getTime();
        detector.tick(level);

        PlayerState state = sample(player, level, worldTick);
        FlightConfig flight = new FlightConfig(
            targetHeightOffset.get(),
            vector1Ticks.get(),
            vector2Ticks.get(),
            vectorSpeedLimit.get(),
            maxCycleTicks.get(),
            abortOnManualInput.get()
        );
        ScanConfig scan = new ScanConfig(
            targetHeightOffset.get(),
            scanUpDistance.get(),
            scanRadius.get(),
            targetStandableOnly.get()
        );

        controller.tick(state, flight, scan);
        lastTick = worldTick;
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        ClientPlayerEntity player = mc.player;
        ClientWorld level = mc.world;
        if (player == null || level == null) return;
        if (triggerOnlyWhenKekNukerActive.get() && !musheorBridge.kekNukerActive()) return;

        Optional<BreakObservation> observation = detector.onBlockUpdate(level, player, event, level.getTime(), dimension(), scanRadius.get());
        if (observation.isEmpty()) return;

        controller.observe(observation.get());

        if (debugTrace.get()) {
            BreakObservation o = observation.get();
            info("Confirmed break at (%d, %d, %d), source=%s", o.pos().x(), o.pos().y(), o.pos().z(), o.source());
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        controller.reset();
        detector.onWorldChange();
    }

    @EventHandler
    private void onServerSync(ServerPositionCorrectionEvent event) {
        controller.onServerCorrection();
    }

    @EventHandler
    private void onMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
        if (debugTrace.get()) PacketTrace.onMovementPacket(ElytraAddon.LOG, lastTick, "Pre");
    }

    @EventHandler
    private void onMovementPacketsPost(SendMovementPacketsEvent.Post event) {
        if (debugTrace.get()) PacketTrace.onMovementPacket(ElytraAddon.LOG, lastTick, "Post");
    }

    private Optional<AbortReason> gateCheck() {
        if (mc.player == null || mc.world == null) return Optional.of(AbortReason.MODULE_DISABLED);
        if (triggerOnlyWhenKekNukerActive.get() && !musheorBridge.kekNukerActive()) {
            return Optional.of(AbortReason.MODULE_DISABLED);
        }
        if (!mc.player.isGliding() || !hasElytra()) {
            return Optional.of(AbortReason.NO_ELYTRA);
        }
        Optional<String> conflict = activeConflict();
        if (conflict.isPresent() && conflictPolicy.get() == ConflictPolicy.Block) {
            warning("Start blocked: conflicting module '%s' is active.", conflict.get());
            return Optional.of(AbortReason.CONFLICTING_MODULE);
        }
        return Optional.empty();
    }

    private void motion(Vec3d direction, double speedLimit) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        saveRotations(player);
        player.setVelocity(direction.x() * speedLimit, direction.y() * speedLimit, direction.z() * speedLimit);
    }

    private void motionDive() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        saveRotations(player);
        player.setPitch(90.0f);
    }

    private void motionRecover() {
        ClientPlayerEntity player = mc.player;
        if (player == null || !savedRotations) return;
        player.setPitch(savedXRot);
        player.setYaw(savedYRot);
        savedRotations = false;
    }

    private void saveRotations(ClientPlayerEntity player) {
        if (savedRotations) return;
        savedXRot = player.getPitch();
        savedYRot = player.getYaw();
        savedRotations = true;
    }

    private Optional<TargetSurface> targetFinder(Vec3d feet, ScanConfig cfg) {
        ClientWorld level = mc.world;
        if (level == null) return Optional.empty();

        int px = BlockPosI.floor(feet.x());
        int py = BlockPosI.floor(feet.y());
        int pz = BlockPosI.floor(feet.z());

        for (int y = py; y <= py + cfg.scanUpDistance(); y++) {
            BlockPosI at = new BlockPosI(px, y, pz);
            if (isStandable(level, at, cfg)) return Optional.of(surface(at));
        }

        if (cfg.scanRadius() <= 0) return Optional.empty();

        TargetSurface best = null;
        double bestDistance = 0;
        for (int r = 1; r <= cfg.scanRadius(); r++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int y = py; y <= py + cfg.scanUpDistance(); y++) {
                        BlockPosI at = new BlockPosI(px + dx, y, pz + dz);
                        if (!isStandable(level, at, cfg)) continue;
                        TargetSurface candidate = surface(at);
                        double distance = candidateDistance(feet, candidate);
                        if (best == null || distance < bestDistance) {
                            best = candidate;
                            bestDistance = distance;
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean isStandable(ClientWorld level, BlockPosI at, ScanConfig cfg) {
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        if (!cfg.standableOnly()) return true;
        return shape.getMax(Direction.Axis.Y) >= 0.5;
    }

    private double candidateDistance(Vec3d feet, TargetSurface candidate) {
        double dx = candidate.pos().centerX() - feet.x();
        double dz = candidate.pos().centerZ() - feet.z();
        double dy = candidate.topY() - feet.y();
        return dx * dx + dy * dy + dz * dz;
    }

    private TargetSurface surface(BlockPosI at) {
        return new TargetSurface(at, at.y() + 1.0, true);
    }

    private Optional<String> activeConflict() {
        for (Module module : Modules.get().getActive()) {
            String normalized = MeteorModuleBridge.normalize(module.name);
            for (String pattern : CONFLICTING_MODULES) {
                if (normalized.contains(pattern)) return Optional.of(module.name);
            }
        }
        return Optional.empty();
    }

    private void traceSink(FlightTrace trace) {
        if (!debugTrace.get()) return;
        PlayerState p = trace.player();
        if (p == null) {
            ElytraAddon.LOG.info(String.format("[trace] tick={%d} state={%s} abort={%s} note={%s}", trace.tick(), trace.state(), trace.abort(), trace.note()));
            return;
        }
        ElytraAddon.LOG.info(String.format(
            "[trace] tick={%d} state={%s} abort={%s} pos=(%.3f,%.3f,%.3f) vel=(%.3f,%.3f,%.3f) fallFlying={%b} onGround={%b} xRot={%.1f} note={%s}",
            trace.tick(), trace.state(), trace.abort(),
            p.pos().x(), p.pos().y(), p.pos().z(),
            p.velocity().x(), p.velocity().y(), p.velocity().z(),
            p.fallFlying(), p.onGround(), p.xRot(), trace.note()
        ));
    }

    private PlayerState sample(ClientPlayerEntity player, ClientWorld level, long worldTick) {
        var pos = player.getEntityPos();
        var vel = player.getVelocity();
        return new PlayerState(
            worldTick,
            new Vec3d(pos.x, pos.y, pos.z),
            new Vec3d(vel.x, vel.y, vel.z),
            player.isOnGround(),
            player.isGliding(),
            player.getPitch(),
            player.horizontalCollision,
            player.verticalCollision,
            manualInput(),
            hasElytra(),
            dimension()
        );
    }

    private boolean manualInput() {
        ClientPlayerEntity player = mc.player;
        if (player == null || player.input == null) return false;
        PlayerInput in = player.input.playerInput;
        return in.jump() || in.sneak() || in.forward() || in.backward() || in.left() || in.right();
    }

    private boolean hasElytra() {
        if (mc.player == null) return false;
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return chest.getItem() == Items.ELYTRA;
    }

    private String dimension() {
        return mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
    }
}