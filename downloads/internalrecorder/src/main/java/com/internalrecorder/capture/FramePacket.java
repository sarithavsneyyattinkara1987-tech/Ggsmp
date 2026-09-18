package com.internalrecorder.capture;

public record FramePacket(byte[] bgra, long timestampNanos) {
}