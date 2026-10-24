package com.minihome.tv;

/** 主桌面空闲检测: 超时且有照片则拉起屏保(顶层类,d8 不支持内部类) */
public class IdleCheck implements Runnable {
    private final MainActivity act;
    public IdleCheck(MainActivity a) { act = a; }
    public void run() { act.checkIdle(); }
}
