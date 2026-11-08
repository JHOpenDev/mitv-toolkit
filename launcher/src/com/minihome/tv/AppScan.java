package com.minihome.tv;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

/** 扫描已装可启动应用: 去重、排除自己和老桌面, 名称手写插入排序(d8 不支持 Comparator) */
public class AppScan extends Thread {

    private final MainActivity act;
    private final List<AppInfo> sink;

    public AppScan(MainActivity a, List<AppInfo> sink) {
        act = a;
        this.sink = sink;
    }

    @Override
    public void run() {
        List<AppInfo> list = new ArrayList<AppInfo>();
        try {
            PackageManager pm = act.getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
            for (int i = 0; i < ris.size(); i++) {
                ResolveInfo ri = ris.get(i);
                String pkg = ri.activityInfo == null ? null : ri.activityInfo.packageName;
                if (pkg == null || pkg.equals("com.minihome.tv") || pkg.equals("com.mitv.tvhome")) continue;
                if (findByPkg(list, pkg) != null) continue;   // 同包多入口只留一个
                String label;
                Drawable icon = null;
                try {
                    label = String.valueOf(ri.loadLabel(pm));
                    icon = ri.loadIcon(pm);
                } catch (Throwable t) {
                    label = pkg;
                }
                if (label == null || label.length() == 0) label = pkg;
                list.add(new AppInfo(pkg, label, icon));
            }
            for (int i = 1; i < list.size(); i++) {          // 插入排序: 名称不区分大小写
                AppInfo k = list.get(i);
                int j = i - 1;
                while (j >= 0 && k.label.compareToIgnoreCase(list.get(j).label) < 0) {
                    list.set(j + 1, list.get(j));
                    j--;
                }
                list.set(j + 1, k);
            }
        } catch (Throwable ignored) {
        }
        act.runOnUiThread(new ScanApply(act, list));
    }

    private static AppInfo findByPkg(List<AppInfo> list, String pkg) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).pkg.equals(pkg)) return list.get(i);
        }
        return null;
    }
}
