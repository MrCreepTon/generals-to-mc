package local.generalsmc.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.*;
/** Adapt changed input event objects at the Minecraft UI boundary. */
abstract class CompatScreen extends Screen {
 protected CompatScreen(Component title){super(title);}
 static int legacyButton(int b){return b==1?0:b==3?1:b==2?2:b-1;}
 static int sdlButton(int b){return b==0?1:b==1?3:b==2?2:b+1;}
 private static MouseButtonEvent mouse(double x,double y,int button){return new MouseButtonEvent(x,y,new MouseButtonInfo(sdlButton(button),0));}
 @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){return mouseClicked(e.x(),e.y(),legacyButton(e.button()));}
 public boolean mouseClicked(double x,double y,int button){return super.mouseClicked(mouse(x,y,button),false);}
 @Override public boolean mouseReleased(MouseButtonEvent e){return mouseReleased(e.x(),e.y(),legacyButton(e.button()));}
 public boolean mouseReleased(double x,double y,int button){return super.mouseReleased(mouse(x,y,button));}
 @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){return mouseDragged(e.x(),e.y(),legacyButton(e.button()),dx,dy);}
 public boolean mouseDragged(double x,double y,int button,double dx,double dy){return super.mouseDragged(mouse(x,y,button),dx,dy);}
 @Override public boolean keyPressed(KeyEvent e){return keyPressed(e.key(),e.keycode(),e.modifiers());}
 public boolean keyPressed(int key,int scan,int modifiers){return super.keyPressed(new KeyEvent(key,scan,modifiers));}
 @Override public boolean keyReleased(KeyEvent e){return keyReleased(e.key(),e.keycode(),e.modifiers());}
 public boolean keyReleased(int key,int scan,int modifiers){return super.keyReleased(new KeyEvent(key,scan,modifiers));}
 @Override public boolean charTyped(CharacterEvent e){return e.codepoint()>Character.MAX_VALUE?super.charTyped(e):charTyped((char)e.codepoint(),0);}
 public boolean charTyped(char value,int modifiers){return super.charTyped(new CharacterEvent(value));}
}
