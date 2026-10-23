package local.generalsmc.client;
import local.generalsmc.nativebridge.NativeProfiles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;

/** Checking a connection is distinct from editing its folders. */
final class ProfileStatusScreen extends Screen {
    final Screen parent;final NativeProfiles.Profile profile;final boolean missing;
    volatile String status="";volatile boolean busy;Thread worker;
    ProfileStatusScreen(Screen parent,NativeProfiles.Profile profile,boolean missing){super(L.component(missing?L.s("Игра не найдена"):L.s("Проверка игры")));this.parent=parent;this.profile=profile;this.missing=missing;}
    @Override protected void init(){int x=width/2-155;
        if(missing){
            addRenderableWidget(MenuButton.styled(L.component(L.s("Указать папку")),b->minecraft.setScreen(new GeneralsSetupScreen.Connect(parent,profile))).bounds(x,height-86,310,20).build());
            addRenderableWidget(MenuButton.styled(L.component(L.s("Удалить подключение")),b->minecraft.setScreen(new ConfirmScreen(ok->{if(ok)try{NativeProfiles.remove(profile.id());minecraft.setScreen(parent);}catch(Exception e){status=e.getMessage();minecraft.setScreen(this);}else minecraft.setScreen(this);},L.component(L.s("Удалить подключение?")),L.component(profile.name())))).bounds(x,height-59,310,20).build());
        }else if(worker==null){busy=true;worker=Thread.ofVirtual().name("zhmc-refresh-game").start(()->{try{NativeProfiles.update(profile.id(),profile.name(),profile.game(),profile.base(),profile.mod(),profile.allArchives(),text->status=L.decode(text));minecraft.execute(()->{if(minecraft.screen==this)minecraft.setScreen(parent);});}catch(Exception e){status=L.decode(e.getMessage());}finally{busy=false;}});}
        addRenderableWidget(MenuButton.styled(L.component(L.s("Назад")),b->onClose()).bounds(x,height-33,310,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float d){MenuButton.panel(g,width,height);super.render(g,mx,my,d);g.drawCenteredString(font,title,width/2,16,0xfff0d68a);g.drawCenteredString(font,profile.name(),width/2,36,0xffeeeeee);String text=missing?L.s("Папка игры перемещена или удалена.")+"\n"+profile.game():status;int x=width/2-155;g.enableScissor(x,60,x+310,height-95);g.drawWordWrap(font,L.component(text),x,60,310,missing||!busy?0xffffbbbb:0xffdddddd);g.disableScissor();if(my>=60&&my<height-95)g.renderTooltip(font,font.split(L.component(text),310),mx,my);}
    @Override public void onClose(){if(worker!=null&&busy)worker.interrupt();minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
