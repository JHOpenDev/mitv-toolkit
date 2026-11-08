package com.minihome.tv;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.widget.Toast;

/** 老系统卡片: 有所有者时临时放开 2015 桌面并进入; 返回极简桌面时自动收回 */
public class LegacyLauncher {
    public static void open(Activity a) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) a.getSystemService(Activity.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(a.getPackageName())) {
                dpm.setApplicationHidden(HomeOwner.admin(a), HomeOwner.LEGACY, false);
            }
            Intent it = a.getPackageManager().getLaunchIntentForPackage(HomeOwner.LEGACY);
            if (it == null) {
                it = new Intent();
                it.setClassName(HomeOwner.LEGACY, "com.mitv.tvhome.MainActivity");
            }
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(it);
        } catch (Throwable t) {
            Toast.makeText(a, "无法进入老系统", Toast.LENGTH_SHORT).show();
        }
    }
}
