package local.generalsmc.client;

import local.generalsmc.nativebridge.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import java.util.*;

/** Select, add, edit and remove game connections in one place. */
final class NativeProfilesScreen extends CompatScreen {
    final Screen parent;int page;String error="";List<NativeProfiles.Profile> profiles=List.of();
    NativeProfilesScreen(Screen parent){super(L.component(L.s("Мои игры")));this.parent=parent;}
    static void refresh(Screen parent,NativeProfiles.Profile profile){net.minecraft.client.Minecraft.getInstance().gui.setScreen(new ProfileStatusScreen(parent,profile,!NativeProfiles.available(profile)));}
    private void remove(NativeProfiles.Profile p){minecraft.gui.setScreen(new ConfirmScreen(ok->{if(ok)try{NativeProfiles.remove(p.id());}catch(Exception e){error=e.getMessage();}minecraft.gui.setScreen(this);},L.component(L.s("Удалить подключение?")),L.component(p.name()+"\n"+L.s("Установленная игра и её файлы останутся на месте."))){@Override public void onClose(){minecraft.gui.setScreen(NativeProfilesScreen.this);}});}
    @Override protected void init(){
        String active="";try{var r=NativeProfiles.read();profiles=r.profiles();active=r.active();}catch(Exception e){error=e.getMessage();}
        int rows=Math.max(1,(height-133)/27),x=width/2-155;page=Math.clamp(page,0,Math.max(0,(profiles.size()-1)/rows));
        for(int i=page*rows;i<Math.min(profiles.size(),(page+1)*rows);i++){
            var p=profiles.get(i);int y=46+(i-page*rows)*27;
            var select=addRenderableWidget(MenuButton.styled(L.component(p.name()),b->{try{NativeProfiles.select(p.id());if(!NativeProfiles.available(p))minecraft.gui.setScreen(new ProfileStatusScreen(this,p,true));else if(NativeProfiles.needsRefresh(p))refresh(parent,p);else minecraft.gui.setScreen(parent);}catch(Exception e){error=e.getMessage();}}).bounds(x,y,194,20).build());
            select.setTooltip(Tooltip.create(L.component(p.game()+(p.mod().isBlank()?"":"\n"+p.mod()))));
            var refresh=addRenderableWidget(MenuButton.styled(L.component("↻"),b->refresh(this,p)).bounds(x+198,y,34,20).build());refresh.setTooltip(Tooltip.create(L.component(L.s("Обновить данные игры и список фракций"))));
            var edit=addRenderableWidget(MenuButton.styled(L.component("✎"),b->minecraft.gui.setScreen(new GeneralsSetupScreen.Connect(this,p))).bounds(x+236,y,31,20).build());edit.setTooltip(Tooltip.create(L.component(L.s("Изменить подключение"))));
            var remove=addRenderableWidget(MenuButton.styled(L.component("×"),b->remove(p)).bounds(x+273,y,37,20).build());remove.setTooltip(Tooltip.create(L.component(L.s("Удалить подключение"))));
        }
        if(profiles.size()>rows){addRenderableWidget(MenuButton.styled(L.component("←"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(x,height-86,45,20).build());addRenderableWidget(MenuButton.styled(L.component("→"),b->{page=Math.min((profiles.size()-1)/rows,page+1);rebuildWidgets();}).bounds(x+265,height-86,45,20).build());}
        addRenderableWidget(MenuButton.styled(L.component(L.s("+ Добавить игру")),b->minecraft.gui.setScreen(new GeneralsSetupScreen.Connect(this))).bounds(x,height-59,310,20).build());
        addRenderableWidget(MenuButton.styled(L.component(L.s("Назад")),b->onClose()).bounds(x,height-32,310,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){MenuButton.panel(g,width,height);super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,18,0xfff0d68a);if(!error.isEmpty()){g.enableScissor(width/2-105,height-85,width/2+105,height-65);g.textWithWordWrap(font,L.component(L.decode(error)),width/2-105,height-85,210,0xffff9999);g.disableScissor();}}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
