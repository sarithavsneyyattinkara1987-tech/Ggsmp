package com.internalrecorder.encode;

import com.internalrecorder.audio.AudioEvent;
import com.internalrecorder.audio.AudioCaptureBridge;
import com.internalrecorder.audio.AudioTimelineMixer;
import com.internalrecorder.capture.FramePacket;
import com.internalrecorder.config.RecorderConfig;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class FfmpegEncoder {
    private static final FramePacket END = new FramePacket(new byte[0], -1L);

    private final RecorderConfig config;
    private final int inputWidth;
    private final int inputHeight;
    private final Path output;
    private final boolean audioEnabled;
    private final AudioTimelineMixer audioTimeline = new AudioTimelineMixer();
    private final ArrayBlockingQueue<AudioEvent> audioEventQueue = new ArrayBlockingQueue<>(256);
    private final ArrayBlockingQueue<FramePacket> videoQueue = new ArrayBlockingQueue<>(4);
    private final ArrayBlockingQueue<byte[]> audioQueue = new ArrayBlockingQueue<>(32);
    private Process process;
    private OutputStream videoInput;
    private OutputStream audioInput;
    private Thread encoderThread;
    private Thread audioThread;
    private volatile boolean running;
    private volatile long startedAtNanos;
    private ServerSocket audioServer;
    private Socket audioSocket;

    public FfmpegEncoder(RecorderConfig config, int inputWidth, int inputHeight, Path output, boolean audioEnabled) {
        this.config = config;
        this.inputWidth = inputWidth;
        this.inputHeight = inputHeight;
        this.output = output;
        this.audioEnabled = audioEnabled;
    }

    public boolean start() throws IOException {
        List<String> command = new ArrayList<>();
        command.add(config.ffmpegPath);
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-y");
        command.add("-f");
        command.add("rawvideo");
        command.add("-pix_fmt");
        command.add("bgra");
        command.add("-video_size");
        command.add(inputWidth + "x" + inputHeight);
        command.add("-framerate");
        command.add(Integer.toString(config.fps));
        command.add("-i");
        command.add("pipe:0");

        if (audioEnabled) {
            audioServer = new ServerSocket();
            audioServer.bind(new InetSocketAddress("127.0.0.1", 0));
            audioServer.setSoTimeout(5_000);
            command.add("-f");
            command.add("s16le");
            command.add("-ar");
            command.add("48000");
            command.add("-ac");
            command.add("2");
            command.add("-i");
            command.add("tcp://127.0.0.1:" + audioServer.getLocalPort());
        }

        command.add("-vf");
        command.add("vflip,scale=" + config.outputWidth + ":" + config.outputHeight + ":flags=lanczos");
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-crf");
        command.add(Integer.toString(config.crf));
        command.add("-pix_fmt");
        command.add("yuv420p");
        if (audioEnabled) {
            command.add("-c:a");
            command.add("aac");
            command.add("-b:a");
            command.add("192k");
            command.add("-shortest");
        }
        command.add(output.toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        try {
            process = builder.start();
            videoInput = new BufferedOutputStream(process.getOutputStream(), 1024 * 1024);
            if (audioEnabled) {
                audioThread = new Thread(this::writeAudioSocket, "InternalRecorder-audio");
                audioThread.start();
            }
            running = true;
            startedAtNanos = System.nanoTime();
            AudioCaptureBridge.startRecording(startedAtNanos);
            encoderThread = new Thread(this::writeVideo, "InternalRecorder-encoder");
            encoderThread.start();
            AudioCaptureBridge.attach(this);
            return true;
        } catch (IOException exception) {
            closeAudioTransport();
            if (process != null) {
                process.destroyForcibly();
                process = null;
            }
            throw exception;
        }
    }

    public void submitVideo(FramePacket frame) {
        if (running) {
            videoQueue.offer(frame);
        }
    }

    public void submitAudio(byte[] pcm) {
        if (running && audioEnabled) {
            audioQueue.offer(pcm);
        }
    }

    public void submitAudioEvent(AudioEvent event) {
        if (!running || !audioEnabled || event == null) {
            return;
        }
        if (!audioEventQueue.offer(event)) {
            audioEventQueue.poll();
            audioEventQueue.offer(event);
        }
    }

    public long elapsedMillis() {
        return startedAtNanos == 0L ? 0L : (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        AudioCaptureBridge.detach();
        AudioCaptureBridge.stopRecording();
        closeAudioServer();
        while (!videoQueue.offer(END)) {
            videoQueue.poll();
        }
        join(encoderThread);
        if (audioEnabled) {
            while (!audioQueue.offer(new byte[0])) {
                audioQueue.poll();
            }
            join(audioThread);
        }
        closeAudioTransport();
        closeQuietly(videoInput);
        closeQuietly(audioInput);
        if (process != null) {
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    private void writeVideo() {
        try {
            while (true) {
                FramePacket frame = videoQueue.take();
                if (frame == END) {
                    break;
                }
                videoInput.write(frame.bgra());
            }
            videoInput.flush();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            System.err.println("[InternalRecorder] Video pipe closed: " + exception.getMessage());
        }
    }

    private void writeAudioSocket() {
        try {
            ServerSocket server = audioServer;
            if (server == null) {
                return;
            }
            audioSocket = server.accept();
            audioInput = new BufferedOutputStream(audioSocket.getOutputStream(), 64 * 1024);
            long cursorNanos = 0L;
            while (running || !audioEventQueue.isEmpty() || !audioTimeline.isEmpty()) {
                AudioEvent event = audioEventQueue.poll(10, TimeUnit.MILLISECONDS);
                if (event != null) {
                    audioTimeline.submitEvent(event);
                }

                long chunkStart = cursorNanos;
                long chunkEnd = chunkStart + AudioTimelineMixer.CHUNK_NANOS;
                byte[] pcm = audioTimeline.renderChunk(chunkStart, chunkEnd);
                if (pcm.length > 0) {
                    audioInput.write(pcm);
                }
                cursorNanos = chunkEnd;

                if (!running && audioEventQueue.isEmpty() && audioTimeline.isEmpty()) {
                    break;
                }
            }
            audioInput.flush();
        } catch (IOException exception) {
            if (running) {
                System.err.println("[InternalRecorder] Audio socket closed: " + exception.getMessage());
                closeAudioTransport();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeAudioTransport() {
        closeQuietly(audioInput);
        audioInput = null;
        if (audioSocket != null) {
            try {
                audioSocket.close();
            } catch (IOException ignored) {
            }
            audioSocket = null;
        }
        closeAudioServer();
    }

    private void closeAudioServer() {
        if (audioServer != null) {
            try {
                audioServer.close();
            } catch (IOException ignored) {
            }
            audioServer = null;
        }
    }

    private static void closeQuietly(OutputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void join(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(5000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}