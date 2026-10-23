package local.generalsmc.client;

import local.generalsmc.nativebridge.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Persistent world marker editor, opened by the player's ordinary block interaction. */
public final class NativeStartScreen extends CompatScreen {
    private final Screen parent;private final boolean remote;private final WorldSiteStore.Start start;private int slot,team,difficulty,color;private String faction;
    public NativeStartScreen(Screen parent,WorldSiteStore.Start start){this(parent,start,false);}
    public NativeStartScreen(Screen parent,WorldSiteStore.Start start,boolean remote){super(L.component(L.s("Старт армии")));this.parent=parent;this.remote=remote;this.start=start;slot=start.slot();team=start.team();difficulty=start.difficulty();faction=start.faction();color=start.color();}
    private void button(String label,int y,Runnable run){addRenderableWidget(MenuButton.styled(L.component(label),b->run.run()).bounds(width/2-155,y,310,20).build());}
    private void cycle(String label,int y,java.util.function.IntConsumer action){addRenderableWidget(new CycleButton(label,width/2-155,y,310,d->{action.accept(d);rebuildWidgets();}));}
    @Override protected void init(){int y=48;cycle(L.s("Позиция: ")+(slot+1),y,d->slot=Math.floorMod(slot+d,8));cycle(L.s("Участник: ")+switch(difficulty){case 0->L.s("NPC — лёгкий");case 1->L.s("NPC — средний");case 2->L.s("NPC — сложный");default->L.s("игрок");},y+24,d->difficulty=Math.floorMod(difficulty+1+d,4)-1);cycle(L.s("Команда: ")+NativeLobbyScreen.team(team),y+48,d->team=NativeLobbyScreen.nextTeam(team,d));button(L.s("Сторона NPC: ")+(faction.isEmpty()?L.s("Случайная"):GeneralsSetupScreen.friendly(faction)),y+72,()->{var values=new ArrayList<String>();values.add("");values.addAll(NativeRtsController.availableFactions());minecraft.setScreen(new GeneralsSetupScreen.Choices(this,L.s("Сторона NPC"),values.stream().map(s->s.isEmpty()?L.s("Случайный генерал"):GeneralsSetupScreen.friendly(s)).toList(),List.of(),i->faction=values.get(i)));});button(L.s("Сохранить"),height-59,()->NativeNetworkClient.request(remote?"marker_start":"edit_start",j->{j.addProperty("x",start.pos().getX());j.addProperty("y",start.pos().getY());j.addProperty("z",start.pos().getZ());j.addProperty("slot",slot);j.addProperty("team",team);j.addProperty("difficulty",difficulty);j.addProperty("faction",faction);j.addProperty("color",color);}));button(L.s("Назад"),height-35,this::onClose);}
    @Override public void render(GuiGraphics g,int mx,int my,float d){MenuButton.panel(g,width,height);super.render(g,mx,my,d);g.drawCenteredString(font,title,width/2,16,0xfff0d68a);g.drawCenteredString(font,start.pos().toShortString(),width/2,31,0xffbbbbbb);if(!NativeNetworkClient.error.isBlank()){int x=width/2-155,y=height-85;g.fill(x-2,y-2,x+312,y+20,0xee181b20);g.enableScissor(x,y,x+310,y+20);g.drawWordWrap(font,L.component(NativeNetworkClient.error),x,y,310,0xffff8888);g.disableScissor();if(my>=y&&my<y+20)g.setTooltipForNextFrame(font,font.split(L.component(NativeNetworkClient.error),300),mx,my);}}
    @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
