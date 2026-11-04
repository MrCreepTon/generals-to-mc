package local.generalsmc.client;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;
/** Ordinary left click advances, right click goes back, without stealing screen input. */
final class CycleButton extends MenuButton {
    private final IntConsumer cycle;
    CycleButton(String label,int x,int y,int w,IntConsumer cycle){super(x,y,w,20,L.component(label),b->cycle.accept(1),DEFAULT_NARRATION);this.cycle=cycle;kind=Kind.FIELD;leftAligned=true;stepper=true;}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==1&&active&&visible&&isMouseOver(x,y)){playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());cycle.accept(-1);return true;}return super.mouseClicked(x,y,button);}
}
