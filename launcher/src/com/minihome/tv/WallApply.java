package com.minihome.tv;

import android.graphics.Bitmap;

/** 壁纸解码完成后回主线程淡入上屏 */
public class WallApply implements Runnable {
    private final MainActivity act;
    private final Bitmap bmp;

    public WallApply(MainActivity a, Bitmap b) {
        act = a;
        bmp = b;
    }

    public void run() {
        act.applyWall(bmp);
    }
}
