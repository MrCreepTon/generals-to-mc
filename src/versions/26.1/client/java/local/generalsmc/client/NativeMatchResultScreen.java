package local.generalsmc.client;
import local.generalsmc.nativebridge.NativeNetworkServer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
final class NativeMatchResultScreen extends CompatScreen {
    final NativeNetworkServer.MatchResult result;
    NativeMatchResultScreen(NativeNetworkServer.MatchResult result){super(L.component(result.draw()?L.s("Ничья"):result.won()?L.s("Победа"):L.s("Поражение")));this.result=result;}
    @Override protected void init(){int width=Math.min(310,this.width-24),x=(this.width-width)/2;addRenderableWidget(MenuButton.styled(L.component(L.s("Новая схватка")),b->minecraft.setScreen(new NativeLobbyScreen(null))).bounds(x,height-62,width,20).build());addRenderableWidget(MenuButton.styled(L.component(L.s("В Minecraft")),b->onClose()).bounds(x,height-35,width,20).build());}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){MenuButton.panel(g,width,height);super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,28,result.won()?0xffbbee99:0xfff0d68a);if(!result.winners().isEmpty())g.textWithWordWrap(font,L.component(L.s("Победители: ")+String.join(", ",result.winners().stream().map(L::decode).toList())),Math.max(12,width/2-155),65,Math.min(310,width-24),0xffeeeeee);}
    @Override public void onClose(){minecraft.setScreen(null);}@Override public boolean isPauseScreen(){return false;}
}
