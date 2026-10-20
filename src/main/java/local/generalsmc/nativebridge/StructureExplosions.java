package local.generalsmc.nativebridge;

import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.util.Mth;

/** Damage at the exposed surface of large source buildings; normal entities use vanilla. */
public final class StructureExplosions {
    private StructureExplosions(){}
    public static void hurt(ServerExplosion blast,ExplosionDamageCalculator calculator){
        double radius=blast.radius()*2; if(radius<=0)return;
        Vec3 center=blast.center();var area=new AABB(center,center).inflate(radius);
        for(var actor:StructureCollisions.buildings(blast.level(),area)){
            if(actor.ignoreExplosion(blast)||!calculator.shouldDamageEntity(blast,actor))continue;
            var box=actor.getBoundingBox();var surface=new Vec3(Mth.clamp(center.x,box.minX,box.maxX),Mth.clamp(center.y,box.minY,box.maxY),Mth.clamp(center.z,box.minZ,box.maxZ));
            double distance=surface.distanceTo(center)/radius;if(distance>1)continue;
            // The invisible proxy is not a block wall. Actual Minecraft blocks
            // between the blast and the nearest building surface still shield it.
            var hit=blast.level().clip(new ClipContext(center,surface,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
            double exposure=hit.getType()==HitResult.Type.MISS?1:0;
            double impact=(1-distance)*exposure;if(impact<=0)continue;
            actor.hurtServer(blast.level(),blast.getDamageSource(),(float)((impact*impact+impact)/2*7*radius+1));
        }
    }
}
