package ru.elytra.addon.debug;

import ru.elytra.addon.flight.Vec3d;

public record MovementSnapshot(
    long tick,
    Vec3d pos,
    Vec3d velocity,
    float yaw,
    float pitch,
    boolean onGround,
    boolean fallFlying,
    String phase
) {
}