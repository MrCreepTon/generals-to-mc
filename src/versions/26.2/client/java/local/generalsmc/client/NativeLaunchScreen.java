package local.generalsmc.client;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
final class NativeLaunchScreen extends CompatScreen {
    final Screen parent;final NativeProfiles.Profile profile;String faction="";int color=-1;
    NativeLaunchScreen(Screen parent,NativeProfiles.Profile profile){super(L.component(L.s("Начать RTS здесь")));this.parent=parent;this.profile=profile;}
    @Override protected void init(){GeneralsSetupScreen.useProfile(profile);var factions=new ArrayList<String>();factions.add("");factions.addAll(profile.factions());var colors=new ArrayList<NativeSession.TeamColor>();colors.add(new NativeSession.TeamColor(-1,L.s("Случайный"),0xaaaaaa));colors.addAll(profile.colors());
        addRenderableWidget(Button.builder(L.component(L.s("Сторона: ")+GeneralsSetupScreen.friendly(faction)),b->minecraft.gui.setScreen(new GeneralsSetupScreen.Choices(this,L.s("Сторона"),factions.stream().map(GeneralsSetupScreen::friendly).toList(),List.of(),i->faction=factions.get(i)))).bounds(width/2-155,58,310,20).build());
        addRenderableWidget(Button.builder(L.component(L.s("Цвет: ")+(color<0?L.s("Случайный"):colors.stream().filter(c->c.id()==color).map(c->GeneralsSetupScreen.colorName(c.label())).findFirst().orElse("?"))),b->minecraft.gui.setScreen(new GeneralsSetupScreen.Choices(this,L.s("Цвет армии"),colors.stream().map(c->c.id()<0?L.s("Случайный цвет"):GeneralsSetupScreen.colorName(c.label())).toList(),colors.stream().map(c->(int)c.color()).toList(),i->color=colors.get(i).id()))).bounds(width/2-155,84,310,20).build());
        addRenderableWidget(Button.builder(L.component(L.s("Начать")),b->{var random=new Random();String side=faction.isEmpty()?profile.factions().get(random.nextInt(profile.factions().size())):faction;int tint=color<0?profile.colors().get(random.nextInt(profile.colors().size())).id():color;minecraft.gui.setScreen(null);NativeRtsController.open(side,tint);}).bounds(width/2-155,height-60,310,20).build());
        addRenderableWidget(Button.builder(L.component(L.s("Назад")),b->onClose()).bounds(width/2-155,height-35,310,20).build());}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,16,0xfff0d68a);g.textWithWordWrap(font,L.component(L.s("Выберите сторону и цвет перед запуском. Для соперников, союзников и компьютера откройте сетевую схватку / NPC.")),width/2-155,115,310,0xffbbbbbb);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
