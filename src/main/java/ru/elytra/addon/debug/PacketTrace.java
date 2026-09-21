package ru.elytra.addon.debug;

import org.slf4j.Logger;

public final class PacketTrace {
    private PacketTrace() {
    }

    public static void onMovementPacket(Logger log, long worldTick, String event) {
        log.info("[packet] tick={} event={}", worldTick, event);
    }
}