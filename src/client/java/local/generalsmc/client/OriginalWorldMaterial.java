package local.generalsmc.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Original WW3D source/destination blend factors and depth policy. */
final class OriginalWorldMaterial extends RenderType {
    private record Key(ResourceLocation texture,int mode) {}
    private static final Map<Key,RenderType> cache=new HashMap<>();
    private static final GlStateManager.SourceFactor[] SOURCE={GlStateManager.SourceFactor.ZERO,GlStateManager.SourceFactor.ONE,GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.SourceFactor.ONE_MINUS_SRC_ALPHA};
    private static final GlStateManager.DestFactor[] DEST={GlStateManager.DestFactor.ZERO,GlStateManager.DestFactor.ONE,GlStateManager.DestFactor.SRC_COLOR,GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,GlStateManager.DestFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA};
    static RenderType get(ResourceLocation texture,int mode){return cache.computeIfAbsent(new Key(texture,mode),k->new OriginalWorldMaterial(k));}
    static void clear(){cache.clear();}
    private OriginalWorldMaterial(Key key){super("generals_material_"+key.mode,DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,false,true,setup(key),cleanup(key));}
    private static RenderType delegate(Key k){return (k.mode&0x800)!=0?((k.mode&0x100)!=0?RenderType.entityCutoutNoCull(k.texture):RenderType.entityTranslucent(k.texture)):RenderType.entityTranslucentEmissive(k.texture);}
    private static Runnable setup(Key k){return ()->{
        delegate(k).setupRenderState();
        int src=k.mode&15,dst=(k.mode>>4)&15;
        if(src>=SOURCE.length||dst>=DEST.length)throw new IllegalArgumentException("Invalid original blend factors");
        RenderSystem.enableBlend();RenderSystem.blendFunc(SOURCE[src],DEST[dst]);
        RenderSystem.depthMask((k.mode&0x200)!=0);if((k.mode&0x80000)!=0)RenderSystem.enableCull();else RenderSystem.disableCull();
        if((k.mode&0x20000)!=0){RenderSystem.depthFunc(512+((k.mode>>12)&7));boolean color=(k.mode&0x400)!=0;RenderSystem.colorMask(color,color,color,color);}
    };}
    private static Runnable cleanup(Key k){return ()->{delegate(k).clearRenderState();RenderSystem.depthMask(true);RenderSystem.depthFunc(515);RenderSystem.colorMask(true,true,true,true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();};}
}
