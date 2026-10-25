package com.minihome.tv;

/** 固定横幅 4 秒后自行消失(顶层类) */
public class BannerFinish implements Runnable {
    private final TimerBannerActivity act;
    public BannerFinish(TimerBannerActivity a) { act = a; }
    public void run() { act.finishNow(); }
}
