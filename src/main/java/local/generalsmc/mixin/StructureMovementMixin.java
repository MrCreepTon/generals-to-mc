package local.generalsmc.mixin;
import local.generalsmc.nativebridge.StructureCollisions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
/** Precise footprints for movement, free-space queries and block placement. */
@Mixin(EntityGetter.class)
public interface StructureMovementMixin {
    @Inject(method="getEntityCollisions",at=@At("RETURN"),cancellable=true)
    private void generalsStructureCollision(Entity mover,AABB area,CallbackInfoReturnable<List<VoxelShape>> result){if((Object)this instanceof Level level)result.setReturnValue(StructureCollisions.supplement(level,mover,area,result.getReturnValue()));}
    @Inject(method="isUnobstructed",at=@At("HEAD"),cancellable=true)
    private void generalsStructurePlacement(Entity except,VoxelShape shape,CallbackInfoReturnable<Boolean> result){if((Object)this instanceof Level level&&StructureCollisions.blocksPlacement(level,except,shape))result.setReturnValue(false);}
}
