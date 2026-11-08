package com.minihome.tv;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/** 桌面壁纸: 从相册照片池按「天数 + 开机次数」轮换取一张, 后台解码。
 *  U盘照片解码失败(换盘/个别坏图)时回退本地 /sdcard/屏保 池, 保证总有壁纸 */
public class WallLoader extends Thread {

    private static final String TAG = "MiniWall";
    private final MainActivity act;
    private final int boot;

    public WallLoader(MainActivity a, int b) {
        act = a;
        boot = b;
    }

    @Override
    public void run() {
        List<String> files = new ArrayList<String>();
        PhotoScan.findPhotos(files);
        Log.i(TAG, "pool=" + files.size());
        if (files.isEmpty()) return;
        long day = System.currentTimeMillis() / 86400000L;
        int idx = (int) ((day + boot) % files.size());
        Bitmap bmp = act.decodeWall(files.get(idx));
        Log.i(TAG, "pick=" + idx + " " + files.get(idx) + " -> "
                + (bmp == null ? "DECODE_FAIL" : bmp.getWidth() + "x" + bmp.getHeight()));
        if (bmp == null) {   // 回退本地照片池
            List<String> local = new ArrayList<String>();
            PhotoScan.findLocalPhotos(local);
            Log.i(TAG, "local pool=" + local.size());
            if (!local.isEmpty()) {
                int li = (int) ((day + boot) % local.size());
                bmp = act.decodeWall(local.get(li));
                Log.i(TAG, "local pick=" + li + " " + local.get(li) + " -> "
                        + (bmp == null ? "DECODE_FAIL" : "ok"));
            }
        }
        act.runOnUiThread(new WallApply(act, bmp));
    }
}
