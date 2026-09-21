package ru.elytra.addon.flight;

@FunctionalInterface
public interface PipelineLog {
    void log(String message);
}