package local.generalsmc.client;

import net.minecraft.client.gui.GuiGraphics;

/** Shared, bounded GUI drawing. No extra render target, textures or per-frame geometry allocation. */
final class UiPaint {
    static final int PANEL=0xf5101822,SURFACE=0xff1b2835,BORDER=0xff354453,TEXT=0xffeaf0f5,
        MUTED=0xffa6b4c2,ACCENT=0xffe8bd76,FOCUS=0xffffd892,BLUE=0xff79bed2;
    static int mix(int a,int b,float t){t=Math.clamp(t,0,1);int out=0;for(int shift=0;shift<32;shift+=8)out|=(int)(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t)<<shift;return out;}
    static void rounded(GuiGraphics g,int x,int y,int w,int h,int color){
        if(w<=0||h<=0)return;
        if(w<6||h<6){g.fill(x,y,x+w,y+h,color);return;}
        g.fill(x+2,y,x+w-2,y+1,color);g.fill(x+1,y+1,x+w-1,y+2,color);
        g.fill(x,y+2,x+w,y+h-2,color);
        g.fill(x+1,y+h-2,x+w-1,y+h-1,color);g.fill(x+2,y+h-1,x+w-2,y+h,color);
    }
    static void panel(GuiGraphics g,int x,int y,int w,int h){
        rounded(g,x,y+3,w,h,0x42000000);rounded(g,x,y,w,h,0xff35414e);
        rounded(g,x+1,y+1,w-2,h-2,PANEL);
        g.fill(x+15,y+1,x+43,y+2,ACCENT);
    }
    static int button(GuiGraphics g,int x,int y,int w,int h,MenuButton.Kind kind,boolean enabled,boolean selected,boolean focus,float hover,boolean pressed){
        boolean live=enabled||kind==MenuButton.Kind.TAB&&selected;
        int fill=SURFACE,edge=BORDER,ink=TEXT;
        if(kind==MenuButton.Kind.PRIMARY){fill=mix(0xffd6a85f,0xfff0cf94,hover);edge=0xfff4d59e;ink=0xff131d28;}
        else if(kind==MenuButton.Kind.DANGER){fill=mix(0xff2c2028,0xff4b2c35,hover);edge=mix(0xff70404a,0xffc5737b,hover);ink=0xffffb4b1;}
        else if(selected){fill=0xff243e4d;edge=0xff76b9cc;ink=0xffe1f3fa;}
        else {fill=mix(SURFACE,0xff2b4051,hover);edge=mix(BORDER,0xff6d8b9e,hover);}
        if(!live){fill=0xff17212c;edge=0xff2b3642;ink=0xff8998a8;}
        if(pressed&&enabled)fill=mix(fill,0xff0f1822,.2f);
        if(kind==MenuButton.Kind.QUIET&&!selected&&!focus){
            if(hover>.01f)rounded(g,x,y,w,h,mix(0x001b2835,0xff263846,hover));
        }else{
            if(kind!=MenuButton.Kind.FIELD&&kind!=MenuButton.Kind.TAB)rounded(g,x,y+1,w,h,0x22000000);
            rounded(g,x,y,w,h,focus&&enabled?FOCUS:edge);int inset=focus&&enabled?2:1;
            rounded(g,x+inset,y+inset,w-2*inset,h-2*inset,fill);
        }
        if(selected&&kind==MenuButton.Kind.TAB)g.fill(x+8,y+h-2,x+w-8,y+h,ACCENT);
        return ink;
    }
    static void icon(GuiGraphics g,int x,int y,int glyph,int color){
        if(glyph==1){g.fill(x+2,y,x+7,y+1,color);g.fill(x+1,y+1,x+2,y+3,color);g.fill(x,y+3,x+1,y+6,color);g.fill(x+1,y+6,x+3,y+7,color);g.fill(x+3,y+7,x+7,y+8,color);g.fill(x+7,y+4,x+8,y+7,color);g.fill(x+6,y+1,x+8,y+3,color);g.fill(x+5,y+3,x+8,y+4,color);}
        else if(glyph==2){for(int i=0;i<6;i++)g.fill(x+i+1,y+6-i,x+i+3,y+8-i,color);g.fill(x,y+7,x+2,y+9,color);}
        else if(glyph==3){for(int i=0;i<7;i++){g.fill(x+i,y+i,x+i+2,y+i+1,color);g.fill(x+6-i,y+i,x+8-i,y+i+1,color);}}
        else if(glyph==4||glyph==5){for(int i=0;i<4;i++){int a=glyph==4?i:6-i;g.fill(x+a,y+3-i,x+a+2,y+5+i,color);}}
        else if(glyph==6){g.fill(x+3,y,x+5,y+8,color);g.fill(x,y+3,x+8,y+5,color);}
    }
    static void check(GuiGraphics g,int x,int y,int color){g.fill(x,y+3,x+2,y+5,color);g.fill(x+2,y+5,x+4,y+7,color);g.fill(x+4,y+3,x+6,y+5,color);g.fill(x+6,y+1,x+8,y+3,color);}
    static void arrow(GuiGraphics g,int x,int y,int color){g.fill(x-2,y-1,x+3,y,color);g.fill(x-1,y,x+2,y+1,color);g.fill(x,y+1,x+1,y+2,color);}
    static void toggle(GuiGraphics g,int x,int y,boolean on,boolean enabled){
        rounded(g,x,y,23,11,enabled?(on?0xff579caf:0xff4c5b68):0xff303c49);
        rounded(g,x+(on?13:2),y+2,8,7,enabled?0xfff0f5f8:0xff91a0ad);
    }
}
