package com.minihome.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** 拦截老桌面的升级提示: 立即结束, 不显示页面 */
public class UpgradeBlockActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent home = new Intent(this, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(home);
        finish();
    }
}
