package local.generalsmc.client.mixin;
import com.mojang.blaze3d.platform.InputConstants;
import local.generalsmc.client.RtsKeyContext;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Map;
@Mixin(KeyMapping.class)
public abstract class RtsKeyMappingMixin {
    @Shadow @Final private static Map<String,KeyMapping> ALL;
    @Shadow @Final private static Map<InputConstants.Key,KeyMapping> MAP;
    @Inject(method="resetMapping",at=@At("RETURN"))
    private static void generalsGameplayMap(CallbackInfo ci){RtsKeyContext.restore(ALL,MAP);}
    @Inject(method="same",at=@At("HEAD"),cancellable=true)
    private void generalsSeparateContexts(KeyMapping other,CallbackInfoReturnable<Boolean> ci){
        if((RtsKeyContext.contexts((KeyMapping)(Object)this)&RtsKeyContext.contexts(other))==0)ci.setReturnValue(false);
    }
}
