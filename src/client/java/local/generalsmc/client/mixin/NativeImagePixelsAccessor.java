package local.generalsmc.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Copy the original GUI in one linear pass rather than millions of checked pixel setter calls. */
@Mixin(NativeImage.class)
public interface NativeImagePixelsAccessor {
    @Accessor("pixels") long generalsPixels();
}
