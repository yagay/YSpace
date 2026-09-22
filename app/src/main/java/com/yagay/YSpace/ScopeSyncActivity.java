package com.yagay.YSpace;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ScopeSyncActivity extends Activity {
    public static final String EXTRA_PACKAGES = "packages";
    public static final String EXTRA_AUTO_SYNC = "auto_sync";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<String> recommended = new ArrayList<>();
    private LinearLayout content;
    private TextView status;
    private Button sync;
    private boolean autoSync;
    private boolean autoSyncStarted;

    private final LsposedBridge.Listener listener = () ->
            main.post(() -> {
                render();
                maybeAutoSync();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ArrayList<String> fromIntent = getIntent().getStringArrayListExtra(EXTRA_PACKAGES);
        if (fromIntent != null) recommended.addAll(fromIntent);
        autoSync = getIntent().getBooleanExtra(EXTRA_AUTO_SYNC, false);
        buildUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        LsposedBridge.addListener(listener);
        render();
        maybeAutoSync();
    }

    @Override
    protected void onStop() {
        LsposedBridge.removeListener(listener);
        super.onStop();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = text("推荐 Hook 应用", 22, true);
        root.addView(title);

        TextView note = text(
                "这里运行在工作资料中。默认只推荐 Hook 你要诊断的目标 App；不默认 Hook system、SystemUI 或 Google Play Services。YSpace 通过 libxposed 官方 requestScope() 请求加入当前工作资料的 LSPosed 作用域，LSPosed 会显示授权确认。",
                14, false);
        note.setPadding(0, dp(8), 0, dp(10));
        root.addView(note);

        status = text("", 14, true);
        status.setPadding(0, 0, 0, dp(8));
        root.addView(status);

        sync = new Button(this);
        sync.setText("同步推荐到 LSPosed");
        sync.setOnClickListener(v -> syncNow());
        root.addView(sync, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private void render() {
        if (content == null) return;
        content.removeAllViews();

        boolean connected = LsposedBridge.isConnected();
        status.setText("LSPosed：" + (connected ? "已连接" : "未连接") +
                "\n" + LsposedBridge.statusText());
        sync.setEnabled(connected && !recommended.isEmpty());

        List<String> scope = LsposedBridge.getScope();
        Set<String> inScope = new HashSet<>(scope);

        if (recommended.isEmpty()) {
            content.addView(text(
                    "当前没有推荐应用。回到主空间 YSpace，长按一个已隔离应用选择“加入推荐 Hook”，或者重新加入一个应用。",
                    15, false));
        } else {
            content.addView(text("推荐应用", 17, true));
            for (String pkg : recommended) {
                TextView row = text(
                        appLabel(pkg) + "\n" + pkg + "\n目标 App · 推荐 Hook：记录它实际查询的环境对象\n" +
                                (inScope.contains(pkg)
                                ? "✓ 已在工作资料 LSPosed 作用域"
                                : "○ 推荐加入作用域"),
                        14, false);
                row.setPadding(dp(8), dp(9), dp(8), dp(9));
                content.addView(row);
            }
        }

        if (connected) {
            TextView scopeTitle = text("当前工作资料作用域", 17, true);
            scopeTitle.setPadding(0, dp(14), 0, dp(4));
            content.addView(scopeTitle);
            if (scope.isEmpty()) {
                content.addView(text("（空）", 14, false));
            } else {
                for (String pkg : scope) {
                    TextView row = text("• " + pkg, 14, false);
                    row.setPadding(dp(8), dp(4), dp(8), dp(4));
                    content.addView(row);
                }
            }
        }
    }

    private void maybeAutoSync() {
        if (!autoSync || autoSyncStarted || !LsposedBridge.isConnected() || recommended.isEmpty()) return;
        autoSyncStarted = true;
        syncNow();
    }

    private void syncNow() {
        if (!LsposedBridge.isConnected()) {
            status.setText("LSPosed：未连接\n请先在 LSPosed 中启用 YSpace 模块。");
            return;
        }
        if (recommended.isEmpty()) return;

        sync.setEnabled(false);
        status.setText("正在向 LSPosed 请求工作资料作用域…");
        LsposedBridge.requestScopes(recommended, (ok, message, approved) ->
                main.post(() -> {
                    status.setText(message);
                    render();
                }));
    }

    private String appLabel(String packageName) {
        try {
            android.content.pm.ApplicationInfo info =
                    getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence label = getPackageManager().getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (Throwable ignored) {
            return packageName;
        }
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
