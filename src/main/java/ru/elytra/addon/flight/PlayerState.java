package ru.elytra.addon.flight;

public record PlayerState(
    long worldTime,
    Vec3d pos,
    Vec3d velocity,
    boolean onGround,
    boolean fallFlying,
    float xRot,
    boolean horizontalCollision,
    boolean verticalCollision,
    boolean manualInput,
    boolean elytraEquipped,
    String dimension
) {
}