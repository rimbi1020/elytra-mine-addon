package ru.elytra.addon.integration;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class SilentFirework {
    public boolean boost() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return false;
        if (!player.isGliding()) return false;

        FindItemResult rocket = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!rocket.found()) return false;

        if (rocket.isOffhand()) {
            mc.interactionManager.interactItem(player, Hand.OFF_HAND);
        } else {
            InvUtils.swap(rocket.slot(), true);
            mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
            InvUtils.swapBack();
        }
        return true;
    }
}