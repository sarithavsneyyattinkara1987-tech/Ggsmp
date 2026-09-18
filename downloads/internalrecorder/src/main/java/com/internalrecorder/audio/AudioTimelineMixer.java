package com.internalrecorder.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class AudioTimelineMixer {
    public static final int TARGET_SAMPLE_RATE = 48_000;
    public static final int TARGET_CHANNELS = 2;
    public static final long CHUNK_NANOS = TimeUnit.MILLISECONDS.toNanos(20L);

    private final List<AudioEvent> events = new ArrayList<>();
    private final Object lock = new Object();

    public void submitEvent(AudioEvent event) {
        if (event == null || event.targetSamples() == null || event.targetSamples().length == 0) {
            return;
        }
        synchronized (lock) {
            events.add(event);
            events.sort(Comparator.comparingLong(AudioEvent::startTimestampNanos));
            if (events.size() > 1024) {
                events.subList(0, events.size() - 1024).clear();
            }
        }
    }

    public byte[] renderChunk(long startTimestampNanos, long endTimestampNanos) {
        if (endTimestampNanos <= startTimestampNanos) {
            return new byte[0];
        }

        long sampleFrames = nanosToSamples(endTimestampNanos - startTimestampNanos, TARGET_SAMPLE_RATE);
        int[] mixed = new int[(int) sampleFrames * TARGET_CHANNELS];

        synchronized (lock) {
            for (AudioEvent event : new ArrayList<>(events)) {
                if (event.endTimestampNanos() <= startTimestampNanos || event.startTimestampNanos() >= endTimestampNanos) {
                    continue;
                }

                long overlapStart = Math.max(startTimestampNanos, event.startTimestampNanos());
                long overlapEnd = Math.min(endTimestampNanos, event.endTimestampNanos());
                long chunkSampleStart = nanosToSamples(overlapStart - startTimestampNanos, TARGET_SAMPLE_RATE);
                long chunkSampleEnd = nanosToSamples(overlapEnd - startTimestampNanos, TARGET_SAMPLE_RATE);

                long eventSampleStart = nanosToSamples(overlapStart - event.startTimestampNanos(), TARGET_SAMPLE_RATE);
                long eventSampleEnd = nanosToSamples(overlapEnd - event.startTimestampNanos(), TARGET_SAMPLE_RATE);

                for (long sampleIndex = eventSampleStart; sampleIndex < eventSampleEnd; sampleIndex++) {
                    long chunkIndex = chunkSampleStart + (sampleIndex - eventSampleStart);
                    if (chunkIndex < 0L || chunkIndex >= sampleFrames) {
                        continue;
                    }
                    int eventIndex = (int) sampleIndex * TARGET_CHANNELS;
                    if (eventIndex + 1 >= event.targetSamples().length) {
                        break;
                    }
                    int targetIndex = (int) chunkIndex * TARGET_CHANNELS;
                    mixed[targetIndex] = clampSample(mixed[targetIndex] + event.targetSamples()[eventIndex]);
                    mixed[targetIndex + 1] = clampSample(mixed[targetIndex + 1] + event.targetSamples()[eventIndex + 1]);
                }
            }

            events.removeIf(event -> event.endTimestampNanos() <= startTimestampNanos);
        }

        ByteBuffer output = ByteBuffer.allocate(mixed.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index < mixed.length; index++) {
            output.putShort((short) clampSample(mixed[index]));
        }
        return output.array();
    }

    public boolean isEmpty() {
        synchronized (lock) {
            return events.isEmpty();
        }
    }

    private static long nanosToSamples(long nanos, int sampleRate) {
        return Math.max(0L, (nanos * sampleRate) / 1_000_000_000L);
    }

    private static int clampSample(int value) {
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        return value;
    }
}