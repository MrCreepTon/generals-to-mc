package local.generalsmc.client;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.*;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
import java.util.*;
/** WW3D blend/depth policy represented by the renderer backend, without raw OpenGL. */
final class OriginalWorldMaterial {
 private record Key(Identifier texture,int mode){}
 private static final Map<Key,RenderType> cache=new HashMap<>();
 private static final Map<Integer,RenderPipeline> pipelines=new HashMap<>();
 private static final SourceFactor[] SOURCE={SourceFactor.ZERO,SourceFactor.ONE,SourceFactor.SRC_ALPHA,SourceFactor.ONE_MINUS_SRC_ALPHA};
 private static final DestFactor[] DEST={DestFactor.ZERO,DestFactor.ONE,DestFactor.SRC_COLOR,DestFactor.ONE_MINUS_SRC_COLOR,DestFactor.SRC_ALPHA,DestFactor.ONE_MINUS_SRC_ALPHA};
 static RenderType get(Identifier texture,int mode){return cache.computeIfAbsent(new Key(texture,mode),OriginalWorldMaterial::create);}
 static void clear(){cache.clear();pipelines.clear();}
 private static RenderType create(Key key){
  var setup=RenderSetup.builder(pipelines.computeIfAbsent(key.mode,mode->pipeline(key.texture,mode))).withTexture("Sampler0",key.texture).useLightmap().useOverlay().sortOnUpload().bufferSize(1536).createRenderSetup();
  return local.generalsmc.client.mixin.OriginalRenderTypeAccessor.create("generals_material_"+key.mode,setup);
 }
 private static RenderPipeline pipeline(Identifier texture,int mode){
  int src=mode&15,dst=(mode>>4)&15;if(src>=SOURCE.length||dst>=DEST.length)throw new IllegalArgumentException("Invalid original blend");
  RenderPipeline base=((mode&0x800)!=0?((mode&0x100)!=0?RenderTypes.entityCutout(texture):RenderTypes.entityTranslucent(texture)):RenderTypes.entityTranslucentEmissive(texture)).pipeline();
  var pipeline=RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("generalsmc","world/"+Integer.toHexString(mode)))
   .withVertexShader(base.getVertexShader()).withFragmentShader(base.getFragmentShader()).withVertexFormat(base.getVertexFormat(),base.getVertexFormatMode()).withCull(false)
   .withColorTargetState(new ColorTargetState(java.util.Optional.of(new BlendFunction(SOURCE[src],DEST[dst])),(mode&0x20000)==0||(mode&0x400)!=0?ColorTargetState.WRITE_ALL:ColorTargetState.WRITE_NONE));
  base.getSamplers().forEach(pipeline::withSampler);base.getUniforms().forEach(u->{if(u.textureFormat()==null)pipeline.withUniform(u.name(),u.type());else pipeline.withUniform(u.name(),u.type(),u.textureFormat());});
  base.getShaderDefines().flags().forEach(pipeline::withShaderDefine);base.getShaderDefines().values().forEach((name,value)->pipeline.withShaderDefine(name,Float.parseFloat(value)));
  int depth=(mode>>12)&7;CompareOp[] ops={CompareOp.NEVER_PASS,CompareOp.LESS_THAN,CompareOp.EQUAL,CompareOp.LESS_THAN_OR_EQUAL,CompareOp.GREATER_THAN,CompareOp.NOT_EQUAL,CompareOp.GREATER_THAN_OR_EQUAL,CompareOp.ALWAYS_PASS};
  pipeline.withDepthStencilState(new DepthStencilState(ops[base.getDepthStencilState().depthTest()==CompareOp.GREATER_THAN_OR_EQUAL?switch(depth){case 1->4;case 3->6;case 4->1;case 6->3;default->depth;}:depth],(mode&0x200)!=0));
  return pipeline.build();
 }
}
