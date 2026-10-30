package com.vialoyed.nodescatch;

public final class AppPalette {
    public final boolean dark;
    public final int background,surface,selected,text,muted,accent,button,success,warning,failure;
    public AppPalette(boolean dark){
        this.dark=dark;
        background=dark?0xFF080B10:0xFFF4F7F9;surface=dark?0xFF151B24:0xFFFFFFFF;
        selected=dark?0xFF253B55:0xFFDFEAFE;text=dark?0xFFE8EEF7:0xFF172B3A;
        muted=dark?0xFFA4B2C5:0xFF586D7C;accent=dark?0xFF73DFD0:0xFF087981;
        button=dark?0xFF20323B:0xFFE1F2F0;
        success=dark?0xFF73E6A6:0xFF116531;warning=dark?0xFFFFD66B:0xFF885900;failure=dark?0xFFFF8E92:0xFFAC2029;
    }
    public int stateColor(int state){switch(state){case TestState.QUEUED:case TestState.RUNNING:return warning;case TestState.SUCCESS:return success;case TestState.FAILED:return failure;default:return muted;}}
    public int stateBackground(int state){int color=stateColor(state);return mix(surface,color,dark?.12f:.08f);}
    private static int mix(int a,int b,float ratio){int r=(int)(((a>>16)&255)*(1-ratio)+((b>>16)&255)*ratio),g=(int)(((a>>8)&255)*(1-ratio)+((b>>8)&255)*ratio),bl=(int)((a&255)*(1-ratio)+(b&255)*ratio);return 0xFF000000|(r<<16)|(g<<8)|bl;}
}
