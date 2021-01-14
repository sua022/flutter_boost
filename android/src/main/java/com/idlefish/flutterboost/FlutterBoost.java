package com.idlefish.flutterboost;


import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.idlefish.flutterboost.interfaces.IContainerManager;
import com.idlefish.flutterboost.interfaces.IFlutterViewContainer;
import com.idlefish.flutterboost.interfaces.INativeRouter;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.flutter.embedding.android.FlutterView;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterJNI;
import io.flutter.embedding.engine.FlutterShellArgs;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.loader.FlutterLoader;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.view.FlutterMain;

public class FlutterBoost {
    private Platform mPlatform;

    private FlutterViewContainerManager mManager;
    private Activity mCurrentActiveActivity;
    private boolean mEnterActivityCreate =false;
    static FlutterBoost sInstance = null;
    private static boolean sInit;

    private long FlutterPostFrameCallTime = 0;
    private Application.ActivityLifecycleCallbacks mActivityLifecycleCallbacks;

    public long getFlutterPostFrameCallTime() {
        return FlutterPostFrameCallTime;
    }

    public void setFlutterPostFrameCallTime(long FlutterPostFrameCallTime) {
        this.FlutterPostFrameCallTime = FlutterPostFrameCallTime;
    }

    public static FlutterBoost instance() {
        if (sInstance == null) {
            sInstance = new FlutterBoost();
        }
        return sInstance;
    }

