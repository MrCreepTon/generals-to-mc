package local.generalsmc.mixin;
import local.generalsmc.nativebridge.WorldEmp;
import net.minecraft.world.entity.Mob;
import net.minecraft.network.syncher.*;
import local.generalsmc.nativebridge.MobDisableState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Mob.class)
public abstract class EmpMobMixin implements MobDisableState {
 @Unique private static final EntityDataAccessor<Boolean> GENERALS_DISABLED=SynchedEntityData.defineId(Mob.class,EntityDataSerializers.BOOLEAN);
 @Inject(method="defineSynchedData",at=@At("TAIL"))
 private void generalsDefineDisabled(SynchedEntityData.Builder builder,CallbackInfo ci){builder.define(GENERALS_DISABLED,false);}
 @Override public boolean generalsDisabled(){return ((Mob)(Object)this).getEntityData().get(GENERALS_DISABLED);}
 @Inject(method="tick",at=@At("TAIL"))
 private void generalsSyncDisabled(CallbackInfo ci){var mob=(Mob)(Object)this;if(!mob.level().isClientSide())mob.getEntityData().set(GENERALS_DISABLED,WorldEmp.stunned(mob)||local.generalsmc.nativebridge.WorldTechnology.stunned(mob));}
 @Inject(method="isEffectiveAi",at=@At("HEAD"),cancellable=true)
 private void generalsEmpStun(CallbackInfoReturnable<Boolean> result){if(WorldEmp.stunned((Mob)(Object)this)||local.generalsmc.nativebridge.WorldTechnology.stunned((Mob)(Object)this))result.setReturnValue(false);}
}
