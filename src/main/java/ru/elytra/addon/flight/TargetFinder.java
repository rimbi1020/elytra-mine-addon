package ru.elytra.addon.flight;

import java.util.Optional;

public interface TargetFinder {
    Optional<TargetSurface> find(Vec3d feet, ScanConfig cfg);
}