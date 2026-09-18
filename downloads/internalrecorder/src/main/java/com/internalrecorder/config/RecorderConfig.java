package com.internalrecorder.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RecorderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("internalrecorder.json");
    private static RecorderConfig current;

    public int outputWidth = 1920;
    public int outputHeight = 1080;
    public int fps = 60;
    public String outputDirectory = "recordings";
    public int crf = 19;
    public String ffmpegPath = "ffmpeg";

    public static void load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                current = GSON.fromJson(Files.readString(CONFIG_PATH), RecorderConfig.class);
            }
        } catch (IOException | JsonSyntaxException | NullPointerException exception) {
            System.err.println("[InternalRecorder] Could not read config; using defaults: " + exception.getMessage());
        }
        if (current == null) {
            current = new RecorderConfig();
        }
        current.sanitize();
        save();
        try {
            Files.createDirectories(FabricLoader.getInstance().getGameDir().resolve(current.outputDirectory));
        } catch (IOException exception) {
            System.err.println("[InternalRecorder] Could not create recordings directory: " + exception.getMessage());
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(current));
        } catch (IOException exception) {
            System.err.println("[InternalRecorder] Could not save config: " + exception.getMessage());
        }
    }

    public static RecorderConfig get() {
        if (current == null) {
            load();
        }
        return current;
    }

    public void sanitize() {
        outputWidth = clamp(outputWidth, 160, 7680);
        outputHeight = clamp(outputHeight, 90, 4320);
        fps = clamp(fps, 1, 240);
        crf = clamp(crf, 0, 51);
        if (outputDirectory == null || outputDirectory.isBlank()) {
            outputDirectory = "recordings";
        }
        if (ffmpegPath == null || ffmpegPath.isBlank()) {
            ffmpegPath = "ffmpeg";
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}