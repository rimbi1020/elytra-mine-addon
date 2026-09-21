package ru.elytra.addon;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import ru.elytra.addon.modules.NukerElytraAssist;

public class ElytraAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Elytra");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Elytra Kek Assist addon");
        Modules.get().add(new NukerElytraAssist());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "ru.elytra.addon";
    }
}