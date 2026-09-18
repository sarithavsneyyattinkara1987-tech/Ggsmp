package com.internalrecorder;

import com.internalrecorder.capture.RecordingManager;
import com.internalrecorder.client.RecordingHud;
import com.internalrecorder.config.RecorderConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class InternalRecorderClient implements ClientModInitializer {
    public static final String MOD_ID = "internalrecorder";
    @Override
    public void onInitializeClient() {
        RecorderConfig.load();
        RecordingManager.initialize();
        HudRenderCallback.EVENT.register(RecordingHud::render);
    }
}