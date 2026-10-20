package local.generalsmc.mixin;
import local.generalsmc.nativebridge.StructureCollisions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(WalkNodeEvaluator.class)
public abstract class StructureNavigationMixin {
    @Inject(method="getPathTypeOfMob",at=@At("HEAD"),cancellable=true)
    private void generalsPlanAroundBuildings(PathfindingContext context,int x,int y,int z,Mob mob,CallbackInfoReturnable<PathType> ci){
        double radius=mob.getBbWidth()/2.;var box=new AABB(x+.5-radius,y+.01,z+.5-radius,x+.5+radius,y+mob.getBbHeight(),z+.5+radius);
        if(StructureCollisions.blocksNavigation(mob.level(),mob,box))ci.setReturnValue(PathType.BLOCKED);
    }
}
