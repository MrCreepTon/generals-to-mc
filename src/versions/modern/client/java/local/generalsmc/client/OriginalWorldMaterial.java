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
  var builder=RenderSetup.builder(pipelines.computeIfAbsent(key.mode,mode->pipeline(key.texture,mode))).withTexture("Sampler0",key.texture).sortOnUpload().bufferSize(1536);if((key.mode&0x800)!=0)builder.useLightmap().useOverlay();var setup=builder.createRenderSetup();
  return local.generalsmc.client.mixin.OriginalRenderTypeAccessor.create("generals_material_"+key.mode,setup);
 }
 private static RenderPipeline pipeline(Identifier texture,int mode){
  int src=mode&15,dst=(mode>>4)&15;if(src>=SOURCE.length||dst>=DEST.length)throw new IllegalArgumentException("Invalid original blend");
  RenderPipeline base=((mode&0x800)!=0?((mode&0x100)!=0?RenderTypes.entityCutoutNoCull(texture):RenderTypes.entityTranslucent(texture)):RenderTypes.entityTranslucentEmissive(texture)).pipeline();
  var pipeline=RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("generalsmc","world/"+Integer.toHexString(mode)))
   .withVertexShader(base.getVertexShader()).withFragmentShader(base.getFragmentShader()).withVertexFormat(base.getVertexFormat(),base.getVertexFormatMode()).withCull((mode&0x80000)!=0)
   .withBlend(new BlendFunction(SOURCE[src],DEST[dst])).withDepthWrite((mode&0x200)!=0).withColorWrite((mode&0x20000)==0||(mode&0x400)!=0);
  base.getSamplers().stream().filter(name->(mode&0x800)!=0||name.equals("Sampler0")).forEach(pipeline::withSampler);base.getUniforms().forEach(u->{if(u.textureFormat()==null)pipeline.withUniform(u.name(),u.type());else pipeline.withUniform(u.name(),u.type(),u.textureFormat());});
  if((mode&0x800)==0){pipeline.withVertexShader(Identifier.fromNamespaceAndPath("generalsmc","core/original_effect")).withFragmentShader(Identifier.fromNamespaceAndPath("generalsmc","core/original_effect"));if((mode&0x100)!=0)pipeline.withShaderDefine("GENERALS_ALPHA_TEST");}
  base.getShaderDefines().flags().forEach(pipeline::withShaderDefine);base.getShaderDefines().values().forEach((name,value)->pipeline.withShaderDefine(name,Float.parseFloat(value)));
  int depth=(mode>>12)&7;pipeline.withDepthTestFunction(switch(depth){case 1->DepthTestFunction.LESS_DEPTH_TEST;case 2->DepthTestFunction.EQUAL_DEPTH_TEST;case 4->DepthTestFunction.GREATER_DEPTH_TEST;case 7->DepthTestFunction.NO_DEPTH_TEST;default->DepthTestFunction.LEQUAL_DEPTH_TEST;});
  return pipeline.build();
 }
}
