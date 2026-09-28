package com.yagay.YSpace;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Process;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * One-tap reset for the YSpace-managed part of the Work Profile.
 * It removes the target apps from this user, clears diagnostics and
 * recommendation state, and removes this module's LSPosed scope entries.
 * The Work Profile itself and Android/Google system components stay intact.
 */
public final class ResetSpaceActivity extends Activity {
    public static final String EXTRA_PACKAGES = "packages";

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        ArrayList<String> packages = getIntent().getStringArrayListExtra(EXTRA_PACKAGES);
        if (packages == null) packages = new ArrayList<>();
        final ArrayList<String> targets = packages;

        new Thread(() -> reset(targets), "YSpace-reset").start();
    }

    private void reset(List<String> targets) {
        int userId = currentUserId();
        if (userId < 0) {
            runOnUiThread(() -> fail("无法确定工作资料 User ID。"));
            return;
        }

        runOnUiThread(() -> status.setText(
                "正在恢复初始状态…\n\n" +
                "将移除隔离 App、清空诊断记录、推荐 Hook 和 YSpace 的 LSPosed 作用域。"));

        try { LsposedBridge.removeScopes(new ArrayList<>(LsposedBridge.getScope())); }
        catch (Throwable ignored) {}

        try { DiagnosticsDb.get(this).clear(null); } catch (Throwable ignored) {}
        try { RecommendedStore.clear(this); } catch (Throwable ignored) {}

        int removed = 0;
        ArrayList<String> failed = new ArrayList<>();
        for (String pkg : targets) {
            if (pkg == null || pkg.isBlank() || pkg.equals(getPackageName())) continue;
            RootProfileOps.Result result = RootProfileOps.removePackage(pkg, userId);
            if (result.ok) removed++;
            else failed.add(pkg + (result.output.isEmpty() ? "" : "：" + result.output));
        }

        final int successCount = removed;
        runOnUiThread(() -> {
            if (failed.isEmpty()) {
                status.setText("空间已重置。\n\n已移除 " + successCount +
                        " 个隔离 App，并清空诊断与 Hook 配置。\nWork Profile 本身保持可用。" );
            } else {
                StringBuilder message = new StringBuilder();
                message.append("重置已完成，但有 ").append(failed.size()).append(" 个 App 移除失败。\n\n");
                for (String item : failed) message.append("• ").append(item).append('\n');
                status.setText(message.toString());
            }
            addCloseButton();
        });
    }

    private int currentUserId() {
        String value = Process.myUserHandle().toString();
        int open = value.indexOf('{');
        int close = value.indexOf('}', open + 1);
        if (open >= 0 && close > open + 1) {
            try { return Integer.parseInt(value.substring(open + 1, close)); }
            catch (NumberFormatException ignored) {}
        }
        return Process.myUserHandle().hashCode();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));

        TextView title = new TextView(this);
        title.setText("重置 YSpace");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setText("准备重置空间…");
        status.setTextSize(16);
        status.setPadding(0, dp(16), 0, dp(16));
        root.addView(status);

        setContentView(root);
    }

    private void fail(String message) {
        status.setText("重置失败：\n" + message);
        addCloseButton();
    }

    private void addCloseButton() {
        LinearLayout parent = (LinearLayout) status.getParent();
        if (parent.getChildCount() > 2) return;
        Button close = new Button(this);
        close.setText("完成");
        close.setOnClickListener(v -> finish());
        parent.addView(close, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
