package local.generalsmc.client;
import local.generalsmc.nativebridge.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;

/** Vanilla beacon geometry gives distant landmarks without any world scans or tick work. */
final class SiteMarkerRenderer implements BlockEntityRenderer<SiteMarkerEntity> {
    SiteMarkerRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(SiteMarkerEntity marker,float delta,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(NativeWorldView.controlling())return;
        int color=marker.getBlockState().is(SiteBlocks.START)?0xff48bfff:marker.getBlockState().is(SiteBlocks.SUPPLY)?0xff62e179:0xffffbc42;
        BeaconRenderer.renderBeaconBeam(pose,buffers,BeaconRenderer.BEAM_LOCATION,delta,1,marker.getLevel().getGameTime(),1,40,color,.08f,.12f);
if(StartAreaPreview.visible(marker)){int r=StartAreaPreview.radius(marker);for(int edge=0;edge<4;edge++){pose.pushPose();pose.translate(edge<2?-r:(edge==2?-r:r+1),.58,edge<2?(edge==0?-r:r+1):-r);pose.mulPose(edge<2?com.mojang.math.Axis.ZP.rotationDegrees(-90):com.mojang.math.Axis.XP.rotationDegrees(90));BeaconRenderer.renderBeaconBeam(pose,buffers,BeaconRenderer.BEAM_LOCATION,0,1,marker.getLevel().getGameTime(),0,r*2+1,color,.06f,.09f);pose.popPose();}}
    }
    @Override public boolean shouldRenderOffScreen(SiteMarkerEntity marker){return true;}
    @Override public int getViewDistance(){return 192;}
}
