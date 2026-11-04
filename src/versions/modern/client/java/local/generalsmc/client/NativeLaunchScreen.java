package local.generalsmc.client;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
final class NativeLaunchScreen extends CompatScreen {
    final Screen parent;final NativeProfiles.Profile profile;String faction="";int color=-1;boolean valid;
    NativeLaunchScreen(Screen parent,NativeProfiles.Profile profile){super(L.component(L.s("Свободная игра")));this.parent=parent;this.profile=profile;var saved=ArmyPreferences.get(profile);faction=saved.faction();color=saved.color();}
    @Override protected void init(){GeneralsSetupScreen.useProfile(profile);valid=profile!=null&&!NativeProfiles.needsRefresh(profile)&&NativeProfiles.available(profile);if(!valid){addRenderableWidget(MenuButton.styled(L.component(L.s("Исправить подключение")),b->{if(profile==null)minecraft.setScreen(new GeneralsSetupScreen.Connect(parent));else NativeProfilesScreen.refresh(parent,profile);}).bounds(width/2-155,58,310,20).build());addRenderableWidget(MenuButton.styled(L.component(L.s("Назад")),b->onClose()).quiet().bounds(width/2-155,height-35,310,20).build());return;}var factions=new ArrayList<String>();factions.add("");factions.addAll(profile.factions());var colors=new ArrayList<NativeSession.TeamColor>();colors.add(new NativeSession.TeamColor(-1,L.s("Случайный"),0xaaaaaa));colors.addAll(profile.colors());
        addRenderableWidget(MenuButton.styled(L.component(L.s("Сторона: ")+GeneralsSetupScreen.friendly(faction)),b->minecraft.setScreen(new GeneralsSetupScreen.Choices(this,L.s("Сторона"),factions.stream().map(GeneralsSetupScreen::friendly).toList(),List.of(),i->faction=factions.get(i)))).choice().bounds(width/2-155,58,310,20).build());
        addRenderableWidget(MenuButton.styled(L.component(L.s("Цвет: ")+(color<0?L.s("Случайный"):colors.stream().filter(c->c.id()==color).map(c->GeneralsSetupScreen.colorName(c.label())).findFirst().orElse("?"))),b->minecraft.setScreen(new GeneralsSetupScreen.Choices(this,L.s("Цвет армии"),colors.stream().map(c->c.id()<0?L.s("Случайный цвет"):GeneralsSetupScreen.colorName(c.label())).toList(),colors.stream().map(c->(int)c.color()).toList(),i->color=colors.get(i).id()))).choice().bounds(width/2-155,84,310,20).build());
        addRenderableWidget(MenuButton.styled(L.component(L.s("Начать")),b->{save();var random=new Random();String side=faction.isEmpty()?profile.factions().get(random.nextInt(profile.factions().size())):faction;int tint=color<0?profile.colors().get(random.nextInt(profile.colors().size())).id():color;minecraft.setScreen(null);NativeRtsController.open(side,tint);}).primary().bounds(width/2-155,height-60,310,20).build());
        addRenderableWidget(MenuButton.styled(L.component(L.s("Назад")),b->onClose()).quiet().bounds(width/2-155,height-35,310,20).build());}
    @Override public void render(GuiGraphics g,int x,int y,float d){MenuButton.panel(g,width,height);super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,16,0xfff0d68a);g.drawWordWrap(font,L.component(valid?L.s("Армия без соперников. Для боя с NPC или друзьями откройте «Схватку»."):L.s("Обновите данные игры, чтобы загрузить её фракции.")),width/2-155,115,310,0xffbbbbbb);}
    void save(){ArmyPreferences.save(profile,new ArmyPreferences.Choice(faction,color,-1,-1));}
    @Override public void onClose(){save();minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
