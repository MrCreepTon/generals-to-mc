package local.generalsmc.client.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.client.renderer.rendertype.*;
@Mixin(RenderType.class)
public interface OriginalRenderTypeAccessor {
 @Invoker("create") static RenderType create(String name,RenderSetup setup){throw new AssertionError();}
}
