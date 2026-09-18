package com.internalrecorder.mixin;

import com.internalrecorder.client.RecordingScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin {
    @Shadow
    protected int width;

    @Shadow
    protected int height;

    @Shadow
    protected abstract <T extends Element & Drawable & Selectable> T addDrawableChild(T drawable);

    @Inject(method = "init", at = @At("TAIL"))
    private void internalrecorder$addCameraButton(CallbackInfo callbackInfo) {
        GameMenuScreen menu = (GameMenuScreen) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        addDrawableChild(
            ButtonWidget.builder(Text.literal("CAM"), button -> client.setScreen(new RecordingScreen(menu)))
            .dimensions(width - 110, height - 28, 100, 20)
                .build()
        );
    }
}
