package com.minihome.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** 本地文件夹浏览器: 列出文件/子文件夹, 点视频/图片直接播 */
public class FileListActivity extends Activity
        implements View.OnClickListener, View.OnFocusChangeListener {

    private static final int ACCENT = 0xFFFF9130;
    private File dir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);

        String path = getIntent().getStringExtra("path");
        dir = (path == null) ? new File(Environment.getExternalStorageDirectory(), "家庭文件") : new File(path);
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            Toast.makeText(this, "无法打开: " + dir.getName(), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(buildUi());
    }

    private ScrollView buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF17203A, 0xFF0A0C14});
        root.setBackgroundDrawable(bg);
        root.setPadding(96, 48, 96, 48);

        TextView title = new TextView(this);
        title.setText("家庭文件".equals(dir.getName()) || "".equals(dir.getName())
                ? "家庭文件" : dir.getName());
        title.setTextSize(26);
        title.setTextColor(ACCENT);
        title.setPadding(0, 0, 0, 24);
        root.addView(title);

        File[] items = dir.listFiles();
        if (items == null || items.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("空文件夹(把内容放进来: " + dir.getAbsolutePath() + ")");
            empty.setTextSize(16);
            empty.setTextColor(0xFF8890A4);
            empty.setPadding(0, 32, 0, 0);
            root.addView(empty);
        } else {
            // 手写插入排序(d8编不了泛型Comparator): 文件夹在前,名称忽略大小写
            for (int i = 1; i < items.length; i++) {
                File k = items[i];
                int j = i - 1;
                while (j >= 0 && less(k, items[j])) { items[j + 1] = items[j]; j--; }
                items[j + 1] = k;
            }
            for (int i = 0; i < items.length; i++) root.addView(row(items[i]));
        }

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.addView(root);
        return sc;
    }

    private View row(File f) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(36, 26, 36, 26);
        l.setTag(f);
        l.setOnClickListener(this);
        l.setOnFocusChangeListener(this);

        TextView name = new TextView(this);
        name.setText(f.isDirectory() ? "[文件夹] " + f.getName() : fileGlyph(f.getName()) + " " + f.getName());
        name.setTextSize(21);
        name.setTextColor(f.isDirectory() ? ACCENT : Color.WHITE);
        l.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        if (!f.isDirectory()) {
            TextView size = new TextView(this);
            size.setText(sizeText(f.length()));
            size.setTextSize(15);
            size.setTextColor(0xFF8890A4);
            l.addView(size);
        }

        applyBg(l, false);
        return l;
    }

    public void onFocusChange(View v, boolean hasFocus) {
        applyBg(v, hasFocus);
    }

    private void applyBg(View v, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(18);
        if (focused) {
            g.setColor(0xF02B3146);
            g.setStroke(4, ACCENT);
        } else {
            g.setColor(0xE61C2130);
        }
        v.setBackgroundDrawable(g);
    }

    public void onClick(View v) {
        File f = (File) v.getTag();
        if (f.isDirectory()) {
            Intent it = new Intent(this, FileListActivity.class);
            it.putExtra("path", f.getAbsolutePath());
            startActivity(it);
            return;
        }
        try {
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(Uri.fromFile(f), mimeOf(f.getName()));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
        } catch (Exception e) {
            Toast.makeText(this, "没有应用能打开: " + f.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean less(File a, File b) {
        if (a.isDirectory() != b.isDirectory()) return a.isDirectory();
        return a.getName().compareToIgnoreCase(b.getName()) < 0;
    }

    private static String fileGlyph(String n) {
        String m = mimeOf(n);
        if (m.startsWith("video")) return "▶";
        if (m.startsWith("image")) return "🖼";
        if (m.startsWith("audio")) return "♪";
        return "▪";
    }

    private static String mimeOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi")
                || n.endsWith(".mov") || n.endsWith(".ts") || n.endsWith(".m2ts")) return "video/*";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".bmp") || n.endsWith(".gif")) return "image/*";
        if (n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".m4a")
                || n.endsWith(".flac") || n.endsWith(".aac")) return "audio/*";
        return "*/*";
    }

    private static String sizeText(long b) {
        if (b > 1073741824L) return String.format("%.1f GB", b / 1073741824.0);
        if (b > 1048576L) return String.format("%.1f MB", b / 1048576.0);
        if (b > 1024L) return (b / 1024) + " KB";
        return b + " B";
    }
}
