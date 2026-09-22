package com.yagay.YSpace;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RecommendedStore {
    private static final String PREFS = "hook_recommendations";
    private static final String KEY = "packages";

    private RecommendedStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean contains(Context context, String packageName) {
        return prefs(context).getStringSet(KEY, Collections.emptySet()).contains(packageName);
    }

    static void add(Context context, String packageName) {
        if (packageName == null || packageName.isBlank() ||
                packageName.equals(context.getPackageName())) return;
        Set<String> next = new HashSet<>(prefs(context).getStringSet(KEY, Collections.emptySet()));
        next.add(packageName);
        prefs(context).edit().putStringSet(KEY, next).apply();
    }

    static void remove(Context context, String packageName) {
        Set<String> next = new HashSet<>(prefs(context).getStringSet(KEY, Collections.emptySet()));
        next.remove(packageName);
        prefs(context).edit().putStringSet(KEY, next).apply();
    }

    static List<String> list(Context context) {
        ArrayList<String> out = new ArrayList<>(
                prefs(context).getStringSet(KEY, Collections.emptySet()));
        Collections.sort(out);
        return out;
    }
}
