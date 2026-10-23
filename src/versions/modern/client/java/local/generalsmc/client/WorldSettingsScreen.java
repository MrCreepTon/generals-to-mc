package local.generalsmc.client;
import local.generalsmc.nativebridge.NativeNetworkServer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
/** Small groups, readable values and server-owned changes. */
public final class WorldSettingsScreen extends CompatScreen {
 private final Screen parent;private NativeNetworkServer.RulesState shown;private int category,scroll,contentHeight;
 private final List<AbstractWidget> rows=new ArrayList<>();private final List<Integer> descriptionsY=new ArrayList<>();private final List<String> descriptions=new ArrayList<>();
 WorldSettingsScreen(Screen parent){super(L.component(L.s("Правила схватки")));this.parent=parent;NativeNetworkClient.rules=null;NativeNetworkClient.request("rules_get");}
 @Override protected void init(){rows.clear();descriptions.clear();descriptionsY.clear();shown=NativeNetworkClient.rules;int w=Math.min(460,width-28),x=(width-w)/2;
  String[] tabs={L.s("Бой"),L.s("Мобы"),L.s("Мир"),L.s("ЭМИ")};for(int i=0;i<tabs.length;i++){final int n=i;var b=addRenderableWidget(MenuButton.styled(L.component(tabs[i]),v->{category=n;scroll=0;rebuildWidgets();}).bounds(x+i*(w+6)/tabs.length,48,(w-6*(tabs.length-1))/tabs.length,20).build());b.active=category!=i;}
  int y=82-scroll;
  if(shown!=null){var v=shown.values();if(category==0){
   row("blockDestruction",L.s("Разрушение блоков"),!v.blockDestruction()?L.s("Выключено"):v.forceAttackDestruction()?L.s("Только Ctrl-атака"):L.s("Любая атака"),L.s("Определяет, какие атаки могут разрушать блоки."),x,y,w,false);y+=48;
   row("fireSpread",L.s("Поджигать мир"),yes(v.fireSpread()),L.s("Огнемёты оставляют огонь на блоках."),x,y,w,false);y+=48;
   row("eliminationVictory",L.s("Победа по зданиям"),yes(v.eliminationVictory()),L.s("Армия выбывает после потери основных зданий."),x,y,w,false);y+=48;
   if(v.blockDestruction()){row("blastRadius",L.s("Радиус разрушений"),"×"+v.blastRadius(),L.s("Влияет на блоки, не на урон юнитам."),x,y,w,false);y+=48;row("blastPower",L.s("Пробитие блоков"),"×"+v.blastPower(),"",x,y,w,false);y+=30;}
  }else if(category==1){
   row("hostileMobs",L.s("Монстры атакуют армию"),yes(v.hostileMobs()),L.s("Монстры выбирают видимые юниты своей целью."),x,y,w,false);y+=48;
   row("neutralRetaliation",L.s("Нейтральные отвечают"),yes(v.neutralRetaliation()),L.s("Големы и нейтральные мобы защищаются при нападении."),x,y,w,false);y+=48;
   row("environmentDamage",L.s("Урон от среды"),yes(v.environmentDamage()),L.s("Огонь, лава и вода повреждают армию."),x,y,w,false);y+=48;
   row("mobGeneralExperience",L.s("Опыт за мобов"),yes(v.mobGeneralExperience()),L.s("Убийства мобов повышают ранг генерала."),x,y,w,false);y+=48;
  }else if(category==2){
   row("prepareStarts",L.s("Стартовые площадки"),v.prepareStarts()?L.s("Расчистить"):L.s("Только проверять"),L.s("Расчистка выровняет землю и уберёт препятствия вокруг стартов перед матчем."),x,y,w,shown.resourcesLocked());y+=60;
   row("naturalResources",L.s("Ресурсы"),v.naturalResources()?L.s("Мир + маркеры"):L.s("Только маркеры"),L.s("Выберите автоматическое размещение или свои маркеры."),x,y,w,shown.resourcesLocked());y+=60;
  }else{
   row("empVehicles",L.s("Техника Generals"),yes(v.emp().vehicles()),L.s("Штатные ЭМИ отключают технику и здания."),x,y,w,false);y+=48;
   row("empMobs",L.s("Оглушать мобов"),yes(v.emp().mobs()),L.s("Мобы перестают двигаться и атаковать до окончания ЭМИ."),x,y,w,false);y+=48;
   row("empRedstone",L.s("Подавлять редстоун"),yes(v.emp().redstone()),L.s("Сигнал временно пропадает. Механизмы остаются на месте."),x,y,w,false);y+=48;
  }}
  contentHeight=y+scroll-82;for(var r:rows)r.visible=r.getY()>=80&&r.getY()+20<=height-58;
  addRenderableWidget(MenuButton.styled(L.component(L.s("Готово")),b->onClose()).bounds(x,height-28,w,20).build());
 }
 private static String yes(boolean v){return v?L.s("Включено"):L.s("Выключено");}
 private void row(String key,String label,String value,String detail,int x,int y,int w,boolean locked){
  int valueWidth=Math.min(145,w/2);var b=addRenderableWidget(MenuButton.styled(L.component(value),v->NativeNetworkClient.request("rules_change",j->j.addProperty("key",key))).bounds(x+w-valueWidth,y,valueWidth,20).build());b.active=shown.editable()&&!locked;b.setTooltip(Tooltip.create(L.component(locked?L.s("Меняется перед началом схватки"):detail)));rows.add(b);
  descriptions.add(label+"\n"+detail);descriptionsY.add(y);
 }
 @Override public void tick(){if(shown!=NativeNetworkClient.rules)rebuildWidgets();}
 @Override public void render(GuiGraphics g,int mx,int my,float partial){MenuButton.panel(g,width,height);super.render(g,mx,my,partial);int w=Math.min(460,width-28),x=(width-w)/2;g.drawCenteredString(font,title,width/2,14,0xfff0d68a);g.drawCenteredString(font,shown==null?L.s("Загрузка…"):shown.editable()?L.s("Сохраняется сразу"):L.s("Настраивает только хозяин схватки"),width/2,30,0xffbbbbbb);
  g.enableScissor(x,78,x+w,height-55);for(int i=0;i<descriptions.size();i++){int y=descriptionsY.get(i);String[] text=descriptions.get(i).split("\n",2);g.drawString(font,font.plainSubstrByWidth(text[0],w-Math.min(145,w/2)-8),x,y+6,0xffeeeeee);if(text.length>1)g.drawWordWrap(font,L.component(text[1]),x,y+24,w,0xffaaaaaa);}g.disableScissor();
  if(!NativeNetworkClient.error.isBlank())g.drawString(font,font.plainSubstrByWidth(NativeNetworkClient.error,w),x,height-48,0xffff8888);
 }
 @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll=Math.clamp(scroll-(int)(v*30),0,Math.max(0,contentHeight-(height-140)));rebuildWidgets();return true;}
 @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
