package ru.elytra.addon.integration;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MeteorModuleBridge {
    private final Map<String, Module> resolved = new HashMap<>();

    public Optional<Module> findByName(String name) {
        String key = normalize(name);
        Module cached = resolved.get(key);
        if (cached != null) return Optional.of(cached);
        for (Module module : Modules.get().getAll()) {
            if (normalize(module.name).equals(key)) {
                resolved.put(key, module);
                return Optional.of(module);
            }
        }
        return Optional.empty();
    }

    public boolean isActive(String name) {
        return findByName(name).filter(Module::isActive).isPresent();
    }

    public List<String> listModulesMatching(String fragment) {
        String needle = normalize(fragment);
        List<String> out = new ArrayList<>();
        for (Module module : Modules.get().getAll()) {
            if (normalize(module.name).contains(needle)) out.add(module.name);
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    public static String normalize(String s) {
        return s.toLowerCase().replace(" ", "-");
    }
}