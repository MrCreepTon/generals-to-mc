package local.generalsmc.client.mixin;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import local.generalsmc.client.MobDisableVisual;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class MobDisableRenderState implements MobDisableVisual {
    @Unique private boolean generalsDisabled;
    public boolean generalsDisabled(){return generalsDisabled;}
    public void generalsDisabled(boolean value){generalsDisabled=value;}
}
