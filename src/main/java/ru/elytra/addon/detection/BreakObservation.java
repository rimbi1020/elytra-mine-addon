package ru.elytra.addon.detection;

import ru.elytra.addon.flight.BlockPosI;

public record BreakObservation(BlockPosI pos, long worldTime, String dimension, String source) {
}