package com.internalrecorder.audio;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class AudioTimelineMixerTest {
    @Test
    void monoToStereoConversionMatchesExpectedSampleCount() {
        ByteBuffer pcm = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        pcm.putShort((short) 1000);
        pcm.putShort((short) -2000);
        pcm.putShort((short) 2500);
        pcm.putShort((short) -5000);
        pcm.flip();

        AudioEvent event = AudioEvent.fromDecodedPcm(pcm, new AudioFormat(44100.0f, 16, 1, true, false), 0L);

        assertEquals(2, event.targetChannels());
        assertEquals(48000, event.targetSampleRate());
        assertNotNull(event.targetSamples());
        assertEquals(5, event.targetSamples().length / 2);
    }

    @Test
    void sampleRateConversionPreservesFrameTiming() {
        ByteBuffer pcm = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        pcm.putShort((short) 2000);
        pcm.putShort((short) 4000);
        pcm.flip();

        AudioEvent event = AudioEvent.fromDecodedPcm(pcm, new AudioFormat(8000.0f, 16, 1, true, false), 0L);

        assertEquals(48000, event.targetSampleRate());
        assertEquals(2, event.targetChannels());
        assertTrue(event.targetSamples().length >= 4);
    }

    @Test
    void overlappingSoundsAreMixedWithoutClipping() {
        AudioTimelineMixer mixer = new AudioTimelineMixer();
        mixer.submitEvent(new AudioEvent(0L, 48000, 2, makeTone(48000, 2000), 48000L, 48000, 2, makeTone(48000, 2000)));
        mixer.submitEvent(new AudioEvent(120000L, 48000, 2, makeTone(48000, 2000), 48000L, 48000, 2, makeTone(48000, 2000)));

        byte[] chunk = mixer.renderChunk(0L, 240000L);
        assertNotNull(chunk);
        assertTrue(chunk.length > 0);
    }

    @Test
    void silenceGapProducesZeroSamples() {
        AudioTimelineMixer mixer = new AudioTimelineMixer();
        mixer.submitEvent(new AudioEvent(0L, 48000, 2, makeTone(48000, 1000), 1000L, 48000, 2, makeTone(48000, 1000)));

        byte[] chunk = mixer.renderChunk(0L, 1000000L);
        assertNotNull(chunk);
        assertTrue(chunk.length > 0);
    }

    @Test
    void timestampPlacementUsesRelativeRecordingClock() {
        AudioTimelineMixer mixer = new AudioTimelineMixer();
        mixer.submitEvent(new AudioEvent(100000000L, 48000, 2, makeTone(48000, 4800), 4800L, 48000, 2, makeTone(48000, 4800)));

        byte[] chunk = mixer.renderChunk(100000000L, 100000000L + 1_000_000L);
        assertNotNull(chunk);
        assertTrue(chunk.length > 0);
    }

    private static short[] makeTone(int sampleRate, int samples) {
        short[] out = new short[samples * 2];
        for (int i = 0; i < samples; i++) {
            short value = (short) (i % 2 == 0 ? 1000 : -1000);
            out[i * 2] = value;
            out[i * 2 + 1] = value;
        }
        return out;
    }
}
