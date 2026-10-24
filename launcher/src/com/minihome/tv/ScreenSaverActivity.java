package com.minihome.tv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 照片屏保: U盘/本地照片交叉淡入淡出+缓推, 右下角小时钟, 任意键退出 */
public class ScreenSaverActivity extends Activity {

    private static final long SLIDE_MS = 12000;
    private static final long FADE_MS = 1400;

    private final List<String> mFiles = new ArrayList<String>();
    private Handler mHandler = new Handler();
    private SsTick mTick;
    private ImageView mFront;
    private ImageView mBack;
    private int mIdx = 0;
    private int mLoading = 0;   // 防重叠加载

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        PhotoScan.findPhotos(mFiles);
        if (mFiles.isEmpty()) {
            finish();
            return;
        }
        Collections.shuffle(mFiles);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        mBack = makeView();
        mFront = makeView();
        mFront.setAlpha(0f);
        root.addView(mBack, new FrameLayout.LayoutParams(-1, -1));
        root.addView(mFront, new FrameLayout.LayoutParams(-1, -1));

        TextClock clock = new TextClock(this, null, 0);
        clock.setFormat12Hour(null);
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(26);
        clock.setTextColor(0xB3FFFFFF);
        clock.setPadding(0, 0, 80, 48);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT | Gravity.BOTTOM);
        root.addView(clock, cp);

        setContentView(root);

        mTick = new SsTick(this);
        showNext();
        mHandler.postDelayed(mTick, SLIDE_MS);
    }

    private ImageView makeView() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return iv;
    }

    /** 由 SsTick 每 12 秒调用 */
    public void advance() {
        showNext();
        mHandler.postDelayed(mTick, SLIDE_MS);
    }

    private void showNext() {
        if (mLoading > 0) return;             // 上一张还在加载
        if (mIdx >= mFiles.size()) mIdx = 0;
        if (mIdx == 0) Collections.shuffle(mFiles);
        final String path = mFiles.get(mIdx);
        mIdx++;
        mLoading++;
        new SsLoader(this, path).start();
    }

    /** 由 SsLoader 在后台线程解码完成后回调(已在主线程) */
    public void applyBitmap(Bitmap bmp) {
        mLoading--;
        if (bmp == null || isFinishing()) {
            if (bmp != null) bmp.recycle();
            return;
        }
        // 交叉换位: 前景变后景, 新图淡入
        ImageView old = mFront;
        mFront = mBack;
        mBack = old;
        mFront.setAlpha(0f);
        mFront.setImageBitmap(bmp);
        mFront.animate().alpha(1f).setDuration(FADE_MS).start();
        mBack.animate().alpha(1f).setDuration(FADE_MS).start();
        // Ken Burns 缓推
        mFront.setScaleX(1f);
        mFront.setScaleY(1f);
        mFront.animate().scaleX(1.08f).scaleY(1.08f)
                .setDuration(SLIDE_MS + FADE_MS).start();
        if (mBack.getDrawable() != null) {
            mBack.setScaleX(1f);
            mBack.setScaleY(1f);
        }
    }

    public Bitmap decode(String path) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 960 && o.outHeight / (sample * 2) >= 540) {
                sample *= 2;
            }
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, o2);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mHandler != null) mHandler.removeCallbacksAndMessages(null);
    }
}
