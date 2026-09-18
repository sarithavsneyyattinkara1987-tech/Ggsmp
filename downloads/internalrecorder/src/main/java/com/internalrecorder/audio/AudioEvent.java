package com.internalrecorder.audio;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public record AudioEvent(
    long startTimestampNanos,
    int sourceSampleRate,
    int sourceChannels,
    short[] decodedSamples,
    long durationNanos,
    int targetSampleRate,
    int targetChannels,
    short[] targetSamples
) {
    public long endTimestampNanos() {
        return startTimestampNanos + durationNanos;
    }

    public static AudioEvent fromDecodedPcm(ByteBuffer source, AudioFormat format, long startTimestampNanos) {
        if (source == null || format == null) {
            return null;
        }

        ByteBuffer input = source.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int sourceChannels = Math.max(1, format.getChannels());
        int sourceRate = Math.max(1, (int) Math.round(format.getSampleRate()));
        int frameCount = input.remaining() / (sourceChannels * 2);
        if (frameCount <= 0) {
            return null;
        }

        short[] decodedSamples = new short[frameCount * sourceChannels];
        for (int index = 0; index < decodedSamples.length; index++) {
            decodedSamples[index] = input.getShort();
        }

        short[] stereo = new short[frameCount * 2];
        for (int frame = 0; frame < frameCount; frame++) {
            int base = frame * sourceChannels;
            short left = sourceChannels == 1 ? decodedSamples[base] : decodedSamples[base];
            short right = sourceChannels == 1 ? decodedSamples[base] : decodedSamples[base + 1];
            stereo[frame * 2] = left;
            stereo[frame * 2 + 1] = right;
        }

        short[] targetSamples = resampleToStereo48k(stereo, sourceRate, frameCount);
        if (targetSamples.length == 0) {
            return null;
        }

        long durationNanos = Math.round(((double) targetSamples.length / 2.0d) * 1_000_000_000.0d / 48_000.0d);
        return new AudioEvent(startTimestampNanos, sourceRate, sourceChannels, decodedSamples, durationNanos, 48_000, 2, targetSamples);
    }

    private static short[] resampleToStereo48k(short[] stereo, int sourceRate, int frameCount) {
        if (stereo == null || stereo.length == 0 || sourceRate <= 0) {
            return new short[0];
        }
        if (sourceRate == 48_000) {
            return stereo;
        }

        int targetFrames = Math.max(1, (int) Math.ceil(frameCount * (48_000.0d / sourceRate)));
        short[] resampled = new short[targetFrames * 2];
        for (int index = 0; index < targetFrames; index++) {
            int sourceIndex = Math.min(frameCount - 1, (int) ((index * sourceRate) / 48_000.0d));
            int base = sourceIndex * 2;
            if (base + 1 >= stereo.length) {
                break;
            }
            resampled[index * 2] = stereo[base];
            resampled[index * 2 + 1] = stereo[base + 1];
        }
        return resampled;
    }
}