package com.internalrecorder.capture;

import com.internalrecorder.audio.AudioCaptureBridge;
import com.internalrecorder.config.RecorderConfig;
import com.internalrecorder.encode.FfmpegEncoder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class RecordingManager {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final PboFrameCapturer CAPTURER = new PboFrameCapturer();
    private static FfmpegEncoder encoder;
    private static int lockedWidth;
    private static int lockedHeight;
    private static boolean recording;
    private static boolean paused;
    private static boolean resizeWarningSent;
    private static long recordingStartNanos;

    private RecordingManager() {
    }

    public static void initialize() {
        AudioCaptureBridge.initialize();
    }

    public static void toggle(MinecraftClient client) {
        if (recording) {
            stop(client, "Recording saved.");
        } else {
            start(client);
        }
    }

    public static void start(MinecraftClient client) {
        if (client.getWindow() == null) {
            return;
        }
        RecorderConfig config = RecorderConfig.get();
        lockedWidth = client.getWindow().getFramebufferWidth();
        lockedHeight = client.getWindow().getFramebufferHeight();
        if (lockedWidth <= 0 || lockedHeight <= 0) {
            notify(client, "Cannot start recording: invalid framebuffer size.");
            return;
        }

        Path directory = FabricLoader.getInstance().getGameDir().resolve(config.outputDirectory);
        try {
            Files.createDirectories(directory);
            Path output = directory.resolve("internalrecorder-" + LocalDateTime.now().format(FILE_TIME) + ".mp4");
            recordingStartNanos = System.nanoTime();
            encoder = new FfmpegEncoder(config, lockedWidth, lockedHeight, output, true);
            if (!encoder.start()) {
                encoder = null;
                notify(client, "FFmpeg was not found or could not be started. Check ffmpegPath in internalrecorder.json.");
                return;
            }
            CAPTURER.begin(lockedWidth, lockedHeight);
            recording = true;
            paused = false;
            resizeWarningSent = false;
            notify(client, "Recording started.");
        } catch (IOException exception) {
            notify(client, "Could not start recording: " + exception.getMessage());
            if (encoder != null) {
                encoder.stop();
                encoder = null;
            }
        }
    }

    public static void stop(MinecraftClient client, String message) {
        if (!recording) {
            return;
        }
        recording = false;
        paused = false;
        CAPTURER.close();
        if (encoder != null) {
            encoder.stop();
            encoder = null;
        }
        notify(client, message);
    }

    public static void captureFrame(MinecraftClient client) {
        if (!recording || paused || encoder == null) {
            return;
        }
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();
        if (width != lockedWidth || height != lockedHeight) {
            if (!resizeWarningSent) {
                resizeWarningSent = true;
                notify(client, "Recording stopped cleanly because the game resolution changed.");
            }
            stop(client, "Recording saved.");
            return;
        }
        FramePacket frame = CAPTURER.capture(System.nanoTime() - recordingStartNanos);
        if (frame != null) {
            encoder.submitVideo(frame);
        }
    }

    public static boolean isRecording() {
        return recording;
    }

    public static boolean isPaused() {
        return paused;
    }

    public static void togglePause(MinecraftClient client) {
        if (!recording) {
            return;
        }
        paused = !paused;
        notify(client, paused ? "Recording paused." : "Recording resumed.");
    }

    public static RecorderConfig config() {
        return RecorderConfig.get();
    }

    public static long elapsedMillis() {
        return encoder == null ? 0L : encoder.elapsedMillis();
    }

    private static void notify(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[InternalRecorder] " + message), false);
        } else {
            System.err.println("[InternalRecorder] " + message);
        }
    }
}