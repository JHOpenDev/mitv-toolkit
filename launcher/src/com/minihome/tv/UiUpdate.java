package com.minihome.tv;

import android.view.View;
import android.widget.TextView;

/** 回到主线程更新天气文本 */
public class UiUpdate implements Runnable {

    private final TextView tv;
    private final String text;

    public UiUpdate(TextView t, String s) {
        tv = t;
        text = s;
    }

    public void run() {
        if (text.length() > 0) tv.setText(text);
        else tv.setVisibility(View.GONE);
    }
}
