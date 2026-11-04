package local.generalsmc.client;

import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;

/** Actual Modern UI host. Minecraft controllers retain validation and server transport. */
public final class ModernMenuScreen extends Screen implements MuiScreen,MenuScreens.Host {
 static volatile java.lang.ref.WeakReference<ModernMenuScreen> presented=new java.lang.ref.WeakReference<>(null);
 final Screen backend;final ModernMenuFragment fragment=new ModernMenuFragment(this);boolean closed,drag;
 final ScreenCallback callback=new ScreenCallback(){public boolean isPauseScreen(){return backend.isPauseScreen();}public boolean hasDefaultBackground(){return false;}public boolean shouldBlurBackground(){return false;}};
 public ModernMenuScreen(Screen backend){super(backend.getTitle());this.backend=backend;}
 public Screen controller(){return backend;}
 public int nativeWidgetCount(){return fragment.controlCount;}
 public Screen self(){return this;}
 public Fragment getFragment(){return fragment;}
 public ScreenCallback getCallback(){return callback;}
 public Screen getPreviousScreen(){return null;}
 public boolean isMenuScreen(){return false;}
 public void onBackPressed(){minecraft.execute(()->{if(popup())backend.keyPressed(256,0,0);else backend.onClose();});}
 void fail(Throwable error){MenuScreens.unavailable(error);minecraft.execute(()->minecraft.setScreen(backend));}
 @Override protected void init(){closed=false;presented=new java.lang.ref.WeakReference<>(this);try{backend.init(minecraft,width,height);fragment.update();UIManager.getInstance().initScreen(this);}catch(RuntimeException|LinkageError error){fail(error);}}
 @Override public void tick(){backend.tick();fragment.update();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){MenuScreens.background(()->backend.renderBackground(g,x,y,partial));}
 @Override public void render(GuiGraphics g,int x,int y,float partial){BlurHandler.INSTANCE.blur(this);MenuScreens.background(()->backend.render(g,x,y,partial));UIManager.getInstance().render(g,x,y,partial);}
 @Override public void removed(){closed=true;UIManager.getInstance().removed(this);backend.removed();}
 @Override public void onClose(){backend.onClose();}
 @Override public boolean isPauseScreen(){return backend.isPauseScreen();}
 boolean popup(){return backend.children().stream().anyMatch(w->w instanceof LobbyCell c&&c.option);}
 boolean controlAt(double x,double y){return backend.children().stream().anyMatch(w->w instanceof AbstractWidget a&&a.visible&&a.active&&a.isMouseOver(x,y));}
 @Override public void mouseMoved(double x,double y){backend.mouseMoved(x,y);UIManager.getInstance().onHoverMove(true);}
 @Override public boolean mouseClicked(double x,double y,int button){drag=!controlAt(x,y);return drag&&backend.mouseClicked(x,y,button);}
 @Override public boolean mouseReleased(double x,double y,int button){boolean value=drag&&backend.mouseReleased(x,y,button);drag=false;return value;}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return drag&&backend.mouseDragged(x,y,button,dx,dy);}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(backend.mouseScrolled(x,y,horizontal,vertical)){fragment.update();return true;}UIManager.getInstance().onScroll(horizontal,vertical);return true;}
 @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==256){onBackPressed();return true;}if(popup()||key==84&&backend instanceof NativeLobbyScreen){boolean accepted=backend.keyPressed(key,scan,modifiers);fragment.update();if(accepted)return true;}UIManager.getInstance().onKeyPress(key,scan,modifiers);return false;}
 @Override public boolean keyReleased(int key,int scan,int modifiers){UIManager.getInstance().onKeyRelease(key,scan,modifiers);return false;}
 @Override public boolean charTyped(char value,int modifiers){return UIManager.getInstance().onCharTyped(value);}
 void pressBackend(net.minecraft.client.gui.components.Button button){button.onPress();}
 boolean keyBackend(int key,int scan,int modifiers){return backend.keyPressed(key,scan,modifiers);}
 void act(AbstractWidget source,Runnable action){minecraft.execute(()->{if(!closed&&MenuScreens.active(minecraft.screen)==backend&&backend.children().contains(source)&&source.visible&&source.active){action.run();fragment.update();}});}
 String tooltip(AbstractWidget widget){var value=widget instanceof LobbyCell c?c.currentTooltip:widget.getTooltip();if(value==null)return "";var out=new StringBuilder();for(var line:value.toCharSequence(minecraft)){if(!out.isEmpty())out.append('\n');line.accept((i,style,cp)->{out.appendCodePoint(cp);return true;});}return out.toString();}
}
