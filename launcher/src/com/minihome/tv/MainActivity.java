package com.minihome.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TVPLAYER_PKG = "com.xiaomi.mitv.tvplayer";
    private static final String SRC_ACTIVITY = "com.xiaomi.mitv.tvplayer.ExternalSourceActivity";
    private static final int ACCENT = 0xFFFF9130;
    private static final int COLS = 3;

    private final List<View> mGrid = new ArrayList<View>();
    private int mIdx = 0;
    private ImageView mWifi;
    private TextView mWeather;

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

        LinearLayout box = new LinearLayout(this);
        box.setBackgroundDrawable(bg);
        box.setOrientation(LinearLayout.VERTICAL);

        box.addView(buildTopBar());
        box.addView(hLine());

        LinearLayout gridBox = new LinearLayout(this);
        gridBox.setOrientation(LinearLayout.VERTICAL);
        gridBox.setGravity(Gravity.CENTER);
        gridBox.addView(buildGrid());
        box.addView(gridBox, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(box);
        focusCurrent();

        updateWifi();
        fetchWeather();
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

    /** WiFi 信号图标 */
    private void updateWifi() {
        int iconId = 0;
        int alpha = 0x50;
        try {
            WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
            int rssi = wm.getConnectionInfo().getRssi();
            int level = WifiManager.calculateSignalLevel(rssi, 5);   // 0..4
            if (level > 0) {
                alpha = 0xFF;
                iconId = getResources().getIdentifier(
                        "stat_sys_wifi_signal_" + Math.max(1, level),
                        "drawable", "android");
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

    /** 天气: wttr.in 按出口IP定位, 中文, 一行文本 */
    private void fetchWeather() {
        new WeatherTask(this, mWeather).start();
    }



    /** 方块网格: 2 个信号源 + 4 个应用 + 设置 */
    private GridLayout buildGrid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(COLS);

        addTile(grid, "有线电视", "in:25",
                getResources().getDrawable(R.drawable.icon_tv_source));
        addTile(grid, "Switch", "in:24",
                getResources().getDrawable(R.drawable.icon_hdmi_source));
        addAppTile(grid, "夸克网盘", "com.quark.yun.tv");
        addAppTile(grid, "儿童思维", "com.lutongnet.nldmxXiaomiChildren");
        addAppTile(grid, "拼音乐园", "com.ihuman.pinyin.ott.vip");
        addAppTile(grid, "识字乐园", "com.hongen.app.word.ott.vip");
        addTile(grid, "设置", "pkg:com.xiaomi.mitv.settings",
                getResources().getDrawable(R.drawable.mitv_icn_setting));
        return grid;
    }

    private void addTile(GridLayout grid, String label, String tag, Drawable icon) {
        View t = tile(label, tag, icon);
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = 272; p.height = -2;
        p.setMargins(18, 18, 18, 30);
        grid.addView(t, p);
        mGrid.add(t);
    }

    private void addAppTile(GridLayout grid, String label, String pkg) {
        Drawable d;
        try {
            d = getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            d = new ColorDrawable(0xFF3A3E4C);
        }
        addTile(grid, label, "pkg:" + pkg, d);
    }

    /** 方块卡片: 图标+文字竖排居中 */
    private View tile(String label, String tag, Drawable icon) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(34, 38, 34, 30);
        card.setMinimumWidth(220);
        card.setTag(tag);

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        iv.setLayoutParams(new LinearLayout.LayoutParams(138, 138));
        card.addView(iv);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(20);
        tv.setTextColor(Color.WHITE);
        tv.setPadding(0, 16, 0, 0);
        card.addView(tv);

        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(this);
        applyBg(card, false);
        return card;
    }

    private void applyBg(View v, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(24);
        if (focused) {
            g.setColor(0xF02B3146);
            g.setStroke(5, ACCENT);
        } else {
            g.setColor(0xE61C2130);
            g.setStroke(1, 0x1FFFFFFF);
        }
        v.setBackgroundDrawable(g);
    }

    private void focusCurrent() {
        for (View v : mGrid) applyBg(v, false);
        View v = mGrid.get(mIdx);
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
                if (mIdx % COLS < COLS - 1 && mIdx < mGrid.size() - 1) { mIdx++; focusCurrent(); return true; }
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
        focusCurrent();
        updateWifi();
    }

    public void onClick(View v) {
        String tag = String.valueOf(v.getTag());
        if (tag.startsWith("in:")) {
            switchSource(tag.substring(3));
        } else if (tag.startsWith("pkg:")) {
            launch(tag.substring(4));
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
