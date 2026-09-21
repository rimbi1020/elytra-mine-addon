package ru.elytra.addon.flight;

public final class VectorPlan {
    private final Vec3d p0;
    private final Vec3d p1;
    private final Vec3d v1;
    private final Vec3d v2;
    private final int vector1Ticks;
    private final int vector2Ticks;

    private int tick;
    private boolean inV1;
    private boolean inV2;

    private VectorPlan(Vec3d p0, Vec3d p1, Vec3d v1, Vec3d v2, int vector1Ticks, int vector2Ticks) {
        this.p0 = p0;
        this.p1 = p1;
        this.v1 = v1;
        this.v2 = v2;
        this.vector1Ticks = vector1Ticks;
        this.vector2Ticks = vector2Ticks;
    }

    public static VectorPlan create(Vec3d p0, TargetSurface target, int offset, int vector1Ticks, int vector2Ticks) {
        Vec3d p1 = new Vec3d(target.pos().centerX(), target.topY() + offset, target.pos().centerZ());
        Vec3d v1 = p1.subtract(p0).normalize();
        Vec3d v2 = new Vec3d(0, -1, 0);
        return new VectorPlan(p0, p1, v1, v2, vector1Ticks, vector2Ticks);
    }

    public void begin() {
        inV1 = true;
        inV2 = false;
        tick = 0;
    }

    public Vec3d currentDirection() {
        if (inV1) return v1;
        if (inV2) return v2;
        return new Vec3d(0, 0, 0);
    }

    public boolean tickV1() {
        tick++;
        return tick >= vector1Ticks;
    }

    public void startV2() {
        inV1 = false;
        inV2 = true;
        tick = 0;
    }

    public boolean tickV2() {
        tick++;
        return tick >= vector2Ticks;
    }

    public boolean contains(Vec3d v) {
        return v.equals(v1) || v.equals(v2);
    }

    public boolean isDegenerate() {
        return v1.allZero() || v2.allZero();
    }

    public Vec3d p0() {
        return p0;
    }

    public Vec3d p1() {
        return p1;
    }

    public Vec3d v1() {
        return v1;
    }

    public Vec3d v2() {
        return v2;
    }

    public int tick() {
        return tick;
    }

    public boolean inV1() {
        return inV1;
    }

    public boolean inV2() {
        return inV2;
    }

    public boolean finished() {
        return inV2 && tick >= vector2Ticks;
    }
}