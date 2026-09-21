package ru.elytra.addon.flight;

public record ScanConfig(
    int offset,
    int scanUpDistance,
    int scanDownDistance,
    int scanRadius,
    boolean standableOnly,
    int safeFallDistance
) {
}