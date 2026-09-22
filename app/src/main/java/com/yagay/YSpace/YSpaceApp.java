package com.yagay.YSpace;

import android.app.Application;

public final class YSpaceApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LsposedBridge.init(this);
    }
}
