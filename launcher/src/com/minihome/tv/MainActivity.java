package com.minihome.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TVPLAYER_PKG = "com.xiaomi.mitv.tvplayer";
    private static final String SRC_ACTIVITY = "com.xiaomi.mitv.tvplayer.ExternalSourceActivity";
    private static final int ACCENT = 0xFFFF9130;
    private static final int COLS = 2;
    private static final long IDLE_MS = 10 * 60 * 1000;   // 10 分钟进屏保

    private final List<View> mGrid = new ArrayList<View>();
    private int mIdx = 0;
    private ImageView mWifi;
    private Handler mHandler;
    private long mLastTouch;
    private TextView mWeather;
    private long mLastWeather;
    private static final long WEATHER_STALE_MS = 30 * 60 * 1000;   // 天气最多半小时刷新一次

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF17203A, 0xFF0A0C14});

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundDrawable(bg);

        root.addView(buildTopBar());
        root.addView(hLine());
        root.addView(buildBody(), new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        focusCurrent();
        mHandler = new Handler();
        mLastTouch = SystemClock.elapsedRealtime();

        updateWifi();
        fetchWeather();
        mLastWeather = SystemClock.elapsedRealtime();
        BootSound.play(this);
    }

    /** 顶栏: 左「极简桌面」 / 右 WiFi + 天气 + 日期时间 */
    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(88, 30, 60, 22);

        TextView brand = new TextView(this);
        brand.setText("极简桌面");
        brand.setTextSize(19);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brand.setTextColor(0xFFE8EBF2);
        brand.setLetterSpacing(0.3f);
        bar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));

        mWifi = new ImageView(this);
        mWifi.setLayoutParams(new LinearLayout.LayoutParams(46, 40));
        mWifi.setPadding(0, 4, 22, 0);
        bar.addView(mWifi);

        mWeather = new TextView(this);
        mWeather.setTextSize(17);
        mWeather.setTextColor(0xFFC8CDD8);
        mWeather.setPadding(0, 0, 30, 0);
        bar.addView(mWeather);

        TextClock mac = new TextClock(this, null, 0);
        mac.setFormat12Hour(null);
        mac.setFormat24Hour("yyyy年M月d日 EEEE  HH:mm:ss");
        mac.setTextSize(17);
        mac.setTextColor(0xFFC8CDD8);
        bar.addView(mac);
        return bar;
    }

    private View hLine() {
        View v = new View(this);
        v.setBackgroundColor(0x26FFFFFF);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 2);
        v.setLayoutParams(p);
        return v;
    }

    /** 主体: 居中 2×2 卡片组 (560x240, 图标100) */
    private LinearLayout buildBody() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);

        LinearLayout row1 = newRow();
        LinearLayout row2 = newRow();

        addBigTile(row1, "有线电视", "in:25", R.drawable.ic_cable);
        addBigTile(row1, "Switch", "in:24", R.drawable.ic_switch);
        addBigTile(row2, "夸克网盘", "pkg:com.quark.yun.tv", R.drawable.ic_quark);
        addBigTile(row2, "设置", "pkg:com.xiaomi.mitv.settings", R.drawable.ic_settings);

        body.addView(row1);
        body.addView(row2);
        return body;
    }

    private LinearLayout newRow() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    private void addBigTile(LinearLayout row, String label, String tag, int iconRes) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setTag(tag);

        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setLayoutParams(new LinearLayout.LayoutParams(130, 130));
        card.addView(iv);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(18);
        tv.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        tv.setTextColor(Color.WHITE);
        tv.setPadding(0, 10, 0, 0);
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(new LinearLayout.LayoutParams(130, -2));
        card.addView(tv);

        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(this);
        applyBg(card, false);
        mGrid.add(card);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(560, 240);
        p.leftMargin = 22;
        p.rightMargin = 22;
        p.topMargin = 22;
        p.bottomMargin = 22;
        row.addView(card, p);
    }

    private void applyBg(View v, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(28);
        if (focused) {
            g.setColor(0xF02B3146);
            g.setStroke(6, ACCENT);
        } else {
            g.setColor(0xE61C2130);
            g.setStroke(1, 0x1FFFFFFF);
        }
        v.setBackgroundDrawable(g);
    }

    /** WiFi 信号图标 */
    private void updateWifi() {
        int iconId = 0;
        int alpha = 0x50;
        try {
            android.net.wifi.WifiManager wm =
                    (android.net.wifi.WifiManager) getSystemService(WIFI_SERVICE);
            int rssi = wm.getConnectionInfo().getRssi();
            int level = android.net.wifi.WifiManager.calculateSignalLevel(rssi, 5);
            if (level > 0) {
                alpha = 0xFF;
                iconId = getResources().getIdentifier(
                        "stat_sys_wifi_signal_" + Math.max(1, level), "drawable", "android");
            } else {
                iconId = getResources().getIdentifier(
                        "stat_sys_wifi_signal_1", "drawable", "android");
            }
        } catch (Throwable ignored) {
        }
        if (iconId != 0) {
            try {
                mWifi.setImageResource(iconId);
                mWifi.setColorFilter(0xFFC8CDD8, android.graphics.PorterDuff.Mode.SRC_ATOP);
                mWifi.setImageAlpha(alpha);
            } catch (Throwable ignored) {
            }
        }
    }

    private void fetchWeather() {
        new WeatherTask(this, mWeather).start();
    }

    private View current() { return mGrid.get(mIdx); }

    private void focusCurrent() {
        for (View v : mGrid) applyBg(v, false);
        View v = current();
        applyBg(v, true);
        v.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (mIdx % COLS > 0) { mIdx--; focusCurrent(); return true; }
                return false;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mIdx % COLS < COLS - 1) { mIdx++; focusCurrent(); return true; }
                return false;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (mIdx >= COLS) { mIdx -= COLS; focusCurrent(); return true; }
                return false;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (mIdx + COLS < mGrid.size()) { mIdx += COLS; focusCurrent(); return true; }
                return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        stopService(new Intent(this, TimerService.class));   // 回到桌面即结束计时
        focusCurrent();
        updateWifi();
        mLastTouch = SystemClock.elapsedRealtime();
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler.postDelayed(new IdleCheck(this), 30000);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mHandler != null) mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        mLastTouch = SystemClock.elapsedRealtime();
    }

    /** IdleCheck 每 30 秒回调 */
    public void checkIdle() {
        if (SystemClock.elapsedRealtime() - mLastWeather >= WEATHER_STALE_MS) {
            mLastWeather = SystemClock.elapsedRealtime();
            fetchWeather();
        }
        if (SystemClock.elapsedRealtime() - mLastTouch >= IDLE_MS && PhotoScan.hasPhotos()) {
            startActivity(new Intent(this, ScreenSaverActivity.class));
        }
        mHandler.postDelayed(new IdleCheck(this), 30000);
    }

    public void onClick(View v) {
        String tag = String.valueOf(v.getTag());
        if ("in:24".equals(tag)) {                 // Switch: 限时40分钟
            Intent timer = new Intent(this, TimerService.class);
            timer.putExtra("secs", 40 * 60);
            startService(timer);
        }
        if (tag.startsWith("in:")) {
            switchSource(tag.substring(3));
        } else if (tag.startsWith("pkg:")) {
            launch(tag.substring(4));
        } else if (tag.startsWith("dir:")) {
            Intent it = new Intent(this, FileListActivity.class);
            it.putExtra("path", tag.substring(4));
            startActivity(it);
        }
    }

    private void launch(String pkg) {
        try {
            Intent it = getPackageManager().getLaunchIntentForPackage(pkg);
            if (it != null) {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                return;
            }
        } catch (Exception ignored) {
        }
        Toast.makeText(this, "未安装: " + pkg, Toast.LENGTH_SHORT).show();
    }

    private void switchSource(String id) {
        try {
            Intent it = new Intent();
            it.setClassName(TVPLAYER_PKG, SRC_ACTIVITY);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            it.putExtra("input", Integer.parseInt(id));
            it.putExtra("no_launch_panel", true);
            startActivity(it);
        } catch (Exception e) {
            Toast.makeText(this, "无法切换信号源", Toast.LENGTH_SHORT).show();
        }
    }
}
