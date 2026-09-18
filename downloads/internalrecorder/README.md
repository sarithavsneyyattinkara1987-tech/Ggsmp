# InternalRecorder

InternalRecorder is a Fabric client mod for Minecraft **1.21.11**. It captures
the Minecraft framebuffer after the world, HUD, chat, and screens have been
rendered, then hands frames to a background FFmpeg encoder. It does not use
desktop, window, or operating-system screen capture.

## Build

Requirements:

- Java 21
- Gradle 9.2.1 or newer
- Fabric Loader 0.19.5
- Minecraft 1.21.11
- FFmpeg available as `ffmpeg` on `PATH`, or an absolute path configured below

From the project directory:

```bash
gradle build
```

Install `build/libs/internalrecorder-1.0.0.jar` in the Fabric `mods` folder
alongside Fabric API.

## Use

- Press **F9** to start or stop recording.
- A red `REC` indicator and timer are drawn through the HUD and are included in
  the captured video.
- The first frame locks the input framebuffer size for that recording. If the
  window or GUI scale changes the framebuffer size, recording stops cleanly
  rather than sending frames of the wrong size into FFmpeg.
- The first launch creates the configured recordings directory in the Minecraft
  game directory.

Files are written as:

```text
.minecraft/recordings/internalrecorder-YYYYMMDD-HHMMSS.mp4
```

## Configuration

The file is created at `.minecraft/config/internalrecorder.json`:

```json
{
  "outputWidth": 1920,
  "outputHeight": 1080,
  "fps": 60,
  "outputDirectory": "recordings",
  "crf": 19,
  "ffmpegPath": "ffmpeg"
}
```

`outputWidth` and `outputHeight` are FFmpeg's encoded output size. The game is
captured at its native framebuffer size and FFmpeg scales it after the
asynchronous readback. `crf` uses x264's scale; lower values are higher quality
and larger files.

## Audio limitation

This captures decoded Minecraft audio buffers before OpenAL's final mix. It is
not a capture of the final OpenAL/device output. Positional OpenAL gain,
panning, and final mixer output are not represented by the captured stream.

The normal 1.21.11 Yarn `SoundSystem` API exposes sound instances and OpenAL
source management, but not the final mixed output that plays on the device.
Sound-instance metadata cannot be converted back into the actual final output,
so this mod does not claim to capture audio by reading system audio or by
fabricating samples.

The implementation captures decoded PCM at the `StaticSound` boundary before the
final OpenAL mix, converts each event to a shared recording timeline, mixes the
overlapping samples into a 48 kHz stereo s16le stream, and sends that stream to
FFmpeg. This is the closest working behavior without violating the no-OS-
capture constraint.

## Performance

- `glReadPixels` writes into two OpenGL pixel buffer objects; the completed
  buffer is mapped one frame later.
- The render thread only performs the readback command, a bounded copy from
  the mapped PBO, and a non-blocking queue offer.
- The encoder thread owns all FFmpeg writes. Frames are dropped when the small
  queue is full rather than stalling gameplay.
- FFmpeg uses `veryfast`, CRF 19, H.264, and AAC when an in-process PCM tap is
  present.