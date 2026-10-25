package com.minihome.tv;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

/** Switch 游戏限时: 隐藏计时, 最后1分钟角标倒计时(无悬浮权限则用Toast), 到点回桌面 */
public class TimerService extends Service {

    private Handler mHandler = new Handler();
    private TimerTick mTick = new TimerTick(this);
    private long mEndAt;
    private long mShowAtMs;          // 剩余多少毫秒时开始显示
    private TextView mOverlay;
    private WindowManager mWm;
    private boolean mOverlayOk = false;
    private int mLastToastSec = -1;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int secs = intent.getIntExtra("secs", 40 * 60);
        int showAt = intent.getIntExtra("showAt", 61);
        mEndAt = SystemClock.elapsedRealtime() + secs * 1000L;
        mShowAtMs = showAt * 1000L;
        removeOverlay();
        mLastToastSec = -1;
        mHandler.removeCallbacks(mTick);
        mHandler.postDelayed(mTick, 500);
        return START_NOT_STICKY;
    }

    public void tick() {
        long remain = mEndAt - SystemClock.elapsedRealtime();
        if (remain <= 0) {
            finishUp();
            return;
        }
        if (remain <= mShowAtMs) {
            int sec = (int) ((remain + 999) / 1000);
            String text = "🎮 " + sec / 60 + ":" + String.format("%02d", sec % 60);
            if (!showOverlay(text)) toastPhase(sec);
        }
        mHandler.postDelayed(mTick, 500);
    }

    /** 悬浮角标; 没权限返回false走Toast */
    private boolean showOverlay(String text) {
        if (mOverlay != null && mOverlayOk) {
            mOverlay.setText(text);
            return true;
        }
        if (mOverlay != null && !mOverlayOk) return false;
        try {
            mWm = (WindowManager) getSystemService(WINDOW_SERVICE);
            mOverlay = new TextView(this);
            mOverlay.setText(text);
            mOverlay.setTextSize(22);
            mOverlay.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            mOverlay.setTextColor(Color.WHITE);
            mOverlay.setPadding(28, 14, 28, 14);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(16);
            g.setColor(0xE6202530);
            g.setStroke(2, 0xFFFF9130);
            mOverlay.setBackgroundDrawable(g);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.RIGHT;
            p.x = 48;
            p.y = 120;
            mWm.addView(mOverlay, p);
            mOverlayOk = true;
            return true;
        } catch (Throwable t) {
            mOverlayOk = false;
            return false;
        }
    }

    /** 无悬浮权限时: 关键节点横幅 + 最后5秒大数字倒数 */
    private void toastPhase(int sec) {
        if (sec == 60 && mLastToastSec != 60) {
            mLastToastSec = 60;
            banner("🎮 Switch 还剩 1 分钟", 0);
        } else if (sec == 30 && mLastToastSec != 30) {
            mLastToastSec = 30;
            banner("🎮 还剩 30 秒", 0);
        } else if (sec == 5 && mLastToastSec != 5) {
            mLastToastSec = 5;
            banner(null, 5);
        }
    }

    private void banner(String text, int countdown) {
        try {
            Intent it = new Intent(this, TimerBannerActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (text != null) it.putExtra("text", text);
            if (countdown > 0) it.putExtra("countdown", countdown);
            startActivity(it);
        } catch (Throwable ignored) {
        }
    }

    private void finishUp() {
        removeOverlay();
        Toast.makeText(this, "🎮 Switch 时间到！已返回桌面", Toast.LENGTH_LONG).show();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        } catch (Throwable ignored) {
        }
        stopSelf();
    }

    private void removeOverlay() {
        if (mOverlay != null && mWm != null) {
            try { mWm.removeView(mOverlay); } catch (Throwable ignored) {
            }
        }
        mOverlay = null;
        mOverlayOk = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mTick);
        removeOverlay();
    }
}
