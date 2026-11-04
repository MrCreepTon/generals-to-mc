package local.generalsmc.client;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
/** Drives real Modern UI widgets on their UI thread; no optional SDK linkage in the harness. */
final class NativeModernWidgetBench {
 static boolean ready(Screen screen){return screen instanceof MenuScreens.Host h&&h.nativeWidgetCount()>0;}
 static void action(Screen screen,AbstractWidget source,String text)throws Exception{
  var f=screen.getClass().getDeclaredField("fragment");f.setAccessible(true);var fragment=f.get(screen);var r=fragment.getClass().getDeclaredField("root");r.setAccessible(true);Object root=r.get(fragment);if(root==null)throw new IllegalStateException("Modern UI root not attached");
  Runnable action=()->{try{var controls=root.getClass().getDeclaredField("controls");controls.setAccessible(true);var map=(java.util.Map<?,?>)controls.get(root);var view=map.get(source);if(view==null)throw new IllegalStateException("Modern UI widget missing");if(text==null)view.getClass().getMethod("performClick").invoke(view);else {var setter=view.getClass().getMethod("setText",CharSequence.class);for(int i=1;i<=text.length();i++)setter.invoke(view,text.substring(0,i));}}catch(Exception error){local.generalsmc.GeneralsMod.LOG.error("Native Modern UI widget oracle failed",error);}};
  root.getClass().getMethod("post",Runnable.class).invoke(root,action);
 }
}
