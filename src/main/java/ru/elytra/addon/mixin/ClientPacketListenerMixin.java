package ru.elytra.addon.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.elytra.addon.event.ServerPositionCorrectionEvent;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"))
    private void elytra$onServerPositionCorrection(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        MeteorClient.EVENT_BUS.post(ServerPositionCorrectionEvent.get());
    }
}