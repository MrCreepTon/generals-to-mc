package local.generalsmc.client;

import local.generalsmc.nativebridge.NativeActorEntity;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Source geometry supplies its own oriented debug outline. */
final class NativeProxyRenderer extends NoopRenderer<NativeActorEntity> {
    NativeProxyRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public void extractRenderState(NativeActorEntity actor,EntityRenderState state,float partial){super.extractRenderState(actor,state,partial);state.isInvisible=true;}
}
