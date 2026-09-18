package com.internalrecorder.capture;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;

import java.nio.ByteBuffer;

public final class PboFrameCapturer {
    private final int[] pbos = new int[2];
    private final long[] fences = new long[2];
    private int width = -1;
    private int height = -1;
    private int nextPbo;
    private boolean hasPreviousFrame;

    public void begin(int width, int height) {
        if (this.width == width && this.height == height) {
            return;
        }
        close();
        this.width = width;
        this.height = height;
        long byteCount = (long) width * height * 4L;
        for (int i = 0; i < pbos.length; i++) {
            pbos[i] = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, pbos[i]);
            GL15C.glBufferData(GL21C.GL_PIXEL_PACK_BUFFER, byteCount, GL15C.GL_STREAM_READ);
            fences[i] = 0L;
        }
        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
        nextPbo = 0;
        hasPreviousFrame = false;
    }

    /**
     * Starts an async read into one PBO and maps the completed read from the previous PBO.
     * The framebuffer is verified at runtime before the readback and converted to BGRA if the
     * active read format is RGBA, which avoids assuming a fixed OpenGL pixel layout.
     */
    public FramePacket capture(long timestampNanos) {
        if (width <= 0 || height <= 0) {
            return null;
        }

        int readPbo = nextPbo;
        int mapPbo = (nextPbo + 1) % pbos.length;
        int readFormat = resolveReadFormat();
        int readType = resolveReadType();

        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, pbos[readPbo]);
        GL11.glReadPixels(0, 0, width, height, readFormat, readType, 0L);
        if (fences[readPbo] != 0L) {
            GL32C.glDeleteSync(fences[readPbo]);
        }
        fences[readPbo] = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

        FramePacket completed = null;
        if (hasPreviousFrame) {
            long previousFence = fences[mapPbo];
            if (previousFence != 0L) {
                int result = GL32C.glClientWaitSync(previousFence, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, 0L);
                if (result == GL32C.GL_ALREADY_SIGNALED || result == GL32C.GL_CONDITION_SATISFIED) {
                    GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, pbos[mapPbo]);
                    ByteBuffer mapped = GL30C.glMapBufferRange(
                        GL21C.GL_PIXEL_PACK_BUFFER,
                        0L,
                        (long) width * height * 4L,
                        GL30C.GL_MAP_READ_BIT
                    );
                    if (mapped != null) {
                        byte[] rawPixels = new byte[width * height * 4];
                        mapped.get(rawPixels);
                        GL30C.glUnmapBuffer(GL21C.GL_PIXEL_PACK_BUFFER);
                        completed = new FramePacket(normalizeToBgra(rawPixels, readFormat, readType), timestampNanos);
                    }
                    GL32C.glDeleteSync(previousFence);
                    fences[mapPbo] = 0L;
                }
            }
        }

        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
        nextPbo = mapPbo;
        hasPreviousFrame = true;
        return completed;
    }

    public void close() {
        for (int i = 0; i < pbos.length; i++) {
            if (fences[i] != 0L) {
                GL32C.glDeleteSync(fences[i]);
                fences[i] = 0L;
            }
            if (pbos[i] != 0) {
                GL15C.glDeleteBuffers(pbos[i]);
                pbos[i] = 0;
            }
        }
        width = -1;
        height = -1;
        hasPreviousFrame = false;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    private int resolveReadFormat() {
        int format = GL11.glGetInteger(0x8B9B);
        return format == 0 ? 0x80E1 : format;
    }

    private int resolveReadType() {
        int type = GL11.glGetInteger(0x8B9A);
        return type == 0 ? GL11.GL_UNSIGNED_BYTE : type;
    }

    private static byte[] normalizeToBgra(byte[] packedPixels, int readFormat, int readType) {
        if (packedPixels == null || packedPixels.length == 0) {
            return packedPixels;
        }
        if (readType != GL11.GL_UNSIGNED_BYTE || readFormat == 0x80E1) {
            return packedPixels;
        }
        byte[] converted = new byte[packedPixels.length];
        for (int index = 0; index < packedPixels.length; index += 4) {
            converted[index] = packedPixels[index + 2];
            converted[index + 1] = packedPixels[index + 1];
            converted[index + 2] = packedPixels[index];
            converted[index + 3] = packedPixels[index + 3];
        }
        return converted;
    }
}