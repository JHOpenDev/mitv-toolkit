package com.minihome.tv;

/** 限时服务节拍(顶层类,d8 不支持内部类) */
public class TimerTick implements Runnable {
    private final TimerService svc;
    public TimerTick(TimerService s) { svc = s; }
    public void run() { svc.tick(); }
}
