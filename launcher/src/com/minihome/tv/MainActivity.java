package com.minihome.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/** v29 壁纸版: 相册壁纸+渐变压暗 / 右上大时钟·日期·天气 / 左侧全部应用墙(可排序隐藏) / 右下两张操作卡 */
public class MainActivity extends Activity
        implements View.OnClickListener, View.OnFocusChangeListener, View.OnLongClickListener {

    private static final String TVPLAYER_PKG = "com.xiaomi.mitv.tvplayer";
    private static final String SRC_ACTIVITY = "com.xiaomi.mitv.tvplayer.ExternalSourceActivity";
    private static final String LEGACY = "com.mitv.tvhome";
    private static final int ACCENT = 0xFFFF9130;
    private static final int COLS = 4;
    private static final long IDLE_MS = 10 * 60 * 1000;          // 10 分钟进屏保
    private static final long WEATHER_STALE_MS = 30 * 60 * 1000; // 天气最多半小时刷新一次

    private static final int TX = 0xFFF2F4F9;
    private static final int TX2 = 0xFFC8CDD8;
    private static final int TILE_BG = 0x700C101C;
    private static final int TILE_BG_FOC = 0xD1141A2A;
    private static final int CARD_BG = 0xA80C101C;
    private static final int LINE = 0x17FFFFFF;

    private final List<AppInfo> mApps = new ArrayList<AppInfo>();
    private final List<View> mHolders = new ArrayList<View>();
    private final List<String> mHolderTags = new ArrayList<String>();
    private final List<View> mFocusables = new ArrayList<View>();
    private final List<View> mCards = new ArrayList<View>();

    // 手动焦点导航: 原生光束搜索在「网格+右列卡片」布局下会死胡同, 自管位置最可靠
    private int mFocusPos;      // mHolders 里的网格磁贴下标
    private int mCardPos;       // 0=启动 Switch 1=有线电视
    private int mLastTile;      // 从卡片返回网格时落回的磁贴

    private FrameLayout mRoot;
    private ImageView mWall;
    private LinearLayout mGridHost;
    private TextView mWeather;
    private LinearLayout mQuick;
    private TextView[] mQuickBtns;
    private FrameLayout mPanelHost;
    private LinearLayout mPanelRows;
    private Handler mHandler;
    private long mLastTouch;
    private long mLastWeather;
    private String mQuickPkg;
    private boolean mPopupOpen;
    private boolean mPanelOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        mRoot = new FrameLayout(this);
        mRoot.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF17203A, 0xFF0A0C14}));

        mWall = new ImageView(this);
        mWall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mRoot.addView(mWall, new FrameLayout.LayoutParams(-1, -1));

        // 蒙皮三层: 全屏压暗底(花哨照片统一退后) + 顶部渐隐 + 底部渐显
        View scrimBase = new View(this);
        scrimBase.setBackgroundColor(0x59080A12);
        mRoot.addView(scrimBase, new FrameLayout.LayoutParams(-1, -1));
        View scrimTop = new View(this);
        scrimTop.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x6B080A12, 0x00000000, 0x00000000}));
        mRoot.addView(scrimTop, new FrameLayout.LayoutParams(-1, -1));
        View scrimBottom = new View(this);
        scrimBottom.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x33080A12, 0xDB080A12}));
        mRoot.addView(scrimBottom, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(40, 26, 40, 0);
        content.addView(buildTopBar());
        content.addView(buildBody(), new LinearLayout.LayoutParams(-1, 0, 1f));
        content.addView(buildHint());
        mRoot.addView(content, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout.LayoutParams qlp = new FrameLayout.LayoutParams(
                -2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        qlp.bottomMargin = 120;
        mRoot.addView(buildQuickBar(), qlp);

        mPanelHost = buildHiddenPanel();
        mRoot.addView(mPanelHost, new FrameLayout.LayoutParams(-1, -1));

        setContentView(mRoot);

        mHandler = new Handler();
        mLastTouch = SystemClock.elapsedRealtime();
        // 开机提示音不再由桌面播: 同步自愈的桌面在开机1分钟后才弹出, 此刻响一声时位不对;
        // 开机时刻的声音交给系统原生 boot0.mp3(/tvconfig/boot.ini MUSIC_ON=1, logo 阶段响)
        HomeOwner.setup(this);
        setupSyncSelfHeal();
        new AppScan(this, mApps).start();
        setupWallpaper();
        fetchWeather();
        mLastWeather = SystemClock.elapsedRealtime();
    }

    /** 开机自愈: 注册假账号+每60秒周期同步。同步调度由系统框架开机后自行恢复,
     *  不走广播、绕开 MIUI 对第三方开机广播的封锁; SyncStub 仅在开机15分钟窗口内拉起桌面 */
    private void setupSyncSelfHeal() {
        try {
            android.accounts.AccountManager am = android.accounts.AccountManager.get(this);
            android.accounts.Account a = new android.accounts.Account("mini", "com.minihome.tv");
            am.addAccountExplicitly(a, null, null);
            android.content.ContentResolver.setSyncAutomatically(a, "com.minihome.tv.sync", true);
            android.content.ContentResolver.addPeriodicSync(a, "com.minihome.tv.sync",
                    new android.os.Bundle(), 60);
        } catch (Throwable ignored) {
        }
    }

    /** 右上: 大时钟 HH:mm + M月d日 星期X + 天气 */
    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.RIGHT);

        TextClock time = new TextClock(this, null, 0);
        time.setFormat12Hour(null);
        time.setFormat24Hour("HH:mm");
        time.setTextSize(46);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setTextColor(TX);
        time.setLetterSpacing(0.06f);
        wrap.addView(time);

        TextClock date = new TextClock(this, null, 0);
        date.setFormat12Hour(null);
        date.setFormat24Hour("M月d日 EEEE");
        date.setTextSize(17);
        date.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        date.setTextColor(TX2);
        wrap.addView(date);

        mWeather = new TextView(this);
        mWeather.setTextSize(17);
        mWeather.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        mWeather.setTextColor(TX2);
        mWeather.setPadding(0, 4, 0, 0);
        wrap.addView(mWeather);

        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
        bar.addView(wrap, new LinearLayout.LayoutParams(-2, -2));
        return bar;
    }

    /** 主体: 左 1.55 应用墙 / 右 1 两张操作卡 */
    private View buildBody() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setPadding(0, 12, 0, 0);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        TextView cap = new TextView(this);
        cap.setText("全部应用");
        cap.setTextSize(20);
        cap.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        cap.setTextColor(TX);
        cap.setLetterSpacing(0.28f);
        cap.setPadding(10, 0, 0, 16);
        left.addView(cap);

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setFocusable(false);
        sc.setFocusableInTouchMode(false);
        mGridHost = new LinearLayout(this);
        mGridHost.setOrientation(LinearLayout.VERTICAL);
        sc.addView(mGridHost, new ScrollView.LayoutParams(-1, -2));
        left.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
        body.addView(left, new LinearLayout.LayoutParams(0, -1, 1.55f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(44, 0, 6, 10);

        right.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
        right.addView(buildActionCard(R.drawable.ic_switch, "启动 Switch", "HDMI2 · 一键直切", "act:24"),
                new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(-1, -2);
        p2.topMargin = 16;
        right.addView(buildActionCard(R.drawable.ic_cable, "开启有线电视", "一键直切 · 无信号面板", "act:25"), p2);
        body.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        return body;
    }

    private View buildActionCard(int iconRes, String title, String sub, String tag) {
        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(10, 10, 10, 10);
        holder.setTag(tag);
        holder.setFocusable(true);
        holder.setFocusableInTouchMode(true);
        holder.setOnClickListener(this);
        holder.setOnFocusChangeListener(this);
        applyGlow(holder, false);
        mFocusables.add(holder);
        mCards.add(holder);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(28, 18, 22, 18);
        applyCardBg(card, false);
        holder.addView(card, new FrameLayout.LayoutParams(-1, -2));

        ImageView ic = new ImageView(this);
        ic.setImageResource(iconRes);
        ic.setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_ATOP);
        ic.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(ic, new LinearLayout.LayoutParams(60, 60));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(22, 0, 0, 0);

        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextSize(21);
        t1.setSingleLine(true);
        t1.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t1.setTextColor(TX);
        t1.setLetterSpacing(0.14f);
        txt.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText(sub);
        t2.setTextSize(14);
        t2.setSingleLine(true);
        t2.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t2.setTextColor(TX2);
        t2.setPadding(0, 6, 0, 0);
        txt.addView(t2);

        card.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
        return holder;
    }

    private View buildHint() {
        TextView h = new TextView(this);
        h.setText("OK 启动 · 长按 OK 隐藏或排序 · 长按老系统磁贴 管理隐藏应用 · 壁纸取自相册照片");
        h.setTextSize(13);
        h.setTextColor(0x8FC8CDD8);
        h.setLetterSpacing(0.08f);
        h.setGravity(Gravity.CENTER);
        h.setPadding(0, 16, 0, 18);
        return h;
    }

    /** 长按弹出的「上移/下移/隐藏」操作条 */
    private View buildQuickBar() {
        mQuick = new LinearLayout(this);
        mQuick.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(18);
        g.setColor(0xF510141F);
        g.setStroke(2, 0x2EFFFFFF);
        mQuick.setBackgroundDrawable(g);
        mQuick.setPadding(14, 10, 14, 10);
        mQuick.setVisibility(View.GONE);

        String[] names = {"上移", "下移", "隐藏", "管理"};
        mQuickBtns = new TextView[4];
        for (int i = 0; i < 4; i++) {
            TextView b = new TextView(this);
            b.setText(names[i]);
            b.setTextSize(17);
            b.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            b.setTag("qa:" + i);
            b.setFocusable(true);
            b.setFocusableInTouchMode(true);
            b.setOnClickListener(this);
            b.setOnFocusChangeListener(this);
            b.setPadding(24, 10, 24, 10);
            applyQuickBg(b, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i < 2) lp.rightMargin = 10;
            mQuick.addView(b, lp);
            mQuickBtns[i] = b;
        }
        return mQuick;
    }

    /** 隐藏应用面板(蒙层+面板, 菜单键呼出) */
    private FrameLayout buildHiddenPanel() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xCC080A11);
        overlay.setVisibility(View.GONE);
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(20);
        g.setColor(0xFF141A2B);
        g.setStroke(2, LINE);
        box.setBackgroundDrawable(g);
        box.setPadding(32, 26, 32, 22);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        TextView title = new TextView(this);
        title.setText("隐藏的应用");
        title.setTextSize(22);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(TX);
        title.setLetterSpacing(0.2f);
        head.addView(title);
        TextView sub = new TextView(this);
        sub.setText("OK 恢复显示 · 返回键退出");
        sub.setTextSize(14);
        sub.setTextColor(0x99C8CDD8);
        sub.setPadding(20, 0, 0, 2);
        head.addView(sub);
        box.addView(head);

        mPanelRows = new LinearLayout(this);
        mPanelRows.setOrientation(LinearLayout.VERTICAL);
        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setFocusable(false);
        sc.setFocusableInTouchMode(false);
        sc.addView(mPanelRows, new ScrollView.LayoutParams(-1, -2));
        box.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView note = new TextView(this);
        note.setText("清单来自系统已安装应用的自动扫描；隐藏只影响桌面显示，不卸载、不改动系统。");
        note.setTextSize(13);
        note.setTextColor(0x8CC8CDD8);
        note.setPadding(0, 16, 0, 0);
        box.addView(note);

        overlay.addView(box, new FrameLayout.LayoutParams(850, 540, Gravity.CENTER));
        return overlay;
    }

    // ─────────────── 应用墙 ───────────────

    public void applyScan(List<AppInfo> list) {
        mApps.clear();
        mApps.addAll(list);
        buildGrid();
        focusFirstTile();
    }

    private void buildGrid() {
        mGridHost.removeAllViews();
        mHolders.clear();
        mHolderTags.clear();
        mFocusables.clear();

        List<AppInfo> ordered = orderedVisible();
        LinearLayout row = null;
        for (int i = 0; i < ordered.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                mGridHost.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            row.addView(buildTile(ordered.get(i)), tileLp());
        }
        if (row == null || ordered.size() % COLS == 0) {
            row = new LinearLayout(this);
            mGridHost.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        row.addView(buildLegacyTile(), tileLp());

        for (int i = 0; i < mCards.size(); i++) mFocusables.add(mCards.get(i));
        if (mFocusPos >= mHolders.size()) {
            mFocusPos = mHolders.isEmpty() ? 0 : mHolders.size() - 1;
        }
        if (mPopupOpen || mPanelOpen) setFocusEnabled(false);
    }

    private static LinearLayout.LayoutParams tileLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.leftMargin = 6;
        lp.rightMargin = 6;
        lp.bottomMargin = 12;
        return lp;
    }

    /** 用户排序优先, 其余按扫描顺序(已按名称排序), 再过滤隐藏 */
    private List<AppInfo> orderedVisible() {
        List<AppInfo> vis = new ArrayList<AppInfo>();
        List<String> hidden = loadList("hidden");
        for (int i = 0; i < mApps.size(); i++) {
            AppInfo a = mApps.get(i);
            if (!hidden.contains(a.pkg)) vis.add(a);
        }
        List<String> order = loadList("order");
        List<AppInfo> out = new ArrayList<AppInfo>();
        for (int i = 0; i < order.size(); i++) {
            AppInfo f = removeByPkg(vis, order.get(i));
            if (f != null) out.add(f);
        }
        out.addAll(vis);
        return out;
    }

    private List<String> currentOrderPkgs() {
        List<AppInfo> vis = orderedVisible();
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < vis.size(); i++) out.add(vis.get(i).pkg);
        return out;
    }

    private View buildTile(AppInfo a) {
        FrameLayout holder = newFrame(a.pkg, true);
        mHolders.add(holder);
        mHolderTags.add(a.pkg);

        LinearLayout inner = tileInner(false);
        holder.addView(inner, new FrameLayout.LayoutParams(-1, -2));

        ImageView ic = new ImageView(this);
        if (a.icon != null) ic.setImageDrawable(a.icon);
        ic.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        inner.addView(ic, new LinearLayout.LayoutParams(104, 58));

        inner.addView(tileLabel(a.label));
        return holder;
    }

    private View buildLegacyTile() {
        FrameLayout holder = newFrame("legacy:", true);
        mHolders.add(holder);
        mHolderTags.add("legacy:");

        LinearLayout inner = tileInner(true);
        holder.addView(inner, new FrameLayout.LayoutParams(-1, -2));

        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.mitv_icn_apps);
        ic.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        inner.addView(ic, new LinearLayout.LayoutParams(104, 58));

        inner.addView(tileLabel("老系统"));
        return holder;
    }

    private FrameLayout newFrame(String tag, boolean longPress) {
        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(10, 10, 10, 10);
        holder.setTag(tag);
        holder.setFocusable(true);
        holder.setFocusableInTouchMode(true);
        holder.setOnClickListener(this);
        holder.setOnFocusChangeListener(this);
        if (longPress) holder.setOnLongClickListener(this);
        applyGlow(holder, false);
        mFocusables.add(holder);
        return holder;
    }

    private LinearLayout tileInner(boolean dashed) {
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        inner.setPadding(12, 20, 12, 16);
        applyTileBg(inner, false, dashed);
        return inner;
    }

    private TextView tileLabel(String text) {
        TextView lb = new TextView(this);
        lb.setText(text);
        lb.setTextSize(16);
        lb.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        lb.setTextColor(TX);
        lb.setSingleLine(true);
        lb.setEllipsize(TextUtils.TruncateAt.END);
        lb.setGravity(Gravity.CENTER);
        lb.setPadding(0, 10, 0, 0);
        return lb;
    }

    // ─────────────── 焦点外观 ───────────────

    private void applyGlow(View holder, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(28);
        g.setColor(on ? 0x59FF9130 : 0x00000000);
        holder.setBackgroundDrawable(g);
    }

    private void applyTileBg(LinearLayout inner, boolean focused, boolean dashed) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(16);
        if (focused) {
            g.setColor(TILE_BG_FOC);
            g.setStroke(4, ACCENT);
        } else if (dashed) {
            g.setColor(TILE_BG);
            g.setStroke(2, 0x47FFFFFF, 10, 10);
        } else {
            g.setColor(TILE_BG);
            g.setStroke(2, LINE);
        }
        inner.setBackgroundDrawable(g);
    }

    private void applyCardBg(LinearLayout card, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(22);
        if (focused) {
            g.setColor(TILE_BG_FOC);
            g.setStroke(4, ACCENT);
        } else {
            g.setColor(CARD_BG);
            g.setStroke(2, 0x1FFFFFFF);
        }
        card.setBackgroundDrawable(g);
    }

    private void applyQuickBg(TextView b, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(10);
        g.setColor(focused ? ACCENT : 0x00000000);
        b.setBackgroundDrawable(g);
        b.setTextColor(focused ? 0xFF14161C : TX2);
    }

    private void applyRowBg(View row, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(12);
        g.setColor(focused ? 0x2EFF9130 : 0x0DFFFFFF);
        row.setBackgroundDrawable(g);
    }

    /** 缩放作用在内层卡片上: holder 四周有 10px 内边距余量, 放大 1.06 不会越界被父容器裁剪 */
    private void scaleHolder(View v, boolean on) {
        View inner = ((ViewGroup) v).getChildAt(0);
        inner.animate().scaleX(on ? 1.06f : 1f).scaleY(on ? 1.06f : 1f).setDuration(150).start();
    }

    public void onFocusChange(View v, boolean has) {
        String t = tagOf(v);
        if (t.startsWith("qa:")) {
            applyQuickBg((TextView) v, has);
            return;
        }
        if (t.startsWith("hid:")) {
            applyRowBg(v, has);
            return;
        }
        View inner = ((ViewGroup) v).getChildAt(0);
        if (t.startsWith("act:")) applyCardBg((LinearLayout) inner, has);
        else applyTileBg((LinearLayout) inner, has, "legacy:".equals(t));
        scaleHolder(v, has);
        applyGlow(v, has);
    }

    // ─────────────── 点击 / 长按 / 按键 ───────────────

    public void onClick(View v) {
        String t = tagOf(v);
        if (t.startsWith("qa:")) {
            onQuickAction(Integer.parseInt(t.substring(3)));
            return;
        }
        if (t.startsWith("hid:")) {
            restoreApp(t.substring(4));
            return;
        }
        if (t.equals("legacy:")) {
            LegacyLauncher.open(this);
            return;
        }
        if (t.startsWith("act:")) {
            if ("act:24".equals(t)) {
                if (isSchoolNight()) {
                    Intent b = new Intent(this, TimerBannerActivity.class);
                    b.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    b.putExtra("text", "🌙 工作日晚上不玩 Switch 哦");
                    startActivity(b);
                    return;
                }
                switchSource("24");   // 40分钟计时已按用户要求移除(2026-11-08)
            } else {
                switchSource("25");
            }
            return;
        }
        launch(t);
    }

    public boolean onLongClick(View v) {
        String t = tagOf(v);
        if (t.startsWith("qa:") || t.startsWith("hid:") || t.startsWith("act:")) return false;
        if (t.equals("legacy:")) {   // 老系统磁贴固定不可编辑, 长按当隐藏面板入口
            openPanel();
            return true;
        }
        showQuickActions(t);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (mPanelOpen) closePanel();
            else {
                closePopup();
                openPanel();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mPopupOpen) {
                closePopup();
                return true;
            }
            if (mPanelOpen) {
                closePanel();
                return true;
            }
            return true;   // 桌面吞掉返回键: HOME 页 finish 后系统不会自动重开, 会黑屏
        }
        if (!mPopupOpen && !mPanelOpen) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    navFocus(keyCode);
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ─────────────── 长按操作条 ───────────────

    private void showQuickActions(String pkg) {
        mQuickPkg = pkg;
        setFocusEnabled(false);
        mQuick.setVisibility(View.VISIBLE);
        mPopupOpen = true;
        mQuickBtns[0].requestFocus();
    }

    private void closePopup() {
        if (!mPopupOpen) return;
        mQuick.setVisibility(View.GONE);
        mPopupOpen = false;
        if (!mPanelOpen) {
            setFocusEnabled(true);
            focusTag(mQuickPkg);
        }
    }

    private void onQuickAction(int which) {
        if (which == 3) {           // 管理: 打开隐藏面板
            closePopup();
            openPanel();
            return;
        }
        if (which == 2) {
            List<String> hidden = loadList("hidden");
            if (!hidden.contains(mQuickPkg)) {
                hidden.add(mQuickPkg);
                saveList("hidden", hidden);
            }
            int idx = mHolderTags.indexOf(mQuickPkg);
            buildGrid();
            closePopup();
            if (idx >= mHolderTags.size()) idx = mHolderTags.size() - 1;
            if (idx >= 0) {
                mFocusPos = idx;
                mLastTile = idx;
                mHolders.get(idx).requestFocus();
            }
            return;
        }
        List<String> order = currentOrderPkgs();
        int i = order.indexOf(mQuickPkg);
        if (i < 0) return;
        int j = which == 0 ? i - 1 : i + 1;
        if (j < 0 || j >= order.size()) return;
        Collections.swap(order, i, j);
        saveList("order", order);
        buildGrid();
        mQuickBtns[which].requestFocus();
    }

    // ─────────────── 隐藏面板 ───────────────

    private void openPanel() {
        refreshPanelRows();
        mPanelHost.setVisibility(View.VISIBLE);
        mPanelOpen = true;
        setFocusEnabled(false);
        View first = firstHidRow();
        if (first != null) first.requestFocus();
    }

    private void closePanel() {
        mPanelHost.setVisibility(View.GONE);
        mPanelOpen = false;
        setFocusEnabled(true);
        focusFirstTile();
    }

    private void refreshPanelRows() {
        mPanelRows.removeAllViews();
        List<String> hidden = loadList("hidden");
        int added = 0;
        for (int i = 0; i < hidden.size(); i++) {
            AppInfo a = findApp(hidden.get(i));
            if (a == null) continue;
            mPanelRows.addView(hiddenRow(a));
            added++;
        }
        if (added == 0) {
            TextView empty = new TextView(this);
            empty.setText("没有隐藏的应用（在磁贴上长按 OK 可以隐藏）");
            empty.setTextSize(16);
            empty.setTextColor(TX2);
            empty.setPadding(6, 18, 0, 18);
            mPanelRows.addView(empty);
        }
    }

    private View hiddenRow(AppInfo a) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(22, 14, 22, 14);
        row.setTag("hid:" + a.pkg);
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setOnClickListener(this);
        row.setOnFocusChangeListener(this);
        applyRowBg(row, false);

        ImageView ic = new ImageView(this);
        if (a.icon != null) ic.setImageDrawable(a.icon);
        ic.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        row.addView(ic, new LinearLayout.LayoutParams(48, 48));

        TextView name = new TextView(this);
        name.setText(a.label);
        name.setTextSize(17);
        name.setTextColor(TX2);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(18, 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView act = new TextView(this);
        act.setText("↺ 恢复");
        act.setTextSize(16);
        act.setTextColor(ACCENT);
        row.addView(act);
        return row;
    }

    private void restoreApp(String pkg) {
        List<String> hidden = loadList("hidden");
        hidden.remove(pkg);
        saveList("hidden", hidden);
        buildGrid();
        refreshPanelRows();
        View first = firstHidRow();
        if (first != null) first.requestFocus();
    }

    private View firstHidRow() {
        for (int i = 0; i < mPanelRows.getChildCount(); i++) {
            View c = mPanelRows.getChildAt(i);
            if (tagOf(c).startsWith("hid:")) return c;
        }
        return null;
    }

    // ─────────────── 焦点辅助 ───────────────

    private void setFocusEnabled(boolean on) {
        for (int i = 0; i < mFocusables.size(); i++) {
            View v = mFocusables.get(i);
            v.setFocusable(on);
            v.setFocusableInTouchMode(on);
        }
    }

    private void focusTag(String pkg) {
        if (pkg != null) {
            for (int i = 0; i < mHolderTags.size(); i++) {
                if (pkg.equals(mHolderTags.get(i))) {
                    mFocusPos = i;
                    mLastTile = i;
                    mHolders.get(i).requestFocus();
                    return;
                }
            }
        }
        focusFirstTile();
    }

    /** 手动四向导航: 网格内行列移动, 网格右缘进卡片列, 卡片间上下, 左键从卡片回网格 */
    private void navFocus(int key) {
        int n = mHolders.size();
        if (mFocusPos < 0) {                       // 焦点在卡片列
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                mFocusPos = mLastTile < n ? mLastTile : 0;
                focusCurrentTile();
            } else if (key == KeyEvent.KEYCODE_DPAD_DOWN && mCardPos == 0) {
                mCardPos = 1;
                focusCurrentCard();
            } else if (key == KeyEvent.KEYCODE_DPAD_UP && mCardPos == 1) {
                mCardPos = 0;
                focusCurrentCard();
            }
            return;
        }
        if (n == 0) return;
        int col = mFocusPos % COLS;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (col < COLS - 1 && mFocusPos + 1 < n) {
                    mFocusPos++;
                    focusCurrentTile();
                } else {                            // 行尾(含老系统) → 卡片列
                    mLastTile = mFocusPos;
                    mFocusPos = -1;
                    mCardPos = 0;
                    focusCurrentCard();
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (col > 0) {
                    mFocusPos--;
                    focusCurrentTile();
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (mFocusPos + COLS < n) {
                    mFocusPos += COLS;
                    focusCurrentTile();
                } else {                            // 下一行不满时落到行尾
                    int last = n - 1;
                    if (last > mFocusPos && last / COLS == mFocusPos / COLS + 1) {
                        mFocusPos = last;
                        focusCurrentTile();
                    }
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (mFocusPos - COLS >= 0) {
                    mFocusPos -= COLS;
                    focusCurrentTile();
                }
                break;
        }
    }

    private void focusCurrentTile() {
        mLastTile = mFocusPos;
        mHolders.get(mFocusPos).requestFocus();
    }

    private void focusCurrentCard() {
        mCards.get(mCardPos).requestFocus();
    }

    private void focusFirstTile() {
        if (!mHolders.isEmpty()) {
            mFocusPos = 0;
            mLastTile = 0;
            mHolders.get(0).requestFocus();
        }
    }

    private AppInfo findApp(String pkg) {
        for (int i = 0; i < mApps.size(); i++) {
            if (mApps.get(i).pkg.equals(pkg)) return mApps.get(i);
        }
        return null;
    }

    private static AppInfo removeByPkg(List<AppInfo> list, String pkg) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).pkg.equals(pkg)) return list.remove(i);
        }
        return null;
    }

    private static String tagOf(View v) {
        Object t = v.getTag();
        return t == null ? "" : String.valueOf(t);
    }

    // ─────────────── 偏好存取 ───────────────

    private List<String> loadList(String key) {
        List<String> out = new ArrayList<String>();
        String s = getSharedPreferences("home", Context.MODE_PRIVATE).getString(key, "");
        if (s == null || s.length() == 0) return out;
        int start = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == ',') {
                if (i > start) out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        return out;
    }

    private void saveList(String key, List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(list.get(i));
        }
        getSharedPreferences("home", Context.MODE_PRIVATE).edit().putString(key, sb.toString()).apply();
    }

    // ─────────────── 壁纸 ───────────────

    private void setupWallpaper() {
        SharedPreferences sp = getSharedPreferences("home", Context.MODE_PRIVATE);
        int boot = sp.getInt("wp_boot", 0);
        sp.edit().putInt("wp_boot", boot + 1).apply();
        new WallLoader(this, boot).start();
    }

    public void applyWall(Bitmap b) {
        if (b == null || isFinishing()) {
            if (b != null) b.recycle();
            return;
        }
        mWall.setImageBitmap(b);
        mWall.setAlpha(0f);
        mWall.animate().alpha(1f).setDuration(500).start();
    }

    public Bitmap decodeWall(String path) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 1920 && o.outHeight / (sample * 2) >= 1080) {
                sample *= 2;
            }
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            o2.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, o2);
        } catch (Throwable t) {
            return null;
        }
    }

    // ─────────────── 天气 / 屏保 / 启动 ───────────────

    private void fetchWeather() {
        new WeatherTask(this, mWeather).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        stopService(new Intent(this, TimerService.class));   // 回到桌面即结束计时
        HomeOwner.rehide(this);   // 从老系统返回后收回它, 下次开机不再被拉起
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
        if (mWall.getDrawable() == null) {
            setupWallpaper();   // 开机自愈弹出时存储可能未就绪, 壁纸加载失败过就重试
        }
        if (SystemClock.elapsedRealtime() - mLastTouch >= IDLE_MS && PhotoScan.hasPhotos()) {
            startActivity(new Intent(this, ScreenSaverActivity.class));
        }
        mHandler.postDelayed(new IdleCheck(this), 30000);
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

    /** 工作日晚间: 周一至周四 18:00 起, 至次日 06:00(即周二/三/四/五凌晨) */
    private boolean isSchoolNight() {
        if (getIntent().getBooleanExtra("test_school_night", false)) return true;
        Calendar c = Calendar.getInstance();
        int day = c.get(Calendar.DAY_OF_WEEK);      // SUN=1 MON=2 ... SAT=7
        int hour = c.get(Calendar.HOUR_OF_DAY);
        boolean monToThuEvening = (day >= Calendar.MONDAY && day <= Calendar.THURSDAY && hour >= 18);
        boolean nextMorning = (day >= Calendar.TUESDAY && day <= Calendar.FRIDAY && hour < 6);
        return monToThuEvening || nextMorning;
    }
}
