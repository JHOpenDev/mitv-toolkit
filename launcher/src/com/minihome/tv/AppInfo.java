package com.minihome.tv;

import android.graphics.drawable.Drawable;

/** 应用墙条目: 包名/显示名/图标 */
public class AppInfo {
    public final String pkg;
    public final String label;
    public final Drawable icon;

    public AppInfo(String pkg, String label, Drawable icon) {
        this.pkg = pkg;
        this.label = label;
        this.icon = icon;
    }
}
