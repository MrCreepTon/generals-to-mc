package local.generalsmc.client;
import local.generalsmc.nativebridge.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.phys.Vec3;
final class SiteMarkerRenderer implements BlockEntityRenderer<SiteMarkerEntity,SiteMarkerRenderer.State>{
 static final class State extends BlockEntityRenderState {int color;float animation;boolean visible;}
 SiteMarkerRenderer(BlockEntityRendererProvider.Context context){}
 @Override public State createRenderState(){return new State();}
 @Override public void extractRenderState(SiteMarkerEntity marker,State state,float delta,Vec3 eye,ModelFeatureRenderer.CrumblingOverlay crumbling){BlockEntityRenderState.extractBase(marker,state,crumbling);state.visible=!NativeWorldView.controlling();state.color=marker.getBlockState().is(SiteBlocks.START)?0xff48bfff:marker.getBlockState().is(SiteBlocks.SUPPLY)?0xff62e179:0xffffbc42;state.animation=marker.getLevel().getGameTime()+delta;}
 @Override public void submit(State state,PoseStack pose,SubmitNodeCollector queue,CameraRenderState camera){if(state.visible)BeaconRenderer.submitBeaconBeam(pose,queue,BeaconRenderer.BEAM_LOCATION,state.animation,1,1,40,state.color,.08f,.12f);}
 @Override public boolean shouldRenderOffScreen(){return true;}
 @Override public int getViewDistance(){return 192;}
}
