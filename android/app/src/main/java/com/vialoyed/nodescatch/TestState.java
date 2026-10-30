package com.vialoyed.nodescatch;

public final class TestState {
    public static final int IDLE=0,QUEUED=1,RUNNING=2,SUCCESS=3,FAILED=4,CANCELLED=5;
    private TestState() {}
    public static int restored(int state){return state==QUEUED||state==RUNNING?CANCELLED:state;}
    public static String label(int state){switch(state){case QUEUED:return "等待";case RUNNING:return "测试中";case FAILED:return "失败";case CANCELLED:return "已停止";default:return "—";}}
}
