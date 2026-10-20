package local.generalsmc.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.*;
/** Adapt changed input event objects at the Minecraft UI boundary. */
abstract class CompatScreen extends Screen {
 protected CompatScreen(Component title){super(title);}
 private static MouseButtonEvent mouse(double x,double y,int button){return new MouseButtonEvent(x,y,new MouseButtonInfo(button,0));}
 @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){return mouseClicked(e.x(),e.y(),e.button());}
 public boolean mouseClicked(double x,double y,int button){return super.mouseClicked(mouse(x,y,button),false);}
 @Override public boolean mouseReleased(MouseButtonEvent e){return mouseReleased(e.x(),e.y(),e.button());}
 public boolean mouseReleased(double x,double y,int button){return super.mouseReleased(mouse(x,y,button));}
 @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){return mouseDragged(e.x(),e.y(),e.button(),dx,dy);}
 public boolean mouseDragged(double x,double y,int button,double dx,double dy){return super.mouseDragged(mouse(x,y,button),dx,dy);}
 @Override public boolean keyPressed(KeyEvent e){return keyPressed(e.key(),e.scancode(),e.modifiers());}
 public boolean keyPressed(int key,int scan,int modifiers){return super.keyPressed(new KeyEvent(key,scan,modifiers));}
 @Override public boolean keyReleased(KeyEvent e){return keyReleased(e.key(),e.scancode(),e.modifiers());}
 public boolean keyReleased(int key,int scan,int modifiers){return super.keyReleased(new KeyEvent(key,scan,modifiers));}
 @Override public boolean charTyped(CharacterEvent e){return e.codepoint()>Character.MAX_VALUE?super.charTyped(e):charTyped((char)e.codepoint(),0);}
 public boolean charTyped(char value,int modifiers){return super.charTyped(new CharacterEvent(value));}
}
