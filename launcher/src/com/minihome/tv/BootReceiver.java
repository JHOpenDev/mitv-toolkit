package com.minihome.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** 开机/换盘/升级自愈: 重新注册首选并把极简桌面拉到前台。
 *  实测 MIUI 拦 BOOT_COMPLETED(开机进程表里本包没起过), 但 5.1 清单接收器还能收
 *  MEDIA_MOUNTED——USB 盘常年插着, 开机必广播, 拿它当开机钩子;
 *  MY_PACKAGE_REPLACED 在每次覆盖安装后触发, 顺带自愈。
 *  白天插拔 U 盘不抢屏: 只有开机 3 分钟窗口内才拉起桌面 */
public class BootReceiver extends BroadcastReceiver {

    private static final long BOOT_WINDOW_MS = 3 * 60 * 1000;

    public void onReceive(Context ctx, Intent i) {
        String a = i == null ? null : i.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(a);
        boolean media = "android.intent.action.MEDIA_MOUNTED".equals(a)
                || "android.intent.action.MEDIA_CHECKING".equals(a);
        boolean replaced = "android.intent.action.MY_PACKAGE_REPLACED".equals(a);
        if (!boot && !media && !replaced) return;
        try {
            HomeOwner.setup(ctx);
            if (replaced || (media && SystemClock.elapsedRealtime() < BOOT_WINDOW_MS)) {
                Intent it = new Intent(ctx, MainActivity.class);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                ctx.startActivity(it);
            }
        } catch (Throwable ignored) {
        }
    }
}
