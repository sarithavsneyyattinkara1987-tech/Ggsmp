package com.internalrecorder.audio;

import com.internalrecorder.capture.RecordingManager;
import com.internalrecorder.encode.FfmpegEncoder;
import net.minecraft.client.sound.SoundInstance;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/**
 * This captures decoded Minecraft PCM before OpenAL performs the final device mix.
 * It is not a capture of the final OpenAL/device output and does not attempt to represent
 * positional OpenAL gain, panning, or the final mixed stream.
 */
public final class AudioCaptureBridge {
    private static volatile boolean sawSoundActivity;
    private static volatile boolean pcmTapAvailable;
    private static volatile long recordingStartNanos;
    private static FfmpegEncoder encoder;

    private AudioCaptureBridge() {
    }

    public static void initialize() {
        sawSoundActivity = false;
        pcmTapAvailable = false;
        recordingStartNanos = 0L;
    }

    public static boolean hasPcmTap() {
        return pcmTapAvailable;
    }

    public static void attach(FfmpegEncoder target) {
        encoder = target;
    }

    public static void detach() {
        encoder = null;
    }

    public static void startRecording(long startNanos) {
        recordingStartNanos = startNanos;
    }

    public static void stopRecording() {
        recordingStartNanos = 0L;
    }

    public static void onSoundStarted(SoundInstance sound) {
        sawSoundActivity = true;
    }

    public static void onDecodedPcm(ByteBuffer sample, AudioFormat format) {
        onDecodedPcm(sample, format, System.nanoTime());
    }

    public static void onDecodedPcm(ByteBuffer sample, AudioFormat format, long capturedAtNanos) {
        if (sample == null || format == null || RecordingManager.isPaused()) {
            return;
        }
        if (recordingStartNanos == 0L) {
            recordingStartNanos = capturedAtNanos;
        }
        AudioEvent event = AudioEvent.fromDecodedPcm(sample, format, capturedAtNanos - recordingStartNanos);
        if (event == null) {
            return;
        }
        pcmTapAvailable = true;
        if (encoder != null) {
            encoder.submitAudioEvent(event);
        }
    }

    public static void submitPcm(byte[] pcmS16LeStereo48k) {
        if (pcmS16LeStereo48k == null || pcmS16LeStereo48k.length == 0 || encoder == null) {
            return;
        }
        pcmTapAvailable = true;
        encoder.submitAudio(pcmS16LeStereo48k);
    }

    public static boolean sawSoundActivity() {
        return sawSoundActivity;
    }
}