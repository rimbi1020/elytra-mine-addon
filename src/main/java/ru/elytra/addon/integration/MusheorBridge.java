package ru.elytra.addon.integration;

import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.Optional;

public class MusheorBridge {
    private final MeteorModuleBridge bridge;
    private String resolvedKekNuker;

    public MusheorBridge(MeteorModuleBridge bridge) {
        this.bridge = bridge;
    }

    public boolean kekNukerExists() {
        return findKekNuker().isPresent();
    }

    public boolean kekNukerActive() {
        return findKekNuker().map(Module::isActive).orElse(false);
    }

    public String resolvedName() {
        return resolvedKekNuker != null ? resolvedKekNuker : "(not found)";
    }

    private Optional<Module> findKekNuker() {
        if (resolvedKekNuker != null) return bridge.findByName(resolvedKekNuker);

        for (String name : bridge.listModulesMatching("nuker")) {
            Optional<Module> found = bridge.findByName(name);
            if (found.isPresent()) {
                resolvedKekNuker = name;
                return found;
            }
        }
        for (String name : bridge.listModulesMatching("kek")) {
            Optional<Module> found = bridge.findByName(name);
            if (found.isPresent()) {
                resolvedKekNuker = name;
                return found;
            }
        }
        return Optional.empty();
    }
}