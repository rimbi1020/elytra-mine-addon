package ru.elytra.addon.flight;

public record FlightConfig(
    int offset,
    int vector1Ticks,
    int vector2Ticks,
    double speedLimit,
    int maxCycleTicks,
    boolean abortOnManualInput
) {
}