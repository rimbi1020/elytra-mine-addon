package ru.elytra.addon.detection;

import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import ru.elytra.addon.flight.BlockPosI;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class BlockBreakDetector {
    private static final int DEDUPE_TICKS = 10;

    private final Map<BlockPosI, Long> recentBreaks = new HashMap<>();
    private String lastDimension = "";
    private Consumer<String> logger = message -> {
    };

    public void setLogger(Consumer<String> logger) {
        this.logger = logger == null ? message -> {
        } : logger;
    }

    private void log(String message) {
        logger.accept(message);
    }

    public void onWorldChange() {
        recentBreaks.clear();
        lastDimension = "";
    }

    public void tick(ClientWorld level) {
        if (level == null) return;
        long now = level.getTime();
        recentBreaks.entrySet().removeIf(e -> now - e.getValue() > DEDUPE_TICKS);
    }

    public Optional<BreakObservation> onBlockUpdate(ClientWorld level, ClientPlayerEntity player, BlockUpdateEvent event, long worldTick, String dimension, int radius) {
        if (player == null || level == null) return Optional.empty();

        BlockPos pos = event.pos;
        BlockState oldState = event.oldState;
        BlockState newState = event.newState;

        if (!dimension.equals(lastDimension)) {
            recentBreaks.clear();
            lastDimension = dimension;
        }

        BlockPosI feet = BlockPosI.ofFeet(player.getX(), player.getY(), player.getZ());
        if (!inSupportZone(pos, feet, radius)) {
            log(String.format("block update (%d, %d, %d) rejected: out of support zone (feet=%d,%d,%d radius=%d)", pos.getX(), pos.getY(), pos.getZ(), feet.x(), feet.y(), feet.z(), radius));
            return Optional.empty();
        }

        boolean wasColliding = !oldState.getCollisionShape(level, pos).isEmpty();
        boolean stillColliding = !newState.getCollisionShape(level, pos).isEmpty();
        if (!wasColliding || stillColliding) {
            log(String.format("block update (%d, %d, %d) rejected: no collision change (wasColliding=%b stillColliding=%b)", pos.getX(), pos.getY(), pos.getZ(), wasColliding, stillColliding));
            return Optional.empty();
        }

        BlockPosI key = key(pos);
        Long previous = recentBreaks.get(key);
        if (previous != null && worldTick - previous < DEDUPE_TICKS) {
            log(String.format("block update (%d, %d, %d) rejected: deduped (last=%d)", pos.getX(), pos.getY(), pos.getZ(), previous));
            return Optional.empty();
        }

        recentBreaks.put(key, worldTick);
        log(String.format("confirmed break (%d, %d, %d) source=world-update", pos.getX(), pos.getY(), pos.getZ()));
        return Optional.of(new BreakObservation(key, worldTick, dimension, "world-update"));
    }

    private boolean inSupportZone(BlockPos pos, BlockPosI feet, int radius) {
        int dx = pos.getX() - feet.x();
        int dz = pos.getZ() - feet.z();
        int dy = pos.getY() - feet.y();
        if (Math.abs(dx) > radius || Math.abs(dz) > radius) return false;
        return dy >= -1 && dy <= 0;
    }

    private BlockPosI key(BlockPos pos) {
        return new BlockPosI(pos.getX(), pos.getY(), pos.getZ());
    }
}