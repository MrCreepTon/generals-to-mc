package local.generalsmc.client.mixin;
import local.generalsmc.client.MenuScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Minecraft.class)
public abstract class ModernMenuOpenMixin {
 @ModifyVariable(method="setScreen",at=@At("HEAD"),argsOnly=true)
 private Screen generalsModernMenu(Screen screen){return MenuScreens.wrap(screen);}
}
