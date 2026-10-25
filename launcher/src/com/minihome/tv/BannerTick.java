package com.minihome.tv;

/** 横幅倒数节拍(顶层类) */
public class BannerTick implements Runnable {
    private final TimerBannerActivity act;
    public BannerTick(TimerBannerActivity a) { act = a; }
    public void run() { act.tick(); }
}
