package com.minihome.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.TextView;

/** 限时提醒横幅: 模式1=固定文字闪4秒; 模式2=最后5秒大数字实时倒数 */
public class TimerBannerActivity extends Activity {

    private Handler mHandler = new Handler();
    private BannerTick mTick;
    private TextView mText;
    private long mEndAt;
    private boolean mLive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String text = getIntent().getStringExtra("text");
        int countdown = getIntent().getIntExtra("countdown", 0);
        mLive = countdown > 0;

        mText = new TextView(this);
        mText.setGravity(Gravity.CENTER);
        mText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        mText.setTextColor(Color.WHITE);
        mText.setBackgroundColor(0x88000000);

        if (mLive) {
            mEndAt = SystemClock.elapsedRealtime() + countdown * 1000L;
            mText.setTextSize(160);
            mTick = new BannerTick(this);
            mTick.run();
        } else {
            mText.setTextSize(56);
            mText.setText(text);
            mHandler.postDelayed(new BannerFinish(this), 4000);
        }
        setContentView(mText);
    }

    public void tick() {
        long remain = mEndAt - SystemClock.elapsedRealtime();
        if (remain <= 0) {
            finish();
            return;
        }
        mText.setText(String.valueOf((remain + 999) / 1000));
        mHandler.postDelayed(mTick, 300);
    }

    public void finishNow() {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }
}
