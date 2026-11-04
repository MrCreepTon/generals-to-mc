package local.generalsmc.client.mixin;
import local.generalsmc.client.MenuScreens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractWidget.class)
public abstract class ModernWidgetSuppressionMixin {
 @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
 private void generalsNativeWidgets(GuiGraphicsExtractor g,int x,int y,float partial,CallbackInfo ci){if(MenuScreens.widgetsHidden())ci.cancel();}
}
