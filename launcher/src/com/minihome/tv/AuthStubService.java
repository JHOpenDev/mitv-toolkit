package com.minihome.tv;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** 认证器挂载服务: 系统通过它找到 AuthStub */
public class AuthStubService extends Service {

    private AuthStub mAuth;

    public IBinder onBind(Intent intent) {
        if (mAuth == null) mAuth = new AuthStub(this);
        return mAuth.getIBinder();
    }
}
