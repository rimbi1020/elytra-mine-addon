package ru.elytra.addon.flight;

public record Vec3d(double x, double y, double z) {
    public Vec3d add(Vec3d o) {
        return new Vec3d(x + o.x, y + o.y, z + o.z);
    }

    public Vec3d subtract(Vec3d o) {
        return new Vec3d(x - o.x, y - o.y, z - o.z);
    }

    public Vec3d scale(double s) {
        return new Vec3d(x * s, y * s, z * s);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public Vec3d normalize() {
        double len = length();
        if (len < 1.0E-6) return new Vec3d(0, 0, 0);
        return new Vec3d(x / len, y / len, z / len);
    }

    public boolean allZero() {
        return x == 0 && y == 0 && z == 0;
    }
}