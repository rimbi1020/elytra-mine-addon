package ru.elytra.addon.flight;

import ru.elytra.addon.debug.FlightTrace;

public interface TraceSink {
    void accept(FlightTrace trace);
}