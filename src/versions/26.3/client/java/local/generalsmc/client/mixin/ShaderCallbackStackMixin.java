package local.generalsmc.client.mixin;

import local.generalsmc.client.ShaderCallbackStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="com.mojang.renderpearl.frontend.shaders.GlslCompiler",remap=false)
public abstract class ShaderCallbackStackMixin {
    @Inject(method="compileToSpv",at=@At("HEAD"))
    private void generalsmc$prepareCallbackStack(CallbackInfoReturnable<?> ci){ShaderCallbackStack.prepare();}
}
