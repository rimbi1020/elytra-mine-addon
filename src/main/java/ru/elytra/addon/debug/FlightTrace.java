package ru.elytra.addon.debug;

import ru.elytra.addon.flight.AbortReason;
import ru.elytra.addon.flight.FlightState;
import ru.elytra.addon.flight.PlayerState;
import ru.elytra.addon.flight.Vec3d;
import ru.elytra.addon.flight.VectorPlan;

public record FlightTrace(
    long tick,
    FlightState state,
    String phase,
    AbortReason abort,
    VectorPlan plan,
    PlayerState player,
    String note
) {
}