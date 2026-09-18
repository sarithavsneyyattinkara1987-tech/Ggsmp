package com.internalrecorder.client;

import com.internalrecorder.capture.RecordingManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.Locale;

public final class RecordingHud {
    private RecordingHud() {
    }

    public static void render(DrawContext drawContext, RenderTickCounter tickCounter) {
        if (!RecordingManager.isRecording()) {
            return;
        }
        long totalSeconds = RecordingManager.elapsedMillis() / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        String timer = String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
        drawContext.drawTextWithShadow(
            net.minecraft.client.MinecraftClient.getInstance().textRenderer,
            Text.literal(RecordingManager.isPaused() ? "PAUSED " + timer : "REC " + timer),
            8,
            8,
            RecordingManager.isPaused() ? 0xFFFFCC33 : 0xFFFF3333
        );
    }
}