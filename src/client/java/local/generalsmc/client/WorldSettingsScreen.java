package local.generalsmc.client;

import local.generalsmc.nativebridge.NativeNetworkServer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** All gameplay changes go through the world's server, including in singleplayer. */
public final class WorldSettingsScreen extends Screen {
    private final Screen parent;private NativeNetworkServer.RulesState shown;private int scroll,contentHeight;
    private final List<AbstractWidget> rows=new ArrayList<>();
    WorldSettingsScreen(Screen parent){super(L.component(L.s("Мир и взаимодействия")));this.parent=parent;NativeNetworkClient.rules=null;NativeNetworkClient.request("rules_get");}
    @Override protected void init(){rows.clear();shown=NativeNetworkClient.rules;int w=Math.min(330,width-24),x=(width-w)/2,y=58-scroll;
        if(shown!=null){var v=shown.values();
            row("blockDestruction",L.s("Разрушать блоки оружием"),v.blockDestruction(),L.s("Снаряды и взрывы Generals разрушают блоки. Урон юнитам и мобам сохраняется при любом значении."),x,y,w,false);y+=25;
            row("fireSpread",L.s("Поджигать блоки"),v.fireSpread(),L.s("Огнемёты, в том числе танк «Дракон», оставляют огонь на блоках. Дальнейшее горение зависит от правил Minecraft."),x,y,w,false);y+=25;
            row("hostileMobs",L.s("Монстры атакуют армию"),v.hostileMobs(),L.s("Враждебные мобы могут выбирать видимые юниты Generals своей целью."),x,y,w,false);y+=25;
            row("neutralRetaliation",L.s("Нейтральные мобы отвечают"),v.neutralRetaliation(),L.s("Големы и нейтральные мобы защищаются, когда армия атакует их. Мирные мобы не начинают бой сами."),x,y,w,false);y+=25;
            row("environmentDamage",L.s("Огонь, лава и утопление"),v.environmentDamage(),L.s("Мир Minecraft может повреждать юниты и здания Generals огнём, лавой и водой."),x,y,w,false);y+=25;
            row("mobGeneralExperience",L.s("Опыт генерала за мобов"),v.mobGeneralExperience(),L.s("Убийства мобов армией повышают генеральский ранг. Опыт начисляется один раз за настоящую смерть; убийства игроком не засчитываются армии."),x,y,w,false);y+=25;
            row("eliminationVictory",L.s("Победа по основным зданиям"),v.eliminationVictory(),L.s("В схватке армия выбывает после потери всех основных зданий. Союз побеждает, когда противников не остаётся. Свободная RTS в одиночном мире продолжается без этого условия."),x,y,w,false);y+=25;
            row("naturalResources",L.s("Ресурсы: ")+(v.naturalResources()?L.s("мир + маркеры"):L.s("только маркеры")),true,L.s("Мир + маркеры: склады появляются в подходящих местах по seed. Только маркеры: расставьте блоки ресурсов вручную. Меняется до начала схватки."),x,y,w,shown.resourcesLocked());y+=25;
        }
        if(shown!=null){setting("blastRadius",L.s("Радиус разрушения блоков: ×")+shown.values().blastRadius(),L.s("Меняет только зону разрушения блоков Minecraft. Дальность и урон оружия Generals сохраняются."),x,y,w);y+=25;setting("blastPower",L.s("Пробитие блоков: ×")+shown.values().blastPower(),L.s("Усиливает разрушение дерева, земли и камня. Не изменяет урон юнитам и мобам."),x,y,w);y+=25;}
        contentHeight=y+scroll-58;for(var r:rows)r.visible=r.getY()>=56&&r.getY()+20<=height-70;
        addRenderableWidget(Button.builder(L.component(L.s("Готово")),b->onClose()).bounds(x,height-30,w,20).build());
    }
    private void setting(String key,String text,String detail,int x,int y,int w){var b=addRenderableWidget(Button.builder(L.component(text),v->NativeNetworkClient.request("rules_change",j->j.addProperty("key",key))).bounds(x,y,w,20).build());b.active=shown.editable();b.setTooltip(Tooltip.create(L.component(detail)));rows.add(b);}
    private void row(String key,String label,boolean value,String detail,int x,int y,int w,boolean locked){String text=key.equals("naturalResources")?label:label+": "+(value?L.s("да"):L.s("нет"));var b=addRenderableWidget(Button.builder(L.component(text),button->NativeNetworkClient.request("rules_change",j->j.addProperty("key",key))).bounds(x,y,w,20).build());b.active=shown.editable()&&!locked;b.setTooltip(Tooltip.create(L.component(detail+(locked?L.s(" Завершите текущую схватку для изменения."):""))));rows.add(b);}
    @Override public void tick(){if(shown!=NativeNetworkClient.rules)rebuildWidgets();}
    @Override public void render(GuiGraphics g,int mx,int my,float d){super.render(g,mx,my,d);g.drawCenteredString(font,title,width/2,15,0xfff0d68a);g.drawCenteredString(font,shown==null?L.s("Правила мира"):shown.editable()?L.s("Сохраняется сразу • действует для всех игроков"):L.s("Правила задаёт владелец мира"),width/2,34,0xffbbbbbb);if(shown==null)g.drawWordWrap(font,L.component(NativeNetworkClient.message.isBlank()?L.s("Загрузка…"):NativeNetworkClient.message),(width-Math.min(330,width-24))/2,62,Math.min(330,width-24),0xffdddddd);if(!NativeNetworkClient.error.isBlank()){int x=width/2-Math.min(330,width-24)/2,y=height-60;g.fill(x-2,y-2,x+Math.min(330,width-24)+2,y+19,0xee181b20);g.enableScissor(x,y,x+Math.min(330,width-24),y+20);g.drawWordWrap(font,L.component(NativeNetworkClient.error),x,y,Math.min(330,width-24),0xffff8888);g.disableScissor();if(my>=y&&my<y+20)g.renderTooltip(font,font.split(L.component(NativeNetworkClient.error),300),mx,my);}if(contentHeight>height-126){int x=width/2+Math.min(330,width-24)/2+3;g.fill(x,56,x+2,height-46,0xff444444);int top=56+(int)((height-136)*(double)scroll/Math.max(1,contentHeight-(height-126)));g.fill(x,top,x+2,top+10,0xfff0d68a);}}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll=Math.clamp(scroll-(int)(v*25),0,Math.max(0,contentHeight-(height-126)));rebuildWidgets();return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
