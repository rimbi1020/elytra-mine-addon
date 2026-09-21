package ru.elytra.addon.event;

public class ServerPositionCorrectionEvent {
    private static final ServerPositionCorrectionEvent INSTANCE = new ServerPositionCorrectionEvent();

    public static ServerPositionCorrectionEvent get() {
        return INSTANCE;
    }
}