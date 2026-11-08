package com.minihome.tv;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** 同步适配器挂载服务 */
public class SyncStubService extends Service {

    private static SyncStub mAdapter;

    public IBinder onBind(Intent intent) {
        if (mAdapter == null) mAdapter = new SyncStub(this);
        return mAdapter.getSyncAdapterBinder();
    }
}
