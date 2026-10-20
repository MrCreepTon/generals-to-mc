package local.generalsmc.client;

import com.mojang.blaze3d.platform.NativeImage;
import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.NativeSession;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.io.IOException;
import java.util.HashSet;
import com.mojang.blaze3d.platform.InputConstants;

/** Diagnostic only: proves original WW3D pixels can enter Minecraft's renderer.
 * This is NOT the world/depth compositor and is not advertised as a completed port.
 */
public class NativeFrameScreen extends CompatScreen {
    protected static final Identifier FRAME = Identifier.fromNamespaceAndPath("generalsmc", "native_transport_probe");
    private final Path file;
    protected DynamicTexture texture;
    public int updates;
    private long lastRead;
    protected NativeSession input;
    private boolean closeOnRemove=true;
    private int buttons, lastX=-1, lastY=-1;
    private long lastMotion;
    private final HashSet<Integer> pressedKeys=new HashSet<>();
    public NativeFrameScreen(Path file) { super(L.component("Original WW3D transport diagnostic")); this.file=file; }
    public NativeFrameScreen(NativeSession input) { this(input.directory.resolve("frame.bmp")); this.input=input; }
    public NativeFrameScreen(NativeSession input, boolean closeOnRemove) {this(input);this.closeOnRemove=closeOnRemove;}
    private int frameWidth() { return Math.max(4,Math.min(width-40,(height-60)*4/3)); }
    /** In-client test input uses the same screen-coordinate route as a human click. */
    void clickOriginal(int x,int y,boolean down) {
        int w=frameWidth();double sx=(width-w)/2+x*w/800.0,sy=30+y*(w*3/4)/600.0;
        mouseMoved(sx,sy);if(down)mouseClicked(sx,sy,0);else mouseReleased(sx,sy,0);
    }
    private boolean sendMouse(int message,double x,double y,int wheel) {
        if(input==null)return false;
        int w=frameWidth(),h=w*3/4,left=(width-w)/2;
        int nx=(int)((x-left)*800/w),ny=(int)((y-30)*600/h);
        if(nx<0||nx>=800||ny<0||ny>=600)return false;
        try {input.mouse(message,nx,ny,buttons,wheel);return true;}
        catch(IOException e){GeneralsMod.LOG.error("Original input transport failed",e);onClose();return false;}
    }
    @Override public void mouseMoved(double x,double y) {
        if(System.currentTimeMillis()-lastMotion<33||(lastX==(int)x&&lastY==(int)y))return;
        lastMotion=System.currentTimeMillis();lastX=(int)x;lastY=(int)y;sendMouse(0x200,x,y,0);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        int mask=button==0?1:button==1?2:button==2?16:0;if(mask==0)return false;
        buttons|=mask;return sendMouse(button==0?0x201:button==1?0x204:0x207,x,y,0);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        int mask=button==0?1:button==1?2:button==2?16:0;if(mask==0)return false;
        buttons&=~mask;return sendMouse(button==0?0x202:button==1?0x205:0x208,x,y,0);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {return sendMouse(0x200,x,y,0);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {return sendMouse(0x20a,x,y,(int)Math.max(-1200,Math.min(1200,vertical*120)));}
    static int scanCode(int key,int keycode){return switch(key){
        case InputConstants.KEY_ESCAPE -> 1;
        case InputConstants.KEY_1 -> 2;
        case InputConstants.KEY_2 -> 3;
        case InputConstants.KEY_3 -> 4;
        case InputConstants.KEY_4 -> 5;
        case InputConstants.KEY_5 -> 6;
        case InputConstants.KEY_6 -> 7;
        case InputConstants.KEY_7 -> 8;
        case InputConstants.KEY_8 -> 9;
        case InputConstants.KEY_9 -> 10;
        case InputConstants.KEY_0 -> 11;
        case InputConstants.KEY_MINUS -> 12;
        case InputConstants.KEY_EQUALS -> 13;
        case InputConstants.KEY_BACKSPACE -> 14;
        case InputConstants.KEY_TAB -> 15;
        case InputConstants.KEY_Q -> 16;
        case InputConstants.KEY_W -> 17;
        case InputConstants.KEY_E -> 18;
        case InputConstants.KEY_R -> 19;
        case InputConstants.KEY_T -> 20;
        case InputConstants.KEY_Y -> 21;
        case InputConstants.KEY_U -> 22;
        case InputConstants.KEY_I -> 23;
        case InputConstants.KEY_O -> 24;
        case InputConstants.KEY_P -> 25;
        case InputConstants.KEY_LBRACKET -> 26;
        case InputConstants.KEY_RBRACKET -> 27;
        case InputConstants.KEY_RETURN -> 28;
        case InputConstants.KEY_LCONTROL -> 29;
        case InputConstants.KEY_A -> 30;
        case InputConstants.KEY_S -> 31;
        case InputConstants.KEY_D -> 32;
        case InputConstants.KEY_F -> 33;
        case InputConstants.KEY_G -> 34;
        case InputConstants.KEY_H -> 35;
        case InputConstants.KEY_J -> 36;
        case InputConstants.KEY_K -> 37;
        case InputConstants.KEY_L -> 38;
        case InputConstants.KEY_SEMICOLON -> 39;
        case InputConstants.KEY_APOSTROPHE -> 40;
        case InputConstants.KEY_GRAVE -> 41;
        case InputConstants.KEY_LSHIFT -> 42;
        case InputConstants.KEY_BACKSLASH -> 43;
        case InputConstants.KEY_Z -> 44;
        case InputConstants.KEY_X -> 45;
        case InputConstants.KEY_C -> 46;
        case InputConstants.KEY_V -> 47;
        case InputConstants.KEY_B -> 48;
        case InputConstants.KEY_N -> 49;
        case InputConstants.KEY_M -> 50;
        case InputConstants.KEY_COMMA -> 51;
        case InputConstants.KEY_PERIOD -> 52;
        case InputConstants.KEY_SLASH -> 53;
        case InputConstants.KEY_RSHIFT -> 54;
        case InputConstants.KEY_MULTIPLY -> 55;
        case InputConstants.KEY_LALT -> 56;
        case InputConstants.KEY_SPACE -> 57;
        case InputConstants.KEY_CAPSLOCK -> 58;
        case InputConstants.KEY_NUMLOCK -> 69;
        case InputConstants.KEY_SCROLLLOCK -> 70;
        case InputConstants.KEY_NUMPAD7 -> 71;
        case InputConstants.KEY_NUMPAD8 -> 72;
        case InputConstants.KEY_NUMPAD9 -> 73;
        case org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_KP_MINUS -> 74;
        case InputConstants.KEY_NUMPAD4 -> 75;
        case InputConstants.KEY_NUMPAD5 -> 76;
        case InputConstants.KEY_NUMPAD6 -> 77;
        case InputConstants.KEY_ADD -> 78;
        case InputConstants.KEY_NUMPAD1 -> 79;
        case InputConstants.KEY_NUMPAD2 -> 80;
        case InputConstants.KEY_NUMPAD3 -> 81;
        case InputConstants.KEY_NUMPAD0 -> 82;
        case org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_KP_PERIOD -> 83;
        case InputConstants.KEY_NUMPADENTER -> 156;
        case InputConstants.KEY_RCONTROL -> 157;
        case InputConstants.KEY_RALT -> 184;
        case InputConstants.KEY_HOME -> 199;
        case InputConstants.KEY_UP -> 200;
        case InputConstants.KEY_PAGEUP -> 201;
        case InputConstants.KEY_LEFT -> 203;
        case InputConstants.KEY_RIGHT -> 205;
        case InputConstants.KEY_END -> 207;
        case InputConstants.KEY_DOWN -> 208;
        case InputConstants.KEY_PAGEDOWN -> 209;
        case InputConstants.KEY_INSERT -> 210;
        case InputConstants.KEY_DELETE -> 211;
        case InputConstants.KEY_LGUI -> 219;
        case InputConstants.KEY_RGUI -> 220;
        case InputConstants.KEY_PRINTSCREEN -> 183;
        case InputConstants.KEY_PAUSE -> 197;
        case InputConstants.KEY_F1 -> 59;
        case InputConstants.KEY_F2 -> 60;
        case InputConstants.KEY_F3 -> 61;
        case InputConstants.KEY_F4 -> 62;
        case InputConstants.KEY_F5 -> 63;
        case InputConstants.KEY_F6 -> 64;
        case InputConstants.KEY_F7 -> 65;
        case InputConstants.KEY_F8 -> 66;
        case InputConstants.KEY_F9 -> 67;
        case InputConstants.KEY_F10 -> 68;
        case InputConstants.KEY_F11 -> 87;
        case InputConstants.KEY_F12 -> 88;
        case org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_KP_DIVIDE -> 181;
        case org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_APPLICATION -> 221;
        default -> 0;
    };}
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(input==null)return super.keyPressed(key,scan,modifiers);
        if(key==InputConstants.KEY_F10&&closeOnRemove){onClose();return true;}
        int code=RtsBindings.scan(key,scan);
        scanInput(code,true);
        return true;
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers) {
        int code=RtsBindings.scan(key,scan);
        scanInput(code,false);
        return true;
    }
    protected void scanInput(int code,boolean down){if(input!=null&&code>0&&code<256&&(down?pressedKeys.add(code):pressedKeys.remove(code)))try{input.key(code,down);}catch(IOException error){onClose();}}
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float f) {}
    protected void updateTexture() {
        if (System.currentTimeMillis()-lastRead>250) {
            lastRead=System.currentTimeMillis();
            try {
                var bitmap=ImageIO.read(file.toFile());
                if (bitmap!=null) {
                    var image=new NativeImage(bitmap.getWidth(),bitmap.getHeight(),false);
                    for(int py=0;py<bitmap.getHeight();py++)for(int px=0;px<bitmap.getWidth();px++) image.setPixel(px,py,bitmap.getRGB(px,py)|0xff000000);
                    if(texture==null){texture=new DynamicTexture(()->"Generals frame",image);minecraft.getTextureManager().register(FRAME,texture);}
                    else {texture.setPixels(image);texture.upload();}
                    updates++;
                }
            }catch(Exception e){if(updates==0) GeneralsMod.LOG.debug("Waiting for original framebuffer: {}",e.toString());}
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float f) {
        updateTexture();
        int w=frameWidth(),h=w*3/4;
        if(texture!=null) g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,FRAME,(width-w)/2,30,0f,0f,w,h,800,600,800,600);
        g.centeredText(font,input==null?"Original WW3D frame — transport diagnostic; world fusion pending":"Zero Hour input/renderer lab — F10 closes; world fusion pending",width/2,10,0xffffffff);
        super.extractRenderState(g,x,y,f);
    }
    @Override public void removed() {
        if(texture!=null){minecraft.getTextureManager().release(FRAME);texture=null;}
        releaseInput();if(input!=null&&closeOnRemove)input.close();
    }
    protected void releaseInput(){if(input!=null)for(int code:pressedKeys)try{input.key(code,false);}catch(IOException ignored){}pressedKeys.clear();}
}
