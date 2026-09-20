package com.yagay.YSpace;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserManager;

import java.util.Collections;

public final class ProfileAdminReceiver extends DeviceAdminReceiver {
    static ComponentName component(Context context) {
        return new ComponentName(context, ProfileAdminReceiver.class);
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        ComponentName admin = component(context);
        if (dpm == null) return;

        try { dpm.setProfileName(admin, "YSpace"); } catch (Throwable ignored) {}
        try { dpm.setProfileEnabled(admin); } catch (Throwable ignored) {}

        // Default isolation: do not leak clipboard, contacts or caller-ID data out of the profile.
        try { dpm.addUserRestriction(admin, UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE); } catch (Throwable ignored) {}
        try { dpm.setCrossProfileContactsSearchDisabled(admin, true); } catch (Throwable ignored) {}
        try { dpm.setCrossProfileCallerIdDisabled(admin, true); } catch (Throwable ignored) {}

        // Allow only YSpace itself to use Android's user-consented Connected Apps bridge.
        if (Build.VERSION.SDK_INT >= 30) {
            try { dpm.setCrossProfilePackages(admin, Collections.singleton(context.getPackageName())); }
            catch (Throwable ignored) {}
        }
    }
}
