package local.generalsmc.client;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;
/** Ordinary left click advances, right click goes back, without stealing screen input. */
final class CycleButton extends MenuButton {
    private final IntConsumer cycle;
    CycleButton(String label,int x,int y,int w,IntConsumer cycle){super(x,y,w,20,L.component(label),b->cycle.accept(1),DEFAULT_NARRATION);this.cycle=cycle;kind=Kind.FIELD;leftAligned=true;stepper=true;}
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick){double x=event.x(),y=event.y();int button=event.button();if(button==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT&&active&&visible&&isMouseOver(x,y)){playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());cycle.accept(-1);return true;}return super.mouseClicked(event,doubleClick);}
}
