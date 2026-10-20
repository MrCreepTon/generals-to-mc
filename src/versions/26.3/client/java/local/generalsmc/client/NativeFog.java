package local.generalsmc.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.NativeSession;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import com.mojang.renderpearl.api.pipeline.*;
import java.nio.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

/** Original partition shroud, projected through the actual Minecraft depth buffer. */
public final class NativeFog implements AutoCloseable {
    private record Grid(int frame,int size,float x,float z,float span,byte[] cells,byte[] opacity) {}
    private static final com.mojang.renderpearl.api.pipeline.RenderPipeline PROGRAM=com.mojang.renderpearl.api.pipeline.RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("generalsmc","fog")).withVertexShader(Identifier.fromNamespaceAndPath("generalsmc","core/native_fog_gpu")).withFragmentShader(Identifier.fromNamespaceAndPath("generalsmc","core/native_fog_gpu"))
        .withVertexBinding(0,DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withBindGroupLayout(BindGroupLayout.builder().withUniform("FogData",UniformType.UNIFORM_BUFFER).withUniform("Sampler0",UniformType.COMBINED_IMAGE_SAMPLER).withUniform("Sampler1",UniformType.COMBINED_IMAGE_SAMPLER).build()).withCull(false).withDepthStencilState(java.util.Optional.empty()).withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
    private com.mojang.renderpearl.api.buffers.GpuBuffer quad;
    private net.minecraft.client.renderer.MappableRingBuffer uniforms;
    private final ByteBuffer uniformBytes=org.lwjgl.system.MemoryUtil.memAlloc(96).order(ByteOrder.nativeOrder());
    private final NativeWorldView view;
    private final Identifier name;
    private Grid grid;
    private OriginalTexture texture;
    private CompletableFuture<Grid> pending;
    private long nextRead;
    private Boolean sentEnabled;
    public long renderedFrames;
    public boolean shaderReady;
    private boolean enabled=Boolean.parseBoolean(System.getProperty("generalsmc.nativeFog",Boolean.getBoolean("generalsmc.nativeManualBench")?"false":"true"));
    NativeFog(NativeWorldView view){this.view=view;name=Identifier.fromNamespaceAndPath("generalsmc","shroud/"+view.session.directory.getFileName());}
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
    public static void render(LevelRenderContext context){
        var view=NativeWorldView.current;if(view==null||!NativeWorldView.controlling()||!view.fog.enabled||view.fog.texture==null)return;
        var fog=view.fog;var target=Minecraft.getInstance().gameRenderer.mainRenderTarget();if(target.getDepthTextureView()==null)return;
        var device=RenderSystem.getDevice();
        if(fog.quad==null){var bytes=org.lwjgl.system.MemoryUtil.memAlloc(72).order(ByteOrder.nativeOrder());try{bytes.asFloatBuffer().put(new float[]{-1,-1,0,1,-1,0,1,1,0,-1,-1,0,1,1,0,-1,1,0});fog.quad=device.createBuffer(()->"Generals fog vertices",com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_VERTEX,bytes);}finally{org.lwjgl.system.MemoryUtil.memFree(bytes);}fog.uniforms=new net.minecraft.client.renderer.MappableRingBuffer(()->"Generals fog uniform",com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_UNIFORM|com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_MAP_WRITE,96);}
        var camera=context.levelState().cameraRenderState;var inverse=new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix).invert();
        fog.uniformBytes.clear();inverse.get(0,fog.uniformBytes);fog.uniformBytes.position(64);fog.uniformBytes.putFloat((float)camera.pos.x).putFloat((float)camera.pos.y).putFloat((float)camera.pos.z).putFloat(0);
        fog.uniformBytes.putFloat(view.origin.x()+fog.grid.x/(float)local.generalsmc.nativebridge.WorldScale.UNITS_PER_BLOCK).putFloat(view.origin.z()+fog.grid.z/(float)local.generalsmc.nativebridge.WorldScale.UNITS_PER_BLOCK).putFloat(fog.grid.span/(float)local.generalsmc.nativebridge.WorldScale.UNITS_PER_BLOCK).putFloat(0);fog.uniformBytes.flip();
        var encoder=device.createCommandEncoder();try(var mapped=fog.uniforms.currentBuffer().map(false,true)){mapped.data().put(fog.uniformBytes);}
        try(var pass=encoder.createRenderPass(()->"Generals fog",target.getColorTextureView(),java.util.Optional.empty())){
            pass.setPipeline(RenderSystem.getCompiledPipeline(PROGRAM));pass.setUniform("FogData",fog.uniforms.currentBuffer());pass.setUniform("Sampler0",target.getDepthTextureView(),RenderSystem.getSamplerCache().getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.NEAREST));pass.setUniform("Sampler1",fog.texture.getTextureView(),RenderSystem.getSamplerCache().getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.LINEAR));pass.setVertexBuffer(0,fog.quad.slice());pass.draw(6,1,0,0);fog.renderedFrames++;fog.shaderReady=true;
        }finally{encoder.submit();fog.uniforms.rotate();}
    }
    @Override public void close(){if(pending!=null)pending.cancel(false);if(quad!=null)quad.close();if(uniforms!=null)uniforms.close();org.lwjgl.system.MemoryUtil.memFree(uniformBytes);if(texture!=null)Minecraft.getInstance().getTextureManager().release(name);texture=null;}
}
