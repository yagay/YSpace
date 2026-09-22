package com.yagay.YSpace;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

public final class DiagnosticsActivity extends Activity {
    private LinearLayout content;
    private DiagnosticsDb db;
    private String selectedPackage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DiagnosticsDb.get(this);
        selectedPackage = getIntent().getStringExtra("package");
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = text("真实检测记录", 22, true);
        root.addView(title);

        TextView note = text(
                "这里只显示目标 App 实际发生过的环境访问。没有发生过的项目不会显示。\n" +
                "当前覆盖：Java / Android Framework API。Native 直接 syscall 尚不会被这里冒充成“未检测”。",
                14, false);
        note.setPadding(0, dp(8), 0, dp(10));
        root.addView(note);

        Button scope = new Button(this);
        scope.setText("LSPosed 诊断设置");
        scope.setAllCaps(false);
        scope.setOnClickListener(v -> ObserverSetup.showGuide(this, selectedPackage));
        root.addView(scope, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button back = new Button(this);
        back.setText("返回应用");
        back.setOnClickListener(v -> {
            if (selectedPackage != null) {
                selectedPackage = null;
                render();
            } else finish();
        });
        actions.addView(back, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button clear = new Button(this);
        clear.setText("清空记录");
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("清空诊断记录")
                .setMessage(selectedPackage == null ? "清空全部记录？" : "清空 " + selectedPackage + " 的记录？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> {
                    db.clear(selectedPackage);
                    render();
                }).show());
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(actions);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        render();
    }

    private void render() {
        content.removeAllViews();
        if (selectedPackage == null) renderPackages();
        else renderEvents(selectedPackage);
    }

    private void renderPackages() {
        List<DiagnosticsDb.PackageRow> rows = db.packages();
        if (rows.isEmpty()) {
            content.addView(text(
                    "还没有任何 Observer 注入记录。\n\n" +
                    "这不代表目标 App 没有检测环境，而是 YSpace 还没有收到目标进程的 Hook 心跳。\n\n" +
                    "请确认：LSPosed → YSpace → Work Profile / 工作资料 → 勾选目标 App，" +
                    "然后强制停止并重新打开目标 App。",
                    15, false));
            return;
        }

        for (DiagnosticsDb.PackageRow row : rows) {
            Button b = new Button(this);
            String when = DateFormat.format("MM-dd HH:mm", new Date(row.lastTime)).toString();
            String observer = row.observerInjected() ? "Observer 已注入 ✓" : "Observer 状态异常";
            b.setAllCaps(false);
            b.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            b.setText(row.packageName + "\n" + observer + " · " +
                    row.realCount + " 条真实检测 · " + when);
            b.setOnClickListener(v -> {
                selectedPackage = row.packageName;
                render();
            });
            content.addView(b, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(66)));
        }
    }

    private void renderEvents(String packageName) {
        TextView p = text(packageName, 17, true);
        p.setPadding(0, dp(8), 0, dp(8));
        content.addView(p);

        DiagnosticsDb.PackageStatus status = db.status(packageName);
        if (!status.observerInjected()) {
            TextView warning = text(
                    "Observer 未注入 ✕\n\n" +
                    "当前没有收到这个 App 的 YSpace Hook 心跳。最常见原因：\n" +
                    "• YSpace 模块未在 LSPosed 启用\n" +
                    "• 勾选的是主空间 App，不是 Work Profile 版本\n" +
                    "• 勾选后没有强制停止并重新启动目标 App\n\n" +
                    "点击上方“LSPosed 诊断设置”查看具体设置。",
                    15, true);
            warning.setPadding(dp(10), dp(12), dp(10), dp(12));
            content.addView(warning);
            return;
        }

        String injectedAt = DateFormat.format(
                "MM-dd HH:mm:ss", new Date(status.lastObserverTime)).toString();
        TextView health = text(
                "Observer 已注入 ✓\n" +
                "最近 Hook：" + injectedAt + "\n" +
                "Session：" + status.sessionCount + "\n" +
                "真实检测调用：" + status.realCount,
                14, true);
        health.setPadding(dp(10), dp(8), dp(10), dp(12));
        content.addView(health);

        List<DiagnosticsDb.EventRow> rows = db.events(packageName, 1000);
        if (status.realCount == 0) {
            content.addView(text(
                    "Hook 已经正常工作，但当前 Session 还没有捕获到目标 App 的 Java / Android Framework 环境检测调用。\n\n" +
                    "可以继续操作登录、支付、转账等页面后再回来查看。Native 直接 syscall 目前仍不在这一层记录范围内。",
                    15, false));
        }

        String lastSession = null;
        for (DiagnosticsDb.EventRow e : rows) {
            if (!e.session.equals(lastSession)) {
                lastSession = e.session;
                TextView s = text("Session  " + shortSession(e.session), 13, true);
                s.setPadding(0, dp(12), 0, dp(4));
                content.addView(s);
            }

            TextView row = text("", 14, false);
            String time = DateFormat.format("HH:mm:ss.SSS", new Date(e.ts)).toString();
            StringBuilder b = new StringBuilder();
            b.append(time).append("  [").append(e.type).append("]\n");
            if (e.object != null && !e.object.isEmpty()) b.append(e.object).append("\n");
            if (e.api != null && !e.api.isEmpty()) b.append(e.api);
            if (e.result != null && !e.result.isEmpty()) b.append("  →  ").append(e.result);
            if (e.process != null && !e.process.equals(e.packageName)) b.append("\nprocess: ").append(e.process);
            row.setText(b.toString());
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            content.addView(row);
        }
    }

    private String shortSession(String s) {
        if (s == null) return "";
        int i = s.lastIndexOf('-');
        return i >= 0 ? s.substring(Math.max(0, i - 18)) : s;
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
