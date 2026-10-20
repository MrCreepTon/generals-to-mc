package local.generalsmc.client;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;
/** Ordinary left click advances, right click goes back, without stealing screen input. */
final class CycleButton extends Button.Plain {
    private final IntConsumer cycle;
    CycleButton(String label,int x,int y,int w,IntConsumer cycle){super(x,y,w,20,L.component(label),b->cycle.accept(1),DEFAULT_NARRATION);this.cycle=cycle;}
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick){double x=event.x(),y=event.y();int button=event.button();if(button==1&&active&&visible&&isMouseOver(x,y)){playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());cycle.accept(-1);return true;}return super.mouseClicked(event,doubleClick);}
}
