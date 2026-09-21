package ru.elytra.addon.flight;

public record BlockPosI(int x, int y, int z) {
    public static BlockPosI ofFeet(double x, double y, double z) {
        return new BlockPosI(floor(x), floor(y), floor(z));
    }

    public static int floor(double v) {
        return (int) Math.floor(v);
    }

    public double centerX() {
        return x + 0.5;
    }

    public double centerZ() {
        return z + 0.5;
    }
}