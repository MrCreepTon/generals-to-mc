package local.generalsmc.nativebridge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.*;

/** Original weapon cadence bypasses only vanilla's hurt cooldown, preserving armor and boss immunity. */
final class NativeDamage {
    private static final ResourceKey<DamageType> PROJECTILE=key("native_projectile"),DIRECT=key("native_direct"),EXPLOSION=key("native_explosion");
    private static ResourceKey<DamageType> key(String name){return ResourceKey.create(Registries.DAMAGE_TYPE,Identifier.fromNamespaceAndPath("generalsmc",name));}
    static DamageSource source(ServerLevel level,NativeSession.CombatEvent event,Entity attacker,Entity target){
        // Values come from the pinned source DamageType enum, not individual weapon definitions.
        boolean projectile=switch(event.damageType()){case 2,3,4,8,24,25,27,28,30,31->true;default->false;};
        var key=event.explosive()?EXPLOSION:projectile&&!(target instanceof net.minecraft.world.entity.monster.Enderman)?PROJECTILE:DIRECT;
        Entity direct=projectile?(attacker instanceof net.minecraft.world.entity.LivingEntity living?new Arrow(level,living,new ItemStack(Items.ARROW),null):new Arrow(net.minecraft.world.entity.EntityTypes.ARROW,level)):attacker;
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key),direct,attacker);
    }
}
