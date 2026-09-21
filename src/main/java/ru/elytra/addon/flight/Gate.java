package ru.elytra.addon.flight;

import java.util.Optional;

public interface Gate {
    Optional<AbortReason> check();
}