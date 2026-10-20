package local.generalsmc.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.NativeSession;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import java.nio.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

/** Original partition shroud, projected through the actual Minecraft depth buffer. */
public final class NativeFog implements AutoCloseable {
    private record Grid(int frame,int size,float x,float z,float span,byte[] cells,byte[] opacity) {}
    private static final ShaderProgram PROGRAM=new ShaderProgram(ResourceLocation.fromNamespaceAndPath("generalsmc","core/native_fog"),DefaultVertexFormat.POSITION,ShaderDefines.EMPTY);
    private final NativeWorldView view;
    private final ResourceLocation name;
    private Grid grid;
    private OriginalTexture texture;
    private CompletableFuture<Grid> pending;
    private long nextRead;
    private Boolean sentEnabled;
    public long renderedFrames;
    public boolean shaderReady;
    private boolean enabled=Boolean.parseBoolean(System.getProperty("generalsmc.nativeFog",Boolean.getBoolean("generalsmc.nativeManualBench")?"false":"true"));
    NativeFog(NativeWorldView view){this.view=view;name=ResourceLocation.fromNamespaceAndPath("generalsmc","shroud/"+view.session.directory.getFileName());}
    public boolean enabled(){return enabled;}
    int frame(){return grid==null?-1:grid.frame;}
    public void enabled(boolean value){enabled=value;}
    void tick(){
        if(sentEnabled==null||sentEnabled!=enabled){try{view.session.send("HOST_FOG "+(enabled?1:0));sentEnabled=enabled;}catch(java.io.IOException exception){GeneralsMod.LOG.warn("Original shroud mode unavailable",exception);}}
        if(pending!=null&&pending.isDone()){
            try{Grid value=pending.join();if(value!=null&&(grid==null||value.frame!=grid.frame)){
                var image=new NativeImage(value.size,value.size,false);
                for(int y=0;y<value.size;y++)for(int x=0;x<value.size;x++){int status=value.cells[y*value.size+x]&255,v=status==0?0:status==160?92:148;image.setPixel(x,y,0xff000000|(v<<16)|(v<<8)|v);}
                if(texture==null){texture=new OriginalTexture(image,false);Minecraft.getInstance().getTextureManager().register(name,texture);}else{texture.setPixels(image);texture.upload();}
                grid=value;
            }}catch(Exception exception){GeneralsMod.LOG.warn("Original shroud snapshot unavailable",exception);}pending=null;
        }
        if(pending==null&&System.currentTimeMillis()>=nextRead){nextRead=System.currentTimeMillis()+120;
            pending=CompletableFuture.supplyAsync(()->{
                try{var bytes=Files.readAllBytes(view.session.directory.resolve("fog.bin"));var data=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                    if(data.remaining()<24||data.getInt()!=0x464d485a)throw new java.io.IOException("Invalid original shroud header");
                    int frame=data.getInt(),size=data.getInt();float x=data.getFloat(),z=data.getFloat(),span=data.getFloat();
                    if(size!=128||data.remaining()!=size*size+3||!Float.isFinite(x)||!Float.isFinite(z)||!Float.isFinite(span)||span<=0)throw new java.io.IOException("Invalid original shroud extent");
                    byte[] opacity=new byte[3];data.get(opacity);byte[] cells=new byte[size*size];data.get(cells);return new Grid(frame,size,x,z,span,cells,opacity);
                }catch(NoSuchFileException|AccessDeniedException transientReplace){return null;}catch(java.io.IOException exception){throw new java.io.UncheckedIOException(exception);}
            });
        }
    }
    public int at(double worldX,double worldZ){
        if(!enabled)return 0;if(grid==null)return 255;
        double nativeX=(worldX-view.origin.x())*local.generalsmc.nativebridge.WorldScale.UNITS_PER_BLOCK,nativeZ=(worldZ-view.origin.z())*local.generalsmc.nativebridge.WorldScale.UNITS_PER_BLOCK;
        int x=(int)Math.floor((nativeX-grid.x)*grid.size/grid.span),z=(int)Math.floor((nativeZ-grid.z)*grid.size/grid.span);
        return x<0||z<0||x>=grid.size||z>=grid.size?255:grid.cells[z*grid.size+x]&255;
    }
    public static boolean visible(double x,double z){var v=NativeWorldView.current;return !NativeWorldView.controlling()||v==null||v.fog==null||v.fog.at(x,z)<80;}
    public static boolean resource(NativeSession.Actor actor){var v=NativeWorldView.current;return v!=null&&v.state!=null&&v.state.resources()!=null&&(v.state.resources().stream().anyMatch(r->r.id()==actor.id())||v.state.sites()!=null&&v.state.sites().stream().anyMatch(r->r.id()==actor.id()));}
    public static void render(WorldRenderContext context){
        var view=NativeWorldView.current;if(view==null||!NativeWorldView.controlling()||!view.fog.enabled||view.fog.texture==null)return;
        var fog=view.fog;var mc=Minecraft.getInstance();var target=mc.getMainRenderTarget();if(target.getDepthTextureId()<0)return;
        int previousDepth=RenderSystem.getShaderTexture(0),previousFog=RenderSystem.getShaderTexture(1);var previous=RenderSystem.getShader();var shader=RenderSystem.setShader(PROGRAM);if(shader==null)return;
        var eye=context.camera().getPosition();var inverse=new Matrix4f(context.projectionMatrix()).mul(context.positionMatrix()).invert();
        shader.getUniform("InverseViewProjection").set(inverse);shader.getUniform("CameraPosition").set((float)eye.x,(float)eye.y,(float)eye.z);
        shader.getUniform("FogBounds").set(view.origin.x()+fog.grid.x/(float)local.generalsmc.nativebridge.WorldScale.UNITS_PER_BLOCK,view.origin.z()+fog.grid.z/(float)local.generalsmc.nativebridge.WorldScale.UNITS_PER_BLOCK,fog.grid.span/(float)local.generalsmc.nativebridge.WorldScale.UNITS_PER_BLOCK,0);
        RenderSystem.setShaderTexture(0,target.getDepthTextureId());RenderSystem.setShaderTexture(1,fog.texture.getId());shader.bindSampler("Sampler0",target.getDepthTextureId());shader.bindSampler("Sampler1",fog.texture.getId());
        // Sampling an attached depth texture is undefined: detach it while the overlay uses no depth writes.
        target.bindWrite(false);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_TEXTURE_2D,0,0);
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();
        try{var buffer=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);
            buffer.addVertex(-1,-1,0);buffer.addVertex(1,-1,0);buffer.addVertex(1,1,0);buffer.addVertex(-1,1,0);BufferUploader.drawWithShader(buffer.buildOrThrow());fog.renderedFrames++;fog.shaderReady=true;
        }finally{GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_TEXTURE_2D,target.getDepthTextureId(),0);RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.disableBlend();RenderSystem.enableCull();RenderSystem.setShaderTexture(0,previousDepth);RenderSystem.setShaderTexture(1,previousFog);RenderSystem.setShader(previous);}
    }
    @Override public void close(){if(pending!=null)pending.cancel(false);if(texture!=null)Minecraft.getInstance().getTextureManager().release(name);texture=null;}
}
