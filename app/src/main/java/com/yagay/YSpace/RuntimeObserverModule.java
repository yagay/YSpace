package com.yagay.YSpace;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.os.Process;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/**
 * Observation-only LSPosed entry.
 *
 * Every hook calls chain.proceed() exactly once and returns the original result.
 * It never replaces arguments, return values, exceptions or integrity verdicts.
 */
public final class RuntimeObserverModule extends XposedModule {
    private final AtomicBoolean attachHookInstalled = new AtomicBoolean(false);
    private final AtomicBoolean runtimeHooksInstalled = new AtomicBoolean(false);

    private volatile String packageName = "";
    private volatile String processName = "";
    private volatile String session = "";
    private volatile Context appContext;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName() == null ? "" : param.getProcessName();
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String pkg = param.getPackageName();
        if (pkg == null || pkg.isEmpty() || "com.yagay.YSpace".equals(pkg)) return;

        packageName = pkg;
        if (processName.isEmpty()) processName = pkg;

        if (!attachHookInstalled.compareAndSet(false, true)) return;

        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            hook(attach).intercept(chain -> {
                Object result = chain.proceed();
                Object arg = chain.getArg(0);
                if (arg instanceof Context) {
                    appContext = ((Context) arg).getApplicationContext();
                    session = packageName + "-" + Process.myPid() + "-" + System.currentTimeMillis();
                    emit("session", "process start", "Application.attach(Context)", "started");
                    installRuntimeHooks(param.getDefaultClassLoader());
                }
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.ERROR, "YSpaceObserver", "Failed to hook Application.attach", t);
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!packageName.equals(param.getPackageName())) return;
        try {
            Context current = currentApplication();
            if (current != null) {
                appContext = current.getApplicationContext();
                if (session.isEmpty()) {
                    session = packageName + "-" + Process.myPid() + "-" + System.currentTimeMillis();
                    emit("session", "process ready", "PackageReady", "started");
                }
                installRuntimeHooks(param.getClassLoader());
            }
        } catch (Throwable ignored) {}
    }

    private void installRuntimeHooks(ClassLoader classLoader) {
        if (!runtimeHooksInstalled.compareAndSet(false, true)) return;

        hookPackageManager(classLoader);
        hookFiles();
        hookSystemProperties();
        hookSettings();
        hookRuntimeExec();
        hookClassLookup();
        hookDebug();
        hookTelephony();
        hookAccessibility();
        hookNetwork();
        hookIntegrity(classLoader);

        emit("observer", "Java / Android Framework", "YSpace observer", "active");
    }

    private void hookPackageManager(ClassLoader cl) {
        Class<?> c = load("android.app.ApplicationPackageManager", cl);
        if (c == null) return;

        String[] specific = {
                "getPackageInfo", "getApplicationInfo", "getLaunchIntentForPackage",
                "getInstallerPackageName", "getInstallSourceInfo", "getPackagesForUid",
                "getNameForUid", "getPackageUid", "getSigningInfo"
        };
        for (String name : specific) {
            hookNamed(c, name, "package", chain -> {
                Object arg0 = chain.getArgs().isEmpty() ? null : chain.getArg(0);
                return stringValue(arg0);
            }, false, false);
        }

        String[] enumerations = {
                "getInstalledPackages", "getInstalledApplications",
                "queryIntentActivities", "queryIntentServices", "queryBroadcastReceivers",
                "resolveActivity", "resolveService"
        };
        for (String name : enumerations) {
            hookNamed(c, name, "package", chain -> {
                if (chain.getArgs().isEmpty()) return "* enumerate";
                Object first = chain.getArg(0);
                if (first instanceof Intent) return ((Intent) first).toUri(0);
                return "* enumerate";
            }, false, true);
        }
    }

    private void hookFiles() {
        String[] names = {"exists", "canRead", "canWrite", "isFile", "isDirectory", "list", "listFiles"};
        for (String name : names) {
            hookNamed(File.class, name, "file", chain -> {
                Object self = chain.getThisObject();
                if (!(self instanceof File)) return null;
                String path = ((File) self).getAbsolutePath();
                return environmentPath(path) ? path : null;
            }, false, false);
        }
    }

    private void hookSystemProperties() {
        Class<?> c = load("android.os.SystemProperties", null);
        if (c == null) return;
        String[] names = {"get", "getInt", "getLong", "getBoolean"};
        for (String name : names) {
            hookNamed(c, name, "property", chain ->
                    chain.getArgs().isEmpty() ? null : stringValue(chain.getArg(0)),
                    false, false);
        }
    }

    private void hookSettings() {
        String[] classes = {
                "android.provider.Settings$Secure",
                "android.provider.Settings$System",
                "android.provider.Settings$Global"
        };
        for (String className : classes) {
            Class<?> c = load(className, null);
            if (c == null) continue;
            String prefix = className.substring(className.lastIndexOf('$') + 1).toLowerCase(Locale.ROOT);
            String[] names = {"getString", "getInt", "getLong", "getFloat"};
            for (String name : names) {
                hookNamed(c, name, "setting", chain -> {
                    if (chain.getArgs().size() < 2) return null;
                    return prefix + ":" + stringValue(chain.getArg(1));
                }, true, false);
            }
        }

        Class<?> settings = load("android.provider.Settings", null);
        if (settings != null) {
            hookNamed(settings, "canDrawOverlays", "overlay", chain -> "overlay permission",
                    false, false);
        }
    }

    private void hookRuntimeExec() {
        hookNamed(Runtime.class, "exec", "command", chain -> {
            if (chain.getArgs().isEmpty()) return null;
            Object cmd = chain.getArg(0);
            if (cmd instanceof String[]) return String.join(" ", (String[]) cmd);
            return stringValue(cmd);
        }, false, false);
    }

    private void hookClassLookup() {
        hookNamed(Class.class, "forName", "class", chain -> {
            if (chain.getArgs().isEmpty()) return null;
            String name = stringValue(chain.getArg(0));
            return environmentClass(name) ? name : null;
        }, false, false);
    }

    private void hookDebug() {
        Class<?> c = load("android.os.Debug", null);
        if (c == null) return;
        hookNamed(c, "isDebuggerConnected", "debug", chain -> "debugger", false, false);
        hookNamed(c, "waitingForDebugger", "debug", chain -> "waiting debugger", false, false);
    }

    private void hookTelephony() {
        Class<?> c = load("android.telephony.TelephonyManager", null);
        if (c == null) return;
        String[] names = {
                "getImei", "getMeid", "getDeviceId", "getSubscriberId", "getSimSerialNumber",
                "getLine1Number", "getNetworkOperator", "getSimOperator", "getPhoneType"
        };
        for (String name : names) {
            hookNamed(c, name, "telephony", chain -> name, true, false);
        }
    }

    private void hookAccessibility() {
        Class<?> c = load("android.view.accessibility.AccessibilityManager", null);
        if (c == null) return;
        hookNamed(c, "getEnabledAccessibilityServiceList", "accessibility",
                chain -> "enabled accessibility services", false, true);
        hookNamed(c, "isEnabled", "accessibility",
                chain -> "accessibility enabled", false, false);
        hookNamed(c, "isTouchExplorationEnabled", "accessibility",
                chain -> "touch exploration", false, false);
    }

    private void hookNetwork() {
        Class<?> connectivity = load("android.net.ConnectivityManager", null);
        if (connectivity != null) {
            String[] names = {"getActiveNetwork", "getNetworkCapabilities", "getLinkProperties", "getDefaultProxy"};
            for (String name : names) {
                hookNamed(connectivity, name, "network", chain -> name, false, false);
            }
        }

        Class<?> inet = load("java.net.InetAddress", null);
        if (inet != null) {
            hookNamed(inet, "getByName", "dns", chain ->
                    chain.getArgs().isEmpty() ? null : stringValue(chain.getArg(0)),
                    false, false);
            hookNamed(inet, "getAllByName", "dns", chain ->
                    chain.getArgs().isEmpty() ? null : stringValue(chain.getArg(0)),
                    false, false);
        }
    }

    private void hookIntegrity(ClassLoader cl) {
        String[] classes = {
                "com.google.android.play.core.integrity.IntegrityManager",
                "com.google.android.play.core.integrity.StandardIntegrityManager",
                "com.google.android.gms.safetynet.SafetyNetClient"
        };
        for (String className : classes) {
            Class<?> c = load(className, cl);
            if (c == null) continue;
            for (Method m : c.getMethods()) {
                String n = m.getName().toLowerCase(Locale.ROOT);
                if (n.contains("integrity") || n.contains("attest")) {
                    hookExact(m, "integrity", chain -> className + "." + m.getName(), true, false);
                }
            }
        }
    }

    private interface ObjectExtractor {
        String object(XposedInterface.Chain chain) throws Throwable;
    }

    private void hookNamed(Class<?> clazz, String methodName, String type,
                           ObjectExtractor extractor, boolean sensitiveResult,
                           boolean listDetails) {
        try {
            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) continue;
                hookExact(method, type, extractor, sensitiveResult, listDetails);
            }
        } catch (Throwable t) {
            log(android.util.Log.WARN, "YSpaceObserver",
                    "Enumerating hooks failed " + clazz.getName() + "." + methodName);
        }
    }

    private void hookExact(Method method, String type, ObjectExtractor extractor,
                           boolean sensitiveResult, boolean listDetails) {
        try {
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                String object;
                try {
                    object = extractor.object(chain);
                } catch (Throwable t) {
                    object = "<extract failed:" + t.getClass().getSimpleName() + ">";
                }

                // null means intentionally filtered out (e.g. ordinary non-environment file access).
                if (object == null) return chain.proceed();

                Object result;
                try {
                    result = chain.proceed();
                } catch (Throwable t) {
                    emit(type, object, api(method), "throws:" + t.getClass().getSimpleName());
                    throw t;
                }

                String summary;
                if (sensitiveResult) summary = redactedSummary(result);
                else if (listDetails) summary = collectionSummary(result);
                else summary = valueSummary(result);

                emit(type, object, api(method), summary);
                return result;
            });
        } catch (Throwable t) {
            log(android.util.Log.WARN, "YSpaceObserver",
                    "Hook failed " + api(method) + ": " + t.getClass().getSimpleName());
        }
    }

    private void emit(String type, String object, String api, String result) {
        Context context = appContext;
        if (context == null || packageName.isEmpty()) return;
        try {
            Intent event = new Intent(EventReceiver.ACTION);
            event.setComponent(new ComponentName(
                    "com.yagay.YSpace", "com.yagay.YSpace.EventReceiver"));
            event.putExtra("ts", System.currentTimeMillis());
            event.putExtra("session", session);
            event.putExtra("source_package", packageName);
            event.putExtra("process", processName);
            event.putExtra("type", clip(type, 80));
            event.putExtra("object", clip(object, 1500));
            event.putExtra("api", clip(api, 500));
            event.putExtra("result", clip(result, 700));
            context.sendBroadcast(event);
        } catch (Throwable ignored) {}
    }

    private static String api(Method m) {
        return m.getDeclaringClass().getName() + "." + m.getName() + "()";
    }

    private static String valueSummary(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return String.valueOf(value);
        if (value instanceof CharSequence) {
            String s = String.valueOf(value);
            return clip(s, 180);
        }
        if (value.getClass().isArray()) return "count=" + Array.getLength(value);
        if (value instanceof Collection) return "count=" + ((Collection<?>) value).size();
        return value.getClass().getSimpleName() + ":non-null";
    }

    private static String redactedSummary(Object value) {
        if (value == null) return "null";
        if (value instanceof CharSequence) return ((CharSequence) value).length() == 0 ? "empty" : "<redacted non-empty>";
        return valueSummary(value);
    }

    private static String collectionSummary(Object value) {
        if (value == null) return "null";
        if (!(value instanceof Collection)) return valueSummary(value);

        Collection<?> c = (Collection<?>) value;
        StringBuilder out = new StringBuilder("count=").append(c.size());
        int n = 0;
        for (Object item : c) {
            String pkg = packageFromItem(item);
            if (pkg == null || pkg.isEmpty()) continue;
            if (n++ == 0) out.append(" ["); else out.append(", ");
            out.append(pkg);
            if (n >= 40) {
                out.append(", …");
                break;
            }
        }
        if (n > 0) out.append("]");
        return clip(out.toString(), 700);
    }

    private static String packageFromItem(Object item) {
        if (item instanceof PackageInfo) return ((PackageInfo) item).packageName;
        if (item instanceof ApplicationInfo) return ((ApplicationInfo) item).packageName;
        if (item instanceof ResolveInfo) {
            ResolveInfo r = (ResolveInfo) item;
            if (r.activityInfo != null) return r.activityInfo.packageName;
            if (r.serviceInfo != null) return r.serviceInfo.packageName;
            if (r.providerInfo != null) return r.providerInfo.packageName;
        }
        return null;
    }

    private static boolean environmentPath(String p) {
        if (p == null) return false;
        String s = p.toLowerCase(Locale.ROOT);
        return s.startsWith("/proc/") || s.equals("/proc") ||
                s.startsWith("/sys/") || s.equals("/sys") ||
                s.startsWith("/system/") || s.startsWith("/vendor/") ||
                s.startsWith("/product/") || s.startsWith("/data/adb") ||
                s.startsWith("/data/local") || s.startsWith("/dev/") ||
                s.contains("magisk") || s.contains("kernelsu") || s.contains("/ksu") ||
                s.contains("xposed") || s.contains("lsposed") || s.contains("zygisk") ||
                s.contains("riru") || s.contains("frida") ||
                s.endsWith("/su") || s.contains("/su/");
    }

    private static boolean environmentClass(String name) {
        if (name == null) return false;
        String s = name.toLowerCase(Locale.ROOT);
        return s.contains("xposed") || s.contains("lsposed") || s.contains("magisk") ||
                s.contains("kernelsu") || s.contains("zygisk") || s.contains("frida") ||
                s.contains("riru") || s.contains("substrate");
    }

    private static Context currentApplication() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method current = at.getDeclaredMethod("currentApplication");
            Object app = current.invoke(null);
            return app instanceof Context ? (Context) app : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class<?> load(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable ignored) {
            try { return Class.forName(name); } catch (Throwable ignoredAgain) { return null; }
        }
    }

    private static String stringValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String[]) return String.join(",", (String[]) value);
        return clip(String.valueOf(value), 1000);
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
