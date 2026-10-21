package local.generalsmc.client;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.blaze3d.platform.*;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
import java.util.*;
/** WW3D blend/depth policy represented by the renderer backend, without raw OpenGL. */
final class OriginalWorldMaterial {
 // Swapping native Y/Z reflects triangle winding. Modern entity pipelines light front and back
 // faces separately, so retain the outward face after that conversion.
 static final int[] CORNERS={0,2,1,1};
 private record Key(Identifier texture,int mode){}
 private static final Map<Key,RenderType> cache=new HashMap<>();
 private static final BlendFactor[] SOURCE={BlendFactor.ZERO,BlendFactor.ONE,BlendFactor.SRC_ALPHA,BlendFactor.ONE_MINUS_SRC_ALPHA};
 private static final BlendFactor[] DEST={BlendFactor.ZERO,BlendFactor.ONE,BlendFactor.SRC_COLOR,BlendFactor.ONE_MINUS_SRC_COLOR,BlendFactor.SRC_ALPHA,BlendFactor.ONE_MINUS_SRC_ALPHA};
 static RenderType get(Identifier texture,int mode){return cache.computeIfAbsent(new Key(texture,mode),OriginalWorldMaterial::create);}
 static void clear(){cache.clear();}
 private static RenderType create(Key key){
  int mode=key.mode,src=mode&15,dst=(mode>>4)&15;if(src>=SOURCE.length||dst>=DEST.length)throw new IllegalArgumentException("Invalid original blend");
  RenderPipeline base=((mode&0x800)!=0?((mode&0x100)!=0?RenderTypes.entityCutout(key.texture):RenderTypes.entityTranslucent(key.texture)):RenderTypes.entityTranslucentEmissive(key.texture)).pipeline();
  var pipeline=RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("generalsmc","world/"+Integer.toHexString(mode)))
   .withVertexShader(base.getShaders().get(ShaderType.VERTEX)).withFragmentShader(base.getShaders().get(ShaderType.FRAGMENT)).withVertexBinding(0,base.getVertexFormatBinding(0)).withPrimitiveTopology(base.getPrimitiveTopology()).withCull(false)
   .withColorTargetState(new ColorTargetState(java.util.Optional.of(new BlendFunction(SOURCE[src],DEST[dst])),base.getColorTargetStates().getFirst().format(),(mode&0x20000)==0||(mode&0x400)!=0?ColorTargetState.WRITE_ALL:ColorTargetState.WRITE_NONE));
  base.getBindGroupLayouts().forEach(pipeline::withBindGroupLayout);
  if((mode&0x800)==0){pipeline.withVertexShader(Identifier.fromNamespaceAndPath("generalsmc","core/original_effect")).withFragmentShader(Identifier.fromNamespaceAndPath("generalsmc","core/original_effect"));if((mode&0x100)!=0)pipeline.withShaderDefine("GENERALS_ALPHA_TEST");}
  base.getShaderDefines().flags().forEach(pipeline::withShaderDefine);base.getShaderDefines().values().forEach((name,value)->pipeline.withShaderDefine(name,Float.parseFloat(value)));
  int depth=(mode&0x20000)==0?3:(mode>>12)&7;CompareOp[] ops={CompareOp.NEVER_PASS,CompareOp.LESS_THAN,CompareOp.EQUAL,CompareOp.LESS_THAN_OR_EQUAL,CompareOp.GREATER_THAN,CompareOp.NOT_EQUAL,CompareOp.GREATER_THAN_OR_EQUAL,CompareOp.ALWAYS_PASS};
  pipeline.withDepthStencilState(new DepthStencilState(ops[base.getDepthStencilState().depthTest()==CompareOp.GREATER_THAN_OR_EQUAL?switch(depth){case 1->4;case 3->6;case 4->1;case 6->3;default->depth;}:depth],(mode&0x200)!=0));
  var setup=RenderSetup.builder(pipeline.build()).withTexture("Sampler0",key.texture).useLightmap().useOverlay().sortOnUpload().createRenderSetup();
  return local.generalsmc.client.mixin.OriginalRenderTypeAccessor.create("generals_material_"+mode,setup);
 }
}
