package com.internalrecorder.client;

import com.internalrecorder.capture.RecordingManager;
import com.internalrecorder.config.RecorderConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class RecordingScreen extends Screen {
    private final Screen parent;
    private ButtonWidget startButton;
    private ButtonWidget stopButton;
    private ButtonWidget pauseButton;
    private ButtonWidget fpsButton;
    private ButtonWidget crfButton;

    public RecordingScreen(Screen parent) {
        super(Text.literal("InternalRecorder"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = height / 2 - 72;
        startButton = addDrawableChild(ButtonWidget.builder(Text.literal("Start recording"), button -> {
            RecordingManager.start(client);
            refreshButtons();
        }).dimensions(centerX - 155, top, 150, 20).build());
        stopButton = addDrawableChild(ButtonWidget.builder(Text.literal("Stop recording"), button -> {
            RecordingManager.stop(client, "Recording saved.");
            refreshButtons();
        }).dimensions(centerX + 5, top, 150, 20).build());
        pauseButton = addDrawableChild(ButtonWidget.builder(Text.literal("Pause recording"), button -> {
            RecordingManager.togglePause(client);
            refreshButtons();
        }).dimensions(centerX - 155, top + 28, 310, 20).build());
        fpsButton = addDrawableChild(ButtonWidget.builder(Text.literal(""), button -> cycleFps()).dimensions(centerX - 155, top + 68, 150, 20).build());
        crfButton = addDrawableChild(ButtonWidget.builder(Text.literal(""), button -> cycleCrf()).dimensions(centerX + 5, top + 68, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close()).dimensions(centerX - 60, height - 32, 120, 20).build());
        refreshButtons();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        drawContext.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 112, 0xFFFFFFFF);
        RecorderConfig config = RecordingManager.config();
        drawContext.drawCenteredTextWithShadow(textRenderer, Text.literal("Resolution: " + config.outputWidth + " x " + config.outputHeight), width / 2, height / 2 + 22, 0xFFB8C2D0);
        drawContext.drawCenteredTextWithShadow(textRenderer, Text.literal("Output: " + config.outputDirectory), width / 2, height / 2 + 38, 0xFFB8C2D0);
        drawContext.drawCenteredTextWithShadow(textRenderer, Text.literal("FFmpeg: " + config.ffmpegPath), width / 2, height / 2 + 54, 0xFFB8C2D0);
        refreshButtons();
        super.render(drawContext, mouseX, mouseY, delta);
    }

    private void refreshButtons() {
        if (startButton == null) {
            return;
        }
        boolean recording = RecordingManager.isRecording();
        startButton.active = !recording;
        stopButton.active = recording;
        pauseButton.active = recording;
        pauseButton.setMessage(Text.literal(RecordingManager.isPaused() ? "Resume recording" : "Pause recording"));
        RecorderConfig config = RecordingManager.config();
        fpsButton.active = !recording;
        crfButton.active = !recording;
        fpsButton.setMessage(Text.literal("FPS: " + config.fps));
        crfButton.setMessage(Text.literal("CRF: " + config.crf));
    }

    private void cycleFps() {
        RecorderConfig config = RecordingManager.config();
        config.fps = config.fps < 30 ? 30 : config.fps < 60 ? 60 : config.fps < 120 ? 120 : 30;
        config.save();
        refreshButtons();
    }

    private void cycleCrf() {
        RecorderConfig config = RecordingManager.config();
        config.crf = config.crf >= 51 ? 0 : Math.min(51, config.crf + 4);
        config.save();
        refreshButtons();
    }
}
