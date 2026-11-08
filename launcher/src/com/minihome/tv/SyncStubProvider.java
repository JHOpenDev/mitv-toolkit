package com.minihome.tv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/** 空内容提供器: 周期同步必须挂一个 authority, 永不提供数据 */
public class SyncStubProvider extends ContentProvider {

    public boolean onCreate() {
        return true;
    }

    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        return null;
    }

    public String getType(Uri uri) {
        return null;
    }

    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
