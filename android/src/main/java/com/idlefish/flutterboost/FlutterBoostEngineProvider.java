package com.idlefish.flutterboost;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterJNI;
import io.flutter.embedding.engine.FlutterShellArgs;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.loader.FlutterLoader;
import io.flutter.view.FlutterMain;

public class FlutterBoostEngineProvider {

    private static volatile FlutterBoostEngineProvider sInstance;
    private static volatile boolean sFlutterInit;

    private ConcurrentHashMap<String, FlutterEngineInfo> flutterEngineInfoMap = new ConcurrentHashMap<>();

    public static final String CACHE_ENGINE_ID = "__boost_cached_id__";

    private FlutterBoostEngineProvider() {

    }

    public static FlutterBoostEngineProvider getInstance() {
        if (sInstance == null) {
            synchronized (FlutterBoostEngineProvider.class) {
                if (sInstance == null) {
                    sInstance = new FlutterBoostEngineProvider();
                }
            }
        }
        return sInstance;
    }


    public FlutterEngine getOrCreateEngine(String engineId) {
        if (engineId == null || engineId.length() == 0) {
            throw new IllegalArgumentException("engineId cannot be null or empty.");
        }
        Platform platform = FlutterBoost.instance().platform();
        if (!sFlutterInit) {
            sFlutterInit = true;
            FlutterMain.startInitialization(platform.getApplication());

            FlutterShellArgs flutterShellArgs = new FlutterShellArgs(new String[0]);
            FlutterMain.ensureInitializationComplete(
                    platform.getApplication().getApplicationContext(), flutterShellArgs.toArray());
        }

        FlutterEngineInfo cachedEngineInfo = flutterEngineInfoMap.get(engineId);
        if (cachedEngineInfo != null) {
            return cachedEngineInfo.flutterEngine;
        }


        if (platform.lifecycleListener != null) {
            platform.lifecycleListener.beforeCreateEngine();
        }

        FlutterEngine engine = new FlutterEngine(platform.getApplication().getApplicationContext(), FlutterLoader.getInstance(), new FlutterJNI(), null, false);

        flutterEngineInfoMap.put(engineId, new FlutterEngineInfo(engineId, engine));
        registerPlugins(engine);


        if (platform.lifecycleListener != null) {
            platform.lifecycleListener.onEngineCreated();
        }
        if (engine.getDartExecutor().isExecutingDart()) {
            return engine;
        }

        if (platform.initialRoute() != null) {
            engine.getNavigationChannel().setInitialRoute(platform.initialRoute());
        }
        DartExecutor.DartEntrypoint entrypoint = new DartExecutor.DartEntrypoint(
                FlutterMain.findAppBundlePath(),
                platform.dartEntrypoint()
        );

        engine.getDartExecutor().executeDartEntrypoint(entrypoint);


        return engine;
    }

    public void removeFlutterEngineInfo(String engineId) {
        if (engineId != null) {
            if (engineId.equals(CACHE_ENGINE_ID)) {
                return;
            }
            flutterEngineInfoMap.remove(engineId);
        }
    }

    public void removeAllEngineInfo() {
        flutterEngineInfoMap.clear();
    }

    public FlutterEngineInfo getFlutterEngineInfo(String engineId) {
        if (engineId == null || engineId.length() == 0) {
            throw new IllegalArgumentException("engineId cannot be null or empty.");
        }
        return flutterEngineInfoMap.get(engineId);
    }

    public FlutterEngine getFlutterEngine(String engineId) {
        if (engineId == null || engineId.length() == 0) {
            throw new IllegalArgumentException("engineId cannot be null or empty.");
        }
        FlutterEngineInfo engineInfo =  getFlutterEngineInfo(engineId);
        if (engineInfo != null) {
            return engineInfo.flutterEngine;
        }
        return null;
    }


    public Set<FlutterEngineInfo> getAllEngine() {
        return new HashSet<>(flutterEngineInfoMap.values());
    }


    public FlutterEngine getOrCreateCacheEngine() {
        return getOrCreateEngine(CACHE_ENGINE_ID);
    }

    public FlutterEngine getCachedEngine() {
        return getFlutterEngine(CACHE_ENGINE_ID);
    }

    private void registerPlugins(FlutterEngine engine) {
        try {
            Class<?> generatedPluginRegistrant = Class.forName("io.flutter.plugins.GeneratedPluginRegistrant");
            Method registrationMethod = generatedPluginRegistrant.getDeclaredMethod("registerWith", FlutterEngine.class);
            registrationMethod.invoke(null, engine);
        } catch (Exception e) {
            Debuger.exception(e);
        }
    }


}
