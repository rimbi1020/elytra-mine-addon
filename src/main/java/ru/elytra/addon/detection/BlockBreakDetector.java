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

public class BlockBreakDetector {
    private static final int DEDUPE_TICKS = 10;

    private final Map<BlockPosI, Long> recentBreaks = new HashMap<>();
    private String lastDimension = "";

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
            return Optional.empty();
        }

        boolean wasColliding = !oldState.getCollisionShape(level, pos).isEmpty();
        boolean stillColliding = !newState.getCollisionShape(level, pos).isEmpty();
        if (!wasColliding || stillColliding) {
            return Optional.empty();
        }

        BlockPosI key = key(pos);
        Long previous = recentBreaks.get(key);
        if (previous != null && worldTick - previous < DEDUPE_TICKS) {
            return Optional.empty();
        }

        recentBreaks.put(key, worldTick);
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