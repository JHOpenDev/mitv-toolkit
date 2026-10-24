package com.minihome.tv;

import android.graphics.Bitmap;

/** 回到主线程把解码好的位图上屏 */
public class SsApply implements Runnable {
    private final ScreenSaverActivity act;
    private final Bitmap bmp;
    public SsApply(ScreenSaverActivity a, Bitmap b) { act = a; bmp = b; }
    public void run() { act.applyBitmap(bmp); }
}
