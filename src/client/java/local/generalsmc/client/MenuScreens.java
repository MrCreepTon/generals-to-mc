package local.generalsmc.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import local.generalsmc.GeneralsMod;

/** Optional presentation host; server requests and menu controllers stay shared. */
public final class MenuScreens {
 public interface Host {Screen controller();default int nativeWidgetCount(){return 0;}}
 private static boolean failed;
 private static final ThreadLocal<Boolean> backdrop=ThreadLocal.withInitial(()->false);
 public static Screen active(Screen screen){return screen instanceof Host host?host.controller():screen;}
 public static boolean widgetsHidden(){return backdrop.get();}
 public static void background(Runnable paint){boolean old=backdrop.get();backdrop.set(true);try{paint.run();}finally{backdrop.set(old);}}
 public static boolean supported(){String v=FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();return v.equals("1.21.4")||v.equals("26.1")||v.equals("26.1.1")||v.equals("26.1.2");}
 public static Screen wrap(Screen screen){
  if(failed||screen==null||screen instanceof Host||screen instanceof NativeFrameScreen||!screen.getClass().getName().startsWith("local.generalsmc.client.")||!supported()||!FabricLoader.getInstance().isModLoaded("modernui"))return screen;
  try{return (Screen)Class.forName("local.generalsmc.client.ModernMenuScreen").getConstructor(Screen.class).newInstance(screen);}
  catch(ReflectiveOperationException|LinkageError error){unavailable(error);return screen;}
 }
 public static void unavailable(Throwable error){if(!failed)GeneralsMod.LOG.error("Modern UI menu adapter failed; using the built-in menus",error);failed=true;}
 private MenuScreens(){}
}
