package local.generalsmc.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
/** Test driver screen access across the Minecraft GUI API boundary. */
final class NativeVersionScreenBench {
    static void initialize(Minecraft mc,Screen screen,int w,int h){screen.init(w,h);}
    static void shot(Minecraft mc,String name)throws Exception{java.nio.file.Files.deleteIfExists(mc.gameDirectory.toPath().resolve("screenshots").resolve(name));net.minecraft.client.Screenshot.grab(mc.gameDirectory,name,mc.gameRenderer.mainRenderTarget(),1,c->{});}
    static void click(Screen screen,net.minecraft.client.gui.components.Button b){double x=b.getX()+b.getWidth()/2.,y=b.getY()+b.getHeight()/2.;((CompatScreen)screen).mouseClicked(x,y,0);((CompatScreen)screen).mouseReleased(x,y,0);}
    static void key(Screen screen,int key){((CompatScreen)screen).keyPressed(key,0,0);}
    static Screen actual(Minecraft mc){return mc.gui.screen();}
    static Screen current(Minecraft mc){return MenuScreens.active(mc.gui.screen());}
    static void show(Minecraft mc,Screen screen){mc.gui.setScreen(screen);}
    static net.minecraft.world.entity.Mob slime(net.minecraft.server.level.ServerLevel level){return new net.minecraft.world.entity.monster.cubemob.Slime(net.minecraft.world.entity.EntityTypes.SLIME,level);}
}
