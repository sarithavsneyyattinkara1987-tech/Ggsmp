package com.internalrecorder.mixin;

import com.internalrecorder.audio.AudioCaptureBridge;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
    @Inject(
        method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
        at = @At("HEAD")
    )
    private void internalrecorder$observeSoundStart(
        SoundInstance sound,
        CallbackInfoReturnable<SoundSystem.PlayResult> callbackInfo
    ) {
        AudioCaptureBridge.onSoundStarted(sound);
    }
}