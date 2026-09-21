package ru.elytra.addon.flight;

public interface Motion {
    void applyDirection(Vec3d direction, double speedLimit);

    void setDivePitch();

    void recover();
}