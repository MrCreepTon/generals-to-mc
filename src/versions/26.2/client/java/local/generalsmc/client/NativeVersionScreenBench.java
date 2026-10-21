package local.generalsmc.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
/** Test driver screen access across the Minecraft GUI API boundary. */
final class NativeVersionScreenBench {
    static Screen current(Minecraft mc){return mc.gui.screen();}
    static void show(Minecraft mc,Screen screen){mc.gui.setScreen(screen);}
    static net.minecraft.world.entity.Mob slime(net.minecraft.server.level.ServerLevel level){return new net.minecraft.world.entity.monster.cubemob.Slime(net.minecraft.world.entity.EntityTypes.SLIME,level);}
}
