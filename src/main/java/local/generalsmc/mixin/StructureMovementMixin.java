package local.generalsmc.mixin;
import local.generalsmc.nativebridge.StructureCollisions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import java.util.List;
@Mixin(Entity.class)
public abstract class StructureMovementMixin {
    @Redirect(method="collide",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<VoxelShape> generalsStructureCollision(Level level,Entity mover,AABB area){return StructureCollisions.supplement(level,mover,area,level.getEntityCollisions(mover,area));}
}
