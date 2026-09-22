package com.yagay.YSpace;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

final class LsposedBridge {
    interface Listener {
        void onChanged();
    }

    interface ScopeCallback {
        void onDone(boolean ok, String message, List<String> approved);
    }

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile XposedService service;
    private static volatile String lastMessage = "等待 LSPosed 服务";

    private LsposedBridge() {}

    static void init(Context context) {
        if (!INITIALIZED.compareAndSet(false, true)) return;

        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService incoming) {
                try {
                    XposedService current = service;
                    if (current == null ||
                            incoming.getFrameworkName().toLowerCase().contains("lsposed") ||
                            !current.getFrameworkName().toLowerCase().contains("lsposed")) {
                        service = incoming;
                        lastMessage = incoming.getFrameworkName() + " " +
                                incoming.getFrameworkVersion() + " · API " +
                                incoming.getAPIVersion();
                    }
                } catch (Throwable t) {
                    service = incoming;
                    lastMessage = "Xposed 服务已连接";
                }
                notifyChanged();
            }

            @Override
            public void onServiceDied(XposedService dead) {
                if (service == dead) {
                    service = null;
                    lastMessage = "LSPosed 服务已断开";
                    notifyChanged();
                }
            }
        });
    }

    static void addListener(Listener listener) {
        if (listener != null) LISTENERS.add(listener);
    }

    static void removeListener(Listener listener) {
        if (listener != null) LISTENERS.remove(listener);
    }

    static boolean isConnected() {
        return service != null;
    }

    static String statusText() {
        return lastMessage;
    }

    static List<String> getScope() {
        XposedService s = service;
        if (s == null) return Collections.emptyList();
        try {
            return new ArrayList<>(s.getScope());
        } catch (Throwable t) {
            lastMessage = "读取 LSPosed 作用域失败：" + t.getClass().getSimpleName();
            return Collections.emptyList();
        }
    }

    static void requestScopes(Collection<String> packages, ScopeCallback callback) {
        XposedService s = service;
        if (s == null) {
            callback.onDone(false, "LSPosed 服务未连接。请确认 YSpace 模块已启用。",
                    Collections.emptyList());
            return;
        }

        LinkedHashSet<String> requested = new LinkedHashSet<>();
        if (packages != null) {
            for (String pkg : packages) {
                if (pkg != null && !pkg.isBlank()) requested.add(pkg);
            }
        }

        List<String> existing;
        try {
            existing = s.getScope();
        } catch (Throwable t) {
            callback.onDone(false, "读取当前作用域失败：" + t.getMessage(),
                    Collections.emptyList());
            return;
        }
        requested.removeAll(existing);

        if (requested.isEmpty()) {
            callback.onDone(true, "推荐应用已经全部在 LSPosed 作用域中。",
                    Collections.emptyList());
            return;
        }

        ArrayList<String> add = new ArrayList<>(requested);
        java.util.concurrent.atomic.AtomicInteger remaining =
                new java.util.concurrent.atomic.AtomicInteger(add.size());
        List<String> approved = java.util.Collections.synchronizedList(new ArrayList<>());
        List<String> failed = java.util.Collections.synchronizedList(new ArrayList<>());

        for (String pkg : add) {
            try {
                s.requestScope(pkg, new XposedService.OnScopeEventListener() {
                    private void finish(String error) {
                        if (error != null && !error.isBlank()) failed.add(error);
                        if (remaining.decrementAndGet() == 0) {
                            boolean ok = failed.isEmpty();
                            String message = ok
                                    ? "LSPosed 已同步 " + approved.size() + " 个推荐应用"
                                    : "LSPosed 已同步 " + approved.size() + " 个，失败 " + failed.size() + " 个";
                            lastMessage = message;
                            notifyChanged();
                            callback.onDone(ok, message, new ArrayList<>(approved));
                        }
                    }

                    @Override
                    public void onScopeRequestApproved(String packageName) {
                        approved.add(packageName);
                        finish(null);
                    }

                    @Override
                    public void onScopeRequestDenied(String packageName) {
                        finish(packageName + "：用户拒绝");
                    }

                    @Override
                    public void onScopeRequestTimeout(String packageName) {
                        finish(packageName + "：授权超时");
                    }

                    @Override
                    public void onScopeRequestFailed(String packageName, String message) {
                        finish(packageName + "：" + message);
                    }
                });
            } catch (Throwable t) {
                failed.add(pkg + "：" + t.getClass().getSimpleName());
                if (remaining.decrementAndGet() == 0) {
                    boolean ok = failed.isEmpty();
                    String message = ok
                            ? "LSPosed 已同步 " + approved.size() + " 个推荐应用"
                            : "LSPosed 已同步 " + approved.size() + " 个，失败 " + failed.size() + " 个";
                    lastMessage = message;
                    notifyChanged();
                    callback.onDone(ok, message, new ArrayList<>(approved));
                }
            }
        }
    }

    private static void notifyChanged() {
        for (Listener listener : LISTENERS) {
            try { listener.onChanged(); } catch (Throwable ignored) {}
        }
    }
}
