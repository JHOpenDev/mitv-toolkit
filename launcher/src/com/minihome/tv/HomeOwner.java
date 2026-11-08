package com.minihome.tv;

import android.content.Context;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;

/** 固定默认桌面与升级提示拦截:
 *  有设备所有者时用 dpm 持久首选 + 隐藏老桌面(开机即极简, 老系统卡片临时放开);
 *  无所有者时退回 addPreferredActivity(已实测生效, 靠系统选择器"始终") */
public class HomeOwner {
    static final String LEGACY = "com.mitv.tvhome";

    public static ComponentName admin(Context a) {
        return new ComponentName(a, AdminReceiver.class);
    }

    public static boolean isOwner(Context a) {
        DevicePolicyManager dpm = (DevicePolicyManager) a.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isDeviceOwnerApp(a.getPackageName());
    }

    public static void setup(Context a) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) a.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(a.getPackageName())) {
                ComponentName adm = admin(a);
                IntentFilter home = new IntentFilter(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                dpm.addPersistentPreferredActivity(adm, home, new ComponentName(a, MainActivity.class));
                IntentFilter up = new IntentFilter("android.intent.action.HOME_UPDATE_DIALOG");
                up.addCategory(Intent.CATEGORY_DEFAULT);
                dpm.addPersistentPreferredActivity(adm, up, new ComponentName(a, UpgradeBlockActivity.class));
                IntentFilter mk = new IntentFilter(Intent.ACTION_VIEW);
                mk.addCategory(Intent.CATEGORY_DEFAULT);
                mk.addDataScheme("market");
                mk.addDataAuthority("appstore.mitv.xiaomi.com", null);
                mk.addDataPath("/details", 1);
                dpm.addPersistentPreferredActivity(adm, mk, new ComponentName(a, UpgradeBlockActivity.class));
                dpm.setApplicationHidden(adm, LEGACY, true);   // 开机不再被点名拉起
            } else {
                android.content.pm.PackageManager pm = a.getPackageManager();
                IntentFilter home = new IntentFilter(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                pm.addPreferredActivity(home, IntentFilter.MATCH_CATEGORY_EMPTY, null,
                        new ComponentName(a, MainActivity.class));
            }
        } catch (Throwable ignored) {
        }
    }

    /** 回到极简桌面时收回老桌面, 下次开机就不会再被拉起 */
    public static void rehide(Context a) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) a.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(a.getPackageName())) {
                dpm.setApplicationHidden(admin(a), LEGACY, true);
            }
        } catch (Throwable ignored) {
        }
    }
}
