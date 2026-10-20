package local.generalsmc.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.*;

/** Preserve smooth original art filtering when vanilla entity shards request nearest filtering. */
final class OriginalTexture extends DynamicTexture {
    private boolean mipmaps;
    OriginalTexture(NativeImage image,boolean mipmaps) {
        super(image);this.mipmaps=mipmaps;
        if(mipmaps) {
            bind();GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            if(GL.getCapabilities().GL_EXT_texture_filter_anisotropic)
                GL11.glTexParameterf(GL11.GL_TEXTURE_2D,EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,Math.min(4,GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT)));
        }
        setFilter(true,mipmaps);
    }
    @Override public void setFilter(boolean blur,boolean mipmap){super.setFilter(true,mipmaps);}
}
