package ru.elytra.addon.flight;

public enum AbortReason {
    NONE,
    NO_ELYTRA,
    NO_TARGET,
    COLLISION,
    WORLD_CHANGED,
    TIMEOUT,
    MANUAL_INPUT,
    MODULE_DISABLED,
    SERVER_CORRECTION,
    CONFLICTING_MODULE
}