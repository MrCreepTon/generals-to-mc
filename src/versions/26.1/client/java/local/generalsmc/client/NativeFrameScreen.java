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
import org.lwjgl.glfw.GLFW;

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
    static int scanCode(int key,int scan) {
        return switch(key) {
            case GLFW.GLFW_KEY_RIGHT -> 0xcd; case GLFW.GLFW_KEY_LEFT -> 0xcb;
            case GLFW.GLFW_KEY_UP -> 0xc8; case GLFW.GLFW_KEY_DOWN -> 0xd0;
            case GLFW.GLFW_KEY_HOME -> 0xc7; case GLFW.GLFW_KEY_END -> 0xcf;
            case GLFW.GLFW_KEY_PAGE_UP -> 0xc9; case GLFW.GLFW_KEY_PAGE_DOWN -> 0xd1;
            case GLFW.GLFW_KEY_INSERT -> 0xd2; case GLFW.GLFW_KEY_DELETE -> 0xd3;
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> 0x9d; case GLFW.GLFW_KEY_RIGHT_ALT -> 0xb8;
            case GLFW.GLFW_KEY_KP_ENTER -> 0x9c; case GLFW.GLFW_KEY_KP_DIVIDE -> 0xb5;
            case GLFW.GLFW_KEY_PRINT_SCREEN -> 0xb7; case GLFW.GLFW_KEY_PAUSE -> 0xc5;
            case GLFW.GLFW_KEY_LEFT_SUPER -> 0xdb; case GLFW.GLFW_KEY_RIGHT_SUPER -> 0xdc; case GLFW.GLFW_KEY_MENU -> 0xdd;
            default -> (scan&0x100)!=0?(scan&0x7f)|0x80:scan;
        };
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(input==null)return super.keyPressed(key,scan,modifiers);
        if(key==GLFW.GLFW_KEY_F10&&closeOnRemove){onClose();return true;}
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
