package com.idlefish.flutterboost;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

public class FlutterEngineInfo {

    public String engineId;
    public FlutterEngine flutterEngine;
    private MethodChannel methodChannel;

    public FlutterEngineInfo(String engineId, FlutterEngine flutterEngine) {
        this.engineId = engineId;
        this.flutterEngine = flutterEngine;
    }


    public MethodChannel getMethodChannel() {
        if (methodChannel == null) {
            methodChannel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), FlutterBoostPlugin.CHANNEL_NAME);
        }
        return methodChannel;
    }
}
