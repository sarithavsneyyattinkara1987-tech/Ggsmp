package com.internalrecorder.encode;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FfmpegEncoderTest {
    @Test
    void usesAbsoluteSystemPropertyPath() throws IOException {
        Path executable = Files.createTempFile("internalrecorder-ffmpeg", "");
        try {
            executable.toFile().setExecutable(true);
            String previous = System.getProperty(FfmpegEncoder.FFMPEG_PATH_PROPERTY);
            System.setProperty(FfmpegEncoder.FFMPEG_PATH_PROPERTY, executable.toString());
            try {
                assertEquals(executable.toAbsolutePath().normalize().toString(),
                    FfmpegEncoder.resolveFfmpegExecutable("ffmpeg"));
            } finally {
                restoreProperty(previous);
            }
        } finally {
            Files.deleteIfExists(executable);
        }
    }

    @Test
    void absentSystemPropertyUsesConfiguredFallback() throws IOException {
        String previous = System.getProperty(FfmpegEncoder.FFMPEG_PATH_PROPERTY);
        System.clearProperty(FfmpegEncoder.FFMPEG_PATH_PROPERTY);
        try {
            assertEquals("custom-ffmpeg", FfmpegEncoder.resolveFfmpegExecutable("custom-ffmpeg"));
        } finally {
            restoreProperty(previous);
        }
    }

    @Test
    void emptySystemPropertyUsesDefaultFallback() throws IOException {
        String previous = System.getProperty(FfmpegEncoder.FFMPEG_PATH_PROPERTY);
        System.setProperty(FfmpegEncoder.FFMPEG_PATH_PROPERTY, "  ");
        try {
            assertEquals("ffmpeg", FfmpegEncoder.resolveFfmpegExecutable(""));
        } finally {
            restoreProperty(previous);
        }
    }

    @Test
    void missingSystemPropertyPathFailsBeforeProcessLaunch() {
        String previous = System.getProperty(FfmpegEncoder.FFMPEG_PATH_PROPERTY);
        System.setProperty(FfmpegEncoder.FFMPEG_PATH_PROPERTY, "/missing/internalrecorder-ffmpeg");
        try {
            assertThrows(IOException.class, () -> FfmpegEncoder.resolveFfmpegExecutable("ffmpeg"));
        } finally {
            restoreProperty(previous);
        }
    }

    private static void restoreProperty(String value) {
        if (value == null) {
            System.clearProperty(FfmpegEncoder.FFMPEG_PATH_PROPERTY);
        } else {
            System.setProperty(FfmpegEncoder.FFMPEG_PATH_PROPERTY, value);
        }
    }
}
