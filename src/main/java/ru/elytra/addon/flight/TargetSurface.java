package ru.elytra.addon.flight;

public record TargetSurface(BlockPosI pos, double topY, boolean standable, TargetKind kind) {
    public TargetSurface(BlockPosI pos, double topY, boolean standable) {
        this(pos, topY, standable, TargetKind.ABOVE);
    }
}