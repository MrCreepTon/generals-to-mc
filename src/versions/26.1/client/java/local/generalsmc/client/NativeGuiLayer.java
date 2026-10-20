package local.generalsmc.client;

import com.mojang.blaze3d.platform.NativeImage;
import local.generalsmc.GeneralsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.nio.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

/** Original GUI rendered into a separate alpha target, with no original terrain pixels. */
final class NativeGuiLayer implements AutoCloseable {
    private record Frame(int[] pixels,int w,int h,int hotX,int hotY,int type,int step,int steps,long stamp) {}
    private final Path file;
    private final Identifier name;
    private DynamicTexture texture;
    private Frame frame;
    private CompletableFuture<Frame> pending;
    private long nextRead,lastStamp=-1;
    private final boolean cursor;
    private boolean closed;
    private ByteBuffer decodeBytes;
    private int[] recycledPixels;
    private long uploads,uploadedAt;
    NativeGuiLayer(Path directory){this(directory,false);}
    NativeGuiLayer(Path directory,boolean cursor){this.cursor=cursor;file=directory.resolve(cursor?"cursor.rgba":"gui.rgba");name=Identifier.fromNamespaceAndPath("generalsmc",(cursor?"original_cursor/":"original_gui/")+directory.getFileName());}
    void update() {
        if(closed)return;
        if(pending!=null&&pending.isDone()) {
            try {
                var value=pending.join();if(value!=null) {
                    var image=texture==null?null:texture.getPixels();boolean first=texture==null;
                    if(image==null||image.getWidth()!=value.w||image.getHeight()!=value.h){image=new NativeImage(value.w,value.h,false);if(!first)texture.setPixels(image);}
                    long address=((local.generalsmc.client.mixin.NativeImagePixelsAccessor)(Object)image).generalsPixels();
                    org.lwjgl.system.MemoryUtil.memIntBuffer(address,value.pixels.length).put(value.pixels);
                    if(first){texture=new OriginalTexture(image,false);Minecraft.getInstance().getTextureManager().register(name,texture);}else texture.upload();
                    recycledPixels=frame==null?null:frame.pixels;
                    frame=value;lastStamp=value.stamp;
                    uploads++;uploadedAt=System.currentTimeMillis();
                }
            }catch(Exception e){GeneralsMod.LOG.error("Original GUI/cursor decode failed",e);}
            pending=null;
        }
        if(pending==null&&System.currentTimeMillis()>=nextRead) {
            nextRead=System.currentTimeMillis()+10;long stamp=lastStamp;
            pending=CompletableFuture.supplyAsync(()->decode(stamp));
        }
    }
    private Frame decode(long previous) {
        try {
            long stamp=Files.getLastModifiedTime(file).toMillis();if(stamp==previous)return null;
            ByteBuffer data;
            try(var channel=java.nio.channels.FileChannel.open(file,StandardOpenOption.READ)){
                long size=channel.size();if(size<8||size>1920L*1080*4+28)throw new IllegalStateException("Invalid original bitmap size");
                if(decodeBytes==null||decodeBytes.capacity()<size)decodeBytes=ByteBuffer.allocateDirect((int)size).order(ByteOrder.LITTLE_ENDIAN);
                data=decodeBytes;data.clear().limit((int)size);while(data.hasRemaining())if(channel.read(data)<=0)return null;data.flip();
            }
            int w=data.getInt(),h=data.getInt(),hotX=0,hotY=0,type=0,step=0,steps=1;
            if(cursor){hotX=data.getInt();hotY=data.getInt();type=data.getInt();step=data.getInt();steps=data.getInt();}
            if(w<1||h<1||w>1920||h>1080||(cursor&&(w>128||h>128))||data.remaining()!=(long)w*h*4)throw new IllegalStateException("Invalid original bitmap");
            int[] pixels=recycledPixels;recycledPixels=null;if(pixels==null||pixels.length!=w*h)pixels=new int[w*h];
            data.asIntBuffer().get(pixels);for(int i=0;i<pixels.length;i++){int color=pixels[i];pixels[i]=(color&0xff00ff00)|((color&255)<<16)|((color>>16)&255);}
            return new Frame(pixels,w,h,hotX,hotY,type,step,steps,stamp);
        }catch(NoSuchFileException|AccessDeniedException transientReplace){return null;}
        catch(Exception e){throw new java.util.concurrent.CompletionException(e);}
    }
    boolean available(){return frame!=null;}
    long uploads(){return uploads;}
    long ageMillis(){return frame==null?Long.MAX_VALUE:System.currentTimeMillis()-frame.stamp;}
    int cursorType(){return frame==null?-1:frame.type;}
    int cursorStep(){return frame==null?-1:frame.step;}
    int cursorSteps(){return frame==null?0:frame.steps;}
    int pixelHash(){return frame==null?0:java.util.Arrays.hashCode(frame.pixels);}
    boolean opaque(int x,int y){return frame!=null&&x>=0&&x<frame.w&&y>=0&&y<frame.h&&(frame.pixels[y*frame.w+x]>>>24)>16;}
    void draw(GuiGraphicsExtractor g,int left,int top,int width,int height){if(frame!=null)g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,name,left,top,0f,0f,width,height,frame.w,frame.h,frame.w,frame.h);}
    void drawCursor(GuiGraphicsExtractor g,int x,int y){if(frame!=null){double scale=1.0/Minecraft.getInstance().getWindow().getGuiScale();draw(g,x-(int)(frame.hotX*scale),y-(int)(frame.hotY*scale),Math.max(1,(int)(frame.w*scale)),Math.max(1,(int)(frame.h*scale)));}}
    @Override public void close(){if(closed)return;closed=true;if(texture!=null)Minecraft.getInstance().getTextureManager().release(name);texture=null;frame=null;pending=null;}
}
