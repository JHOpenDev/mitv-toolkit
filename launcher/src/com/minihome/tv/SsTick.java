package com.minihome.tv;

/** 屏保轮播节拍(顶层类,d8 不支持内部类) */
public class SsTick implements Runnable {
    private final ScreenSaverActivity act;
    public SsTick(ScreenSaverActivity a) { act = a; }
    public void run() { act.advance(); }
}