    public void init(Platform platform) {
        if (sInit){
            Debuger.log("FlutterBoost is already initialized. Don't initialize it twice");
            return;
        }

        mPlatform = platform;
        mManager = new FlutterViewContainerManager();

        mActivityLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                //fix crash：'FlutterBoostPlugin not register yet'
                //case: initFlutter after Activity.OnCreate method，and then called start/stop crash
                // In SplashActivity ,showDialog(in OnCreate method) to check permission, if authorized, then init sdk and jump homePage)

                // fix bug : The LauncherActivity will be launch by clicking app icon when app enter background in HuaWei Rom, cause missing forgoround event
                if(mEnterActivityCreate && mCurrentActiveActivity == null) {
                    Intent intent = activity.getIntent();
                    if (!activity.isTaskRoot()
                            && intent != null
                            && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                            && intent.getAction() != null
                            && intent.getAction().equals(Intent.ACTION_MAIN)) {
                        return;
                    }
                }
                mEnterActivityCreate = true;
                mCurrentActiveActivity = activity;
                if (mPlatform.whenEngineStart() == ConfigBuilder.ANY_ACTIVITY_CREATED) {
                    doInitialFlutter();
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {
                if (!mEnterActivityCreate){
                    return;
                }
                if (mCurrentActiveActivity == null) {
                    Debuger.log("Application entry foreground");

                    Set<FlutterEngineInfo> allEngines = FlutterBoostEngineProvider.getInstance().getAllEngine();
                    for (FlutterEngineInfo engine : allEngines) {
                        if (engine != null) {
                            HashMap<String, String> map = new HashMap<>();
                            map.put("type", "foreground");
                            sendEvent(engine.engineId, "lifecycle", map);
                        }
                    }
                }
                mCurrentActiveActivity = activity;
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (!mEnterActivityCreate){
                    return;
                }
                mCurrentActiveActivity = activity;
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (!mEnterActivityCreate){
                    return;
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
                if (!mEnterActivityCreate){
                    return;
                }
                if (mCurrentActiveActivity == activity) {
                    Debuger.log("Application entry background");

                    Set<FlutterEngineInfo> allEngines = FlutterBoostEngineProvider.getInstance().getAllEngine();
                    for (FlutterEngineInfo engine : allEngines) {
                        if (engine != null) {
                            HashMap<String, String> map = new HashMap<>();
                            map.put("type", "background");
                            sendEvent(engine.engineId, "lifecycle", map);
                        }
                    }
                    mCurrentActiveActivity = null;
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                if (!mEnterActivityCreate){
                    return;
                }
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                if (!mEnterActivityCreate){
                    return;
                }
                if (mCurrentActiveActivity == activity) {
                    Debuger.log("Application entry background");

                    Set<FlutterEngineInfo> allEngines = FlutterBoostEngineProvider.getInstance().getAllEngine();
                    for (FlutterEngineInfo engine : allEngines) {
                        if (engine != null) {
                            HashMap<String, String> map = new HashMap<>();
                            map.put("type", "background");
                            sendEvent(engine.engineId, "lifecycle", map);
                        }
                    }
                    mCurrentActiveActivity = null;
                }
            }
        };
        platform.getApplication().registerActivityLifecycleCallbacks(mActivityLifecycleCallbacks);


        if (mPlatform.whenEngineStart() == ConfigBuilder.IMMEDIATELY) {

            doInitialFlutter();
        }
        sInit = true;

    }

    public void doInitialFlutter() {
        if (FlutterBoostEngineProvider.getInstance().getCachedEngine() != null) {
            return;
        }
        FlutterBoostEngineProvider.getInstance().getOrCreateCacheEngine();
    }


    public static class ConfigBuilder {

        public static final String DEFAULT_DART_ENTRYPOINT = "main";
        public static final String DEFAULT_INITIAL_ROUTE = "/";
        public static int IMMEDIATELY = 0;          //立即启动引擎

        public static int ANY_ACTIVITY_CREATED = 1; //当有任何Activity创建时,启动引擎

        public static int FLUTTER_ACTIVITY_CREATED = 2; //当有flutterActivity创建时,启动引擎


        public static int APP_EXit = 0; //所有flutter Activity destory 时，销毁engine
        public static int All_FLUTTER_ACTIVITY_DESTROY = 1; //所有flutter Activity destory 时，销毁engine

        private String dartEntrypoint = DEFAULT_DART_ENTRYPOINT;
        private String initialRoute = DEFAULT_INITIAL_ROUTE;
        private int whenEngineStart = ANY_ACTIVITY_CREATED;
        private int whenEngineDestory = APP_EXit;


        private boolean isDebug = false;

        private FlutterView.RenderMode renderMode = FlutterView.RenderMode.texture;

        private Application mApp;

        private INativeRouter router = null;

        private BoostLifecycleListener lifecycleListener;




        public ConfigBuilder(Application app, INativeRouter router) {
            this.router = router;
            this.mApp = app;
        }

        public ConfigBuilder renderMode(FlutterView.RenderMode renderMode) {
            this.renderMode = renderMode;
            return this;
        }

        public ConfigBuilder dartEntrypoint(@NonNull String dartEntrypoint) {
            this.dartEntrypoint = dartEntrypoint;
            return this;
        }

        public ConfigBuilder initialRoute(@NonNull String initialRoute) {
            this.initialRoute = initialRoute;
            return this;
        }

        public ConfigBuilder isDebug(boolean isDebug) {
            this.isDebug = isDebug;
            return this;
        }

        public ConfigBuilder whenEngineStart(int whenEngineStart) {
            this.whenEngineStart = whenEngineStart;
            return this;
        }


        public ConfigBuilder lifecycleListener(BoostLifecycleListener lifecycleListener) {
            this.lifecycleListener = lifecycleListener;
            return this;
        }

        public Platform build() {

            Platform platform = new Platform() {

                public Application getApplication() {
                    return ConfigBuilder.this.mApp;
                }

                public boolean isDebug() {

                    return ConfigBuilder.this.isDebug;
                }

                @Override
                public String dartEntrypoint() { return ConfigBuilder.this.dartEntrypoint; }

                @Override
                public String initialRoute() {
                    return ConfigBuilder.this.initialRoute;
                }

                public void openContainer(Context context, String url, Map<String, Object> urlParams, int requestCode, Map<String, Object> exts) {
                    router.openContainer(context, url, urlParams, requestCode, exts);
                }


                public int whenEngineStart() {
                    return ConfigBuilder.this.whenEngineStart;
                }


                public FlutterView.RenderMode renderMode() {
                    return ConfigBuilder.this.renderMode;
                }
            };

            platform.lifecycleListener = this.lifecycleListener;
            return platform;

        }

    }

    public IContainerManager containerManager() {
        return sInstance.mManager;
    }

    public Platform platform() {
        return sInstance.mPlatform;
    }

    public MethodChannel channel(String engineId) {
        return FlutterBoostEngineProvider.getInstance().getFlutterEngineInfo(engineId).getMethodChannel();
    }

    public void sendEvent(String engineId, String name, Map args) {
        FlutterBoostPlugin.sendEvent(FlutterBoostEngineProvider.getInstance().getFlutterEngineInfo(engineId).getMethodChannel(), name, args);
    }

    public void invokeMethodUnsafe(String engineId, String name, Serializable args) {
        FlutterBoostPlugin.invokeMethodUnsafe(FlutterBoostEngineProvider.getInstance().getFlutterEngineInfo(engineId).getMethodChannel(), name, args);
    }

    public void invokeMethod(String engineId, String name, Serializable args) {
        FlutterBoostPlugin.invokeMethod(FlutterBoostEngineProvider.getInstance().getFlutterEngineInfo(engineId).getMethodChannel(), name, args);
    }

    public Activity currentActivity() {
        return sInstance.mCurrentActiveActivity;
    }

    public IFlutterViewContainer findContainerById(String id) {
        return mManager.findContainerById(id);
    }


    public FlutterEngine getFlutterEngine(String engineId) {
        return FlutterBoostEngineProvider.getInstance().getFlutterEngine(engineId);
    }


    public void boostDestroy() {
        Set<FlutterEngineInfo> allEngines = FlutterBoostEngineProvider.getInstance().getAllEngine();
        for (FlutterEngineInfo engineInfo : allEngines) {
            if (engineInfo.flutterEngine != null) {
                engineInfo.flutterEngine.destroy();
            }
        }
        if (mPlatform.lifecycleListener != null) {
            mPlatform.lifecycleListener.onEngineDestroy();
        }
        FlutterBoostEngineProvider.getInstance().removeAllEngineInfo();
        mCurrentActiveActivity = null;
    }


    public interface BoostLifecycleListener {

        void beforeCreateEngine();

        void onEngineCreated();

        void onPluginsRegistered();

        void onEngineDestroy();
    }


}
