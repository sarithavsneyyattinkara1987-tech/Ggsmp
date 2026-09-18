package com.internalrecorder.mixin;

import com.internalrecorder.audio.AudioCaptureBridge;
import net.minecraft.client.sound.StaticSound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

@Mixin(StaticSound.class)
public abstract class StaticSoundMixin {
    @Shadow
    private ByteBuffer sample;

    @Shadow
    private javax.sound.sampled.AudioFormat format;

    @Inject(
        method = "getStreamBufferPointer",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/openal/AL10;alBufferData(IILjava/nio/ByteBuffer;I)V"
        )
    )
    private void internalrecorder$captureDecodedPcm(CallbackInfoReturnable<OptionalInt> cir) {
        if (sample == null || format == null) {
            return;
        }
        ByteBuffer duplicate = sample.duplicate();
        AudioCaptureBridge.onDecodedPcm(duplicate, format);
    }
}
