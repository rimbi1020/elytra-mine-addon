package ru.elytra.addon.flight;

public record ScanConfig(int offset, int scanUpDistance, int scanRadius, boolean standableOnly) {
}