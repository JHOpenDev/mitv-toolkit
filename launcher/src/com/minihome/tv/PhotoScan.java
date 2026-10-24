package com.minihome.tv;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 扫描照片: 优先 U 盘各候选挂载点, 退回电视本地 /sdcard/屏保 */
public class PhotoScan {

    private static final String[] USB_ROOTS = {
            "/mnt/usb", "/mnt/usb_storage", "/mnt/usbhost",
            "/storage/udisk", "/mnt/media_rw"
    };
    private static final int MAX_FILES = 200;
    private static final int MAX_DEPTH = 5;

    public static void findPhotos(List<String> out) {
        out.clear();
        for (int i = 0; i < USB_ROOTS.length; i++) {
            File root = new File(USB_ROOTS[i]);
            if (root.isDirectory() && root.canRead()) {
                walk(root, 0, out);
                if (!out.isEmpty()) return;      // U 盘有照片就用它
            }
        }
        File local = new File("/sdcard/屏保");
        if (!local.exists()) local.mkdirs();
        walk(local, 0, out);
    }

    private static void walk(File dir, int depth, List<String> out) {
        if (depth > MAX_DEPTH || out.size() >= MAX_FILES) return;
        File[] items = dir.listFiles();
        if (items == null) return;
        for (int i = 0; i < items.length && out.size() < MAX_FILES; i++) {
            File f = items[i];
            if (f.isDirectory()) {
                walk(f, depth + 1, out);
            } else if (isImage(f.getName())) {
                out.add(f.getAbsolutePath());
            }
        }
    }

    private static boolean isImage(String n) {
        String s = n.toLowerCase();
        return s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".png");
    }

    /** 快速判断有没有可用照片(空闲检测用,超时保护) */
    public static boolean hasPhotos() {
        List<String> tmp = new ArrayList<String>();
        findPhotos(tmp);
        return !tmp.isEmpty();
    }
}
