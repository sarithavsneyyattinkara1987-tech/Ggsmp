package com.internalrecorder.mixin;

import com.internalrecorder.capture.RecordingManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/util/Window;swapBuffers(Lnet/minecraft/client/util/tracy/TracyFrameCapturer;)V"
        )
    )
    private void internalrecorder$captureAfterHud(boolean tick, CallbackInfo callbackInfo) {
        RecordingManager.captureFrame((MinecraftClient) (Object) this);
    }
}