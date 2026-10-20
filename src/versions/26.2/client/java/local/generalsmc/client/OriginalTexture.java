package local.generalsmc.client;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.Identifier;
/** Shared backend sampler policy; static original art retains its mip chain. */
final class OriginalTexture extends DynamicTexture {
 OriginalTexture(NativeImage image,boolean mipmaps){
  super(()->"Generals texture",image);
  int levels=mipmaps?31-Integer.numberOfLeadingZeros(Math.min(image.getWidth(),image.getHeight())):0;
  sampler=RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR,levels>0);
  if(levels==0)return;
  NativeImage[] images=MipmapGenerator.generateMipLevels(Identifier.fromNamespaceAndPath("generalsmc","source_art"),new NativeImage[]{image},levels,MipmapStrategy.MEAN,0,image.computeTransparency());
  try{
   releaseTextures();var device=RenderSystem.getDevice();
   texture=device.createTexture(()->"Generals mipmapped art",GpuTexture.USAGE_TEXTURE_BINDING|GpuTexture.USAGE_COPY_DST,com.mojang.blaze3d.GpuFormat.RGBA8_UNORM,image.getWidth(),image.getHeight(),1,levels+1);
   textureView=device.createTextureView(texture);var encoder=device.createCommandEncoder();
   for(int level=0;level<=levels;level++)encoder.writeToTexture(texture,images[level],level,0,0,0);
   encoder.submit();
  }finally{for(int level=1;level<images.length;level++)images[level].close();}
 }
}
