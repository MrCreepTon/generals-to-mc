package local.generalsmc.client;
import local.generalsmc.nativebridge.*;
import net.minecraft.world.phys.BlockHitResult;
/** Conservative plot for all starting buildings, including random generals. */
final class StartAreaPreview {
 private static int radius=24;
 static void profile(NativeProfiles.Profile profile){radius=profile==null||profile.startRadii()==null||profile.startRadii().isEmpty()?24:Math.clamp(profile.startRadii().values().stream().mapToInt(Integer::intValue).max().orElse(24),1,24);}
 static int radius(){return radius;}
 static boolean visible(SiteMarkerEntity marker){var mc=net.minecraft.client.Minecraft.getInstance();return marker.getBlockState().is(SiteBlocks.START)&&mc.player!=null&&mc.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(marker.getBlockPos()))<48*48&&(mc.player.getMainHandItem().is(SiteBlocks.START.asItem())||mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(marker.getBlockPos()));}
}
