package com.minihome.tv;

import android.graphics.Bitmap;

/** 后台解码一张照片, 完成后回主线程应用 */
public class SsLoader extends Thread {
    private final ScreenSaverActivity act;
    private final String path;
    public SsLoader(ScreenSaverActivity a, String p) { act = a; path = p; }
    public void run() {
        final Bitmap bmp = act.decode(path);
        act.runOnUiThread(new SsApply(act, bmp));
    }
}
