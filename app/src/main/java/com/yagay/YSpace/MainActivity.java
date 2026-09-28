package com.yagay.YSpace;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.CrossProfileApps;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private LinearLayout list;
    private TextView status;
    private EditText search;
    private PackageManager pm;
    private LauncherApps launcherApps;
    private CrossProfileApps crossProfileApps;
    private UserHandle workUser;
    private Button scopeSyncButton;
    private Button resetButton;
    private final ArrayList<ApplicationInfo> apps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
        if (dpm != null && dpm.isProfileOwnerApp(getPackageName())) {
            startActivity(new Intent(this, DiagnosticsActivity.class));
            finish();
            return;
        }

        pm = getPackageManager();
        launcherApps = getSystemService(LauncherApps.class);
        crossProfileApps = getSystemService(CrossProfileApps.class);

        buildUi();
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null) {
            workUser = findWorkUser();
            seedExistingRecommendations();
            updateStatus();
            render();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView title = new TextView(this);
        title.setText("YSpace");
        title.setTextSize(24);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, dp(5), 0, dp(8));
        root.addView(status);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);

        Button setup = new Button(this);
        setup.setText("初始化隔离");
        setup.setOnClickListener(v -> provisionProfile());
        tools.addView(setup, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button diagnostics = new Button(this);
        diagnostics.setText("真实检测记录");
        diagnostics.setOnClickListener(v -> openDiagnostics(null));
        tools.addView(diagnostics, new LinearLayout.LayoutParams(0, dp(48), 1));

        root.addView(tools);

        Button observerSetup = new Button(this);
        observerSetup.setText("LSPosed 诊断设置");
        observerSetup.setAllCaps(false);
        observerSetup.setOnClickListener(v -> ObserverSetup.showGuide(this, null));
        root.addView(observerSetup, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        scopeSyncButton = new Button(this);
        scopeSyncButton.setText("推荐 Hook / 同步 LSPosed");
        scopeSyncButton.setAllCaps(false);
        scopeSyncButton.setOnClickListener(v -> openScopeSync(recommendedWorkPackages(), true));
        root.addView(scopeSyncButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        resetButton = new Button(this);
        resetButton.setText("一键重置空间");
        resetButton.setAllCaps(false);
        resetButton.setOnClickListener(v -> confirmResetSpace());
        root.addView(resetButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        search = new EditText(this);
        search.setHint("搜索应用或包名");
        search.setSingleLine(true);
        search.setTextSize(16);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(search, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        workUser = findWorkUser();
        updateStatus();
    }

    private void loadApps() {
        apps.clear();
        List<ApplicationInfo> all;
        if (Build.VERSION.SDK_INT >= 33) {
            all = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0));
        } else {
            all = pm.getInstalledApplications(0);
        }
        for (ApplicationInfo ai : all) {
            if (ai.packageName.equals(getPackageName())) continue;
            if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
            apps.add(ai);
        }
        apps.sort(Comparator.comparing(a -> label(a).toLowerCase(Locale.ROOT)));
        render();
    }

    private void render() {
        if (list == null || pm == null) return;
        list.removeAllViews();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);

        for (ApplicationInfo ai : apps) {
            String label = label(ai);
            if (!q.isEmpty() &&
                    !label.toLowerCase(Locale.ROOT).contains(q) &&
                    !ai.packageName.toLowerCase(Locale.ROOT).contains(q)) continue;
            list.addView(appRow(ai, label));
        }
    }

    private LinearLayout appRow(ApplicationInfo ai, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        row.setMinimumHeight(dp(66));

        ImageView icon = new ImageView(this);
        try {
            Drawable d = ai.loadIcon(pm);
            icon.setImageDrawable(d);
        } catch (Throwable ignored) {}
        row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView text = new TextView(this);
        boolean inside = isInWorkProfile(ai.packageName);
        boolean recommended = RecommendedStore.contains(this, ai.packageName);
        text.setText(label + (inside ? "   ✓" : "") + "\n" + ai.packageName +
                "\n" + (inside
                ? "已隔离 · 点击启动" + (recommended ? " · 推荐 Hook" : "")
                : "点击加入隔离"));
        text.setTextSize(14);
        text.setPadding(dp(10), 0, dp(4), 0);
        row.addView(text, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        row.setOnClickListener(v -> {
            if (workUser == null) {
                provisionProfile();
            } else if (isInWorkProfile(ai.packageName)) {
                launchInWork(ai.packageName);
            } else {
                cloneToWork(ai.packageName);
            }
        });

        row.setOnLongClickListener(v -> {
            showAppActions(ai.packageName, label);
            return true;
        });

        return row;
    }

    private void showAppActions(String packageName, String label) {
        boolean inside = isInWorkProfile(packageName);
        ArrayList<String> items = new ArrayList<>();
        if (inside) items.add("启动隔离版本");
        if (inside) items.add("同步到 LSPosed");
        if (inside) items.add("LSPosed 诊断设置");
        items.add("查看真实检测记录");
        if (inside) items.add("从隔离空间移除");

        new AlertDialog.Builder(this)
                .setTitle(label)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    String action = items.get(which);
                    if (action.startsWith("启动")) launchInWork(packageName);
                    else if (action.startsWith("同步")) {
                        RecommendedStore.add(this, packageName);
                        openScopeSync(java.util.Collections.singletonList(packageName), true);
                    } else if (action.startsWith("LSPosed")) ObserverSetup.showGuide(this, packageName);
                    else if (action.startsWith("查看")) openDiagnostics(packageName);
                    else if (action.startsWith("从隔离")) removeFromWork(packageName);
                }).show();
    }

    private void cloneToWork(String packageName) {
        if (workUser == null) {
            provisionProfile();
            return;
        }
        final int userId = profileUserId(workUser);
        status.setText("正在加入隔离空间：" + packageName);
        new Thread(() -> {
            RootProfileOps.Result result = RootProfileOps.clonePackage(packageName, userId);
            runOnUiThread(() -> {
                if (result.ok) {
                    if (!packageName.equals("com.google.android.gms")) {
                        RecommendedStore.add(this, packageName);
                    }
                    Toast.makeText(this,
                            packageName.equals("com.google.android.gms")
                                    ? "已加入隔离空间；Google Play Services 不默认推荐 Hook"
                                    : "已加入隔离空间，并加入推荐 Hook 列表",
                            Toast.LENGTH_LONG).show();
                    updateStatus();
                    render();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("快速加入失败")
                            .setMessage("YSpace 当前的一键加入使用 Root 的 install-existing 通道。\n\n" +
                                    result.output +
                                    "\n\n这不会修改目标 App；非 Root 的 Connected Apps 安装通道会作为后续兼容后端。")
                            .setPositiveButton("确定", null).show();
                }
            });
        }, "YSpace-clone").start();
    }

    private void removeFromWork(String packageName) {
        if (workUser == null) return;
        final int userId = profileUserId(workUser);
        new Thread(() -> {
            RootProfileOps.Result result = RootProfileOps.removePackage(packageName, userId);
            runOnUiThread(() -> {
                if (result.ok) RecommendedStore.remove(this, packageName);
                Toast.makeText(this, result.ok ? "已从隔离空间移除" : result.output,
                        Toast.LENGTH_LONG).show();
                updateStatus();
                render();
            });
        }, "YSpace-remove").start();
    }

    private void launchInWork(String packageName) {
        if (workUser == null || launcherApps == null) return;
        try {
            List<LauncherActivityInfo> infos = launcherApps.getActivityList(packageName, workUser);
            if (infos.isEmpty()) {
                Toast.makeText(this, "隔离版本没有可启动入口", Toast.LENGTH_SHORT).show();
                return;
            }
            launcherApps.startMainActivity(infos.get(0).getComponentName(), workUser, null, null);
        } catch (Throwable t) {
            Toast.makeText(this, "启动失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openDiagnostics(String packageName) {
        if (workUser == null) {
            Toast.makeText(this, "请先初始化隔离空间", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, DiagnosticsActivity.class)
                .setComponent(new ComponentName(this, DiagnosticsActivity.class));
        if (packageName != null) intent.putExtra("package", packageName);

        if (Build.VERSION.SDK_INT >= 30 && crossProfileApps != null) {
            try {
                if (crossProfileApps.canInteractAcrossProfiles()) {
                    crossProfileApps.startActivity(intent, workUser, this);
                    return;
                }
                if (crossProfileApps.canRequestInteractAcrossProfiles()) {
                    startActivity(crossProfileApps.createRequestInteractAcrossProfilesIntent());
                    Toast.makeText(this, "授权后再点一次诊断，即可直接打开对应 App 的记录",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (Throwable ignored) {}
        }

        try {
            List<LauncherActivityInfo> infos = launcherApps.getActivityList(getPackageName(), workUser);
            if (!infos.isEmpty()) {
                launcherApps.startMainActivity(infos.get(0).getComponentName(), workUser, null, null);
                return;
            }
        } catch (Throwable ignored) {}

        Toast.makeText(this, "无法打开工作资料中的诊断器", Toast.LENGTH_LONG).show();
    }

    private void openScopeSync(List<String> packages, boolean autoSync) {
        if (workUser == null) {
            Toast.makeText(this, "请先初始化隔离空间", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> filtered = new ArrayList<>();
        if (packages != null) {
            for (String pkg : packages) {
                if (pkg != null && isInWorkProfile(pkg) && !pkg.equals(getPackageName())) {
                    filtered.add(pkg);
                }
            }
        }
        if (filtered.isEmpty()) {
            Toast.makeText(this, "没有可同步的工作资料推荐应用", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ScopeSyncActivity.class)
                .setComponent(new ComponentName(this, ScopeSyncActivity.class))
                .putStringArrayListExtra(ScopeSyncActivity.EXTRA_PACKAGES, filtered)
                .putExtra(ScopeSyncActivity.EXTRA_AUTO_SYNC, autoSync);

        if (Build.VERSION.SDK_INT >= 30 && crossProfileApps != null) {
            try {
                if (crossProfileApps.canInteractAcrossProfiles()) {
                    crossProfileApps.startActivity(intent, workUser, this);
                    return;
                }
                if (crossProfileApps.canRequestInteractAcrossProfiles()) {
                    startActivity(crossProfileApps.createRequestInteractAcrossProfilesIntent());
                    Toast.makeText(this,
                            "先允许 YSpace 跨资料连接；授权后再点一次同步。",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (Throwable t) {
                Toast.makeText(this, "打开工作资料同步页失败：" + t.getMessage(),
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        Toast.makeText(this,
                "系统未允许跨资料启动。请先在系统设置中允许 YSpace 的跨资料连接。",
                Toast.LENGTH_LONG).show();
    }

    private void confirmResetSpace() {
        if (workUser == null) {
            Toast.makeText(this, "隔离空间尚未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> targets = workspaceResetPackages();
        new AlertDialog.Builder(this)
                .setTitle("一键重置空间")
                .setMessage("将把 YSpace 管理的工作空间恢复到初始状态：\n\n" +
                        "• 移除 " + targets.size() + " 个隔离 App 及其工作资料应用数据\n" +
                        "• 清空真实检测记录\n" +
                        "• 清空推荐 Hook 和 YSpace 的 LSPosed 作用域\n\n" +
                        "Work Profile 本身和 Android/Google 系统组件会保留，主空间不会受影响。\n" +
                        "工作资料中的共享下载/照片和账号不会被强制删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重置", (d, w) -> openResetSpace(targets))
                .show();
    }

    private void openResetSpace(ArrayList<String> targets) {
        if (workUser == null || crossProfileApps == null) return;

        Intent intent = new Intent(this, ResetSpaceActivity.class)
                .setComponent(new ComponentName(this, ResetSpaceActivity.class))
                .putStringArrayListExtra(ResetSpaceActivity.EXTRA_PACKAGES, targets);

        if (Build.VERSION.SDK_INT >= 30) {
            try {
                if (crossProfileApps.canInteractAcrossProfiles()) {
                    crossProfileApps.startActivity(intent, workUser, this);
                    RecommendedStore.clear(this);
                    updateStatus();
                    return;
                }
                if (crossProfileApps.canRequestInteractAcrossProfiles()) {
                    startActivity(crossProfileApps.createRequestInteractAcrossProfilesIntent());
                    Toast.makeText(this,
                            "先允许 YSpace 跨资料连接；授权后再点一次“一键重置空间”。",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (Throwable t) {
                Toast.makeText(this, "打开工作资料重置器失败：" + t.getMessage(),
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        Toast.makeText(this, "当前系统不支持此跨资料重置方式。", Toast.LENGTH_LONG).show();
    }

    private ArrayList<String> workspaceResetPackages() {
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        for (ApplicationInfo ai : apps) {
            if (isInWorkProfile(ai.packageName) &&
                    !ai.packageName.equals(getPackageName()) &&
                    !ai.packageName.equals("com.google.android.gms")) {
                packages.add(ai.packageName);
            }
        }
        packages.addAll(recommendedWorkPackages());
        packages.remove(getPackageName());
        return new ArrayList<>(packages);
    }

    private List<String> recommendedWorkPackages() {
        ArrayList<String> out = new ArrayList<>();
        for (String pkg : RecommendedStore.list(this)) {
            if (isInWorkProfile(pkg)) out.add(pkg);
        }
        return out;
    }

    private void seedExistingRecommendations() {
        if (workUser == null || apps.isEmpty()) return;
        for (ApplicationInfo ai : apps) {
            if (ai.packageName.equals("com.google.android.gms")) continue;
            if (isInWorkProfile(ai.packageName)) RecommendedStore.add(this, ai.packageName);
        }
    }

    private boolean isInWorkProfile(String packageName) {
        if (workUser == null || launcherApps == null) return false;
        try { return !launcherApps.getActivityList(packageName, workUser).isEmpty(); }
        catch (Throwable ignored) { return false; }
    }

    private UserHandle findWorkUser() {
        if (crossProfileApps == null) return null;
        try {
            List<UserHandle> targets = crossProfileApps.getTargetUserProfiles();
            for (UserHandle user : targets) {
                if (Build.VERSION.SDK_INT >= 35) {
                    if (crossProfileApps.isManagedProfile(user)) return user;
                } else {
                    return user;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void provisionProfile() {
        if (findWorkUser() != null) {
            Toast.makeText(this, "隔离空间已经存在", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
        intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                ProfileAdminReceiver.component(this));
        try {
            startActivity(intent);
        } catch (Throwable t) {
            new AlertDialog.Builder(this)
                    .setTitle("无法创建工作资料")
                    .setMessage("系统拒绝了 Managed Profile 创建请求：" + t.getMessage())
                    .setPositiveButton("确定", null).show();
        }
    }

    private void updateStatus() {
        if (status == null) return;
        if (workUser == null) {
            status.setText("未初始化隔离空间 · 首次需要 Android 系统确认一次");
            if (scopeSyncButton != null) scopeSyncButton.setText("推荐 Hook / 同步 LSPosed");
            if (resetButton != null) resetButton.setEnabled(false);
        } else {
            int recommended = recommendedWorkPackages().size();
            status.setText("隔离空间已连接 · User " + profileUserId(workUser) +
                    " · 推荐 Hook " + recommended + " 个");
            if (scopeSyncButton != null) {
                scopeSyncButton.setText("推荐 Hook / 同步 LSPosed (" + recommended + ")");
            }
            if (resetButton != null) resetButton.setEnabled(true);
        }
    }

    private int profileUserId(UserHandle user) {
        if (user == null) return -1;
        String value = user.toString();
        int open = value.indexOf('{');
        int close = value.indexOf('}', open + 1);
        if (open >= 0 && close > open + 1) {
            try {
                return Integer.parseInt(value.substring(open + 1, close));
            } catch (NumberFormatException ignored) {}
        }
        return user.hashCode();
    }

    private String label(ApplicationInfo ai) {
        CharSequence s = ai.loadLabel(pm);
        return s == null ? ai.packageName : s.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
