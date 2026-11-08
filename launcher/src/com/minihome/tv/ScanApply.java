package com.minihome.tv;

import java.util.List;

/** 扫描完成后回主线程重建应用墙 */
public class ScanApply implements Runnable {
    private final MainActivity act;
    private final List<AppInfo> list;

    public ScanApply(MainActivity a, List<AppInfo> l) {
        act = a;
        list = l;
    }

    public void run() {
        act.applyScan(list);
    }
}
