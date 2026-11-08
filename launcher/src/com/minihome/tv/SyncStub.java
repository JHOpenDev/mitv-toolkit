package com.minihome.tv;

import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.accounts.Account;
import android.os.Bundle;
import android.os.SystemClock;

/** 周期同步回调: 同步调度由系统框架开机后自行恢复, 不走广播、不受 MIUI 自启动管制。
 *  双条件弹出极简桌面(任一命中):
 *  1. freshBoot: 内核计时 <5 分钟 = 刚上过电(快速开关机也算, elapsedRealtime 开机即归零);
 *  2. longGap: 墙钟打卡间隔 >3 分钟 = 长时间断电(系统死亡, 同步停摆)。
 *  待机唤醒不算(系统存活时同步不停, 打卡间隔恒 60 秒), 唤醒后屏幕回到原界面 */
public class SyncStub extends AbstractThreadedSyncAdapter {

    private static final long FRESH_BOOT_MS = 5 * 60 * 1000L;
    private static final long OFF_GAP_MS = 3 * 60 * 1000L;
    private final Context mCtx;

    public SyncStub(Context c) {
        super(c, true);
        mCtx = c;
    }

    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult result) {
        long nowElapsed = SystemClock.elapsedRealtime();
        long nowWall = System.currentTimeMillis();
        android.content.SharedPreferences sp =
                mCtx.getSharedPreferences("home", Context.MODE_PRIVATE);
        long lastSeen = sp.getLong("sync_seen_wall", -1);
        sp.edit().putLong("sync_seen_wall", nowWall).commit();
        boolean freshBoot = nowElapsed < FRESH_BOOT_MS;
        boolean longGap = lastSeen > 0 && nowWall - lastSeen > OFF_GAP_MS;
        android.util.Log.i("MiniWall", "elapsed=" + (nowElapsed / 1000) + "s gap="
                + (lastSeen > 0 ? (nowWall - lastSeen) / 1000 : -1)
                + " freshBoot=" + freshBoot + " longGap=" + longGap);
        if (!freshBoot && !longGap) return;
        try {
            HomeOwner.setup(mCtx);
            Intent it = new Intent(mCtx, MainActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mCtx.startActivity(it);
        } catch (Throwable ignored) {
        }
    }
}
