package com.yagay.YSpace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

final class ObserverSetup {
    private static final String LSPOSED_MANAGER = "org.lsposed.manager";

    private ObserverSetup() {}

    static void showGuide(Activity activity, String targetPackage) {
        String target = targetPackage == null || targetPackage.isEmpty()
                ? "需要诊断的目标 App"
                : targetPackage;

        String message =
                "真实检测记录需要 YSpace 的 LSPosed Observer 注入目标 App。\n\n" +
                "请在 LSPosed 中：\n" +
                "1. 启用 YSpace 模块\n" +
                "2. 切换到 Work Profile / 工作资料用户\n" +
                "3. 在作用域中只勾目标 App：\n   " + target + "\n" +
                "4. 强制停止目标 App并重新打开\n\n" +
                "不需要勾 Android、SystemUI、Launcher、YSpace 或 Google Play 服务。\n" +
                "如果目标 App 还有独立安全组件 APK，需要把那个独立包也单独加入作用域。";

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("LSPosed 诊断设置")
                .setMessage(message)
                .setNegativeButton("关闭", null)
                .setPositiveButton("打开 LSPosed", null)
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    if (!openLsposed(activity)) {
                        Toast.makeText(activity,
                                "没有找到可直接启动的 LSPosed Manager。若使用隐藏/寄生 Manager，请手动打开 LSPosed。",
                                Toast.LENGTH_LONG).show();
                    }
                }));
        dialog.show();
    }

    static boolean openLsposed(Activity activity) {
        PackageManager pm = activity.getPackageManager();
        try {
            Intent launch = pm.getLaunchIntentForPackage(LSPOSED_MANAGER);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(launch);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
