package com.yagay.YSpace;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class EventReceiver extends BroadcastReceiver {
    static final String ACTION = "com.yagay.YSpace.EVENT";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        String source = intent.getStringExtra("source_package");
        if (source == null || source.isBlank() || source.equals(context.getPackageName())) return;
        DiagnosticsDb.get(context).insert(intent);
    }
}
