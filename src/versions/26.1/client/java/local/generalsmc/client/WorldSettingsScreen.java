package local.generalsmc.client;
import local.generalsmc.nativebridge.NativeNetworkServer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
/** Small groups, readable values and server-owned changes. */
public final class WorldSettingsScreen extends CompatScreen {
 private final Screen parent;private NativeNetworkServer.RulesState shown;private int category,scroll,contentHeight;private boolean advanced;
 private final List<AbstractWidget> rows=new ArrayList<>();private final List<Integer> descriptionsY=new ArrayList<>();private final List<String> descriptions=new ArrayList<>();
 WorldSettingsScreen(Screen parent){super(L.component(L.s("Правила схватки")));this.parent=parent;NativeNetworkClient.rules=null;NativeNetworkClient.request("rules_get");}
 @Override protected void init(){rows.clear();descriptions.clear();descriptionsY.clear();shown=NativeNetworkClient.rules;int w=Math.min(460,width-28),x=(width-w)/2;
  if(advanced){String[] tabs={L.s("Бой"),L.s("Мобы"),L.s("Мир"),L.s("ЭМИ")};for(int i=0;i<tabs.length;i++){final int n=i;var b=addRenderableWidget(MenuButton.styled(L.component(tabs[i]),v->{category=n;scroll=0;rebuildWidgets();}).selected(category==i).bounds(x+i*(w+6)/tabs.length,48,(w-6*(tabs.length-1))/tabs.length,20).build());b.active=category!=i;}}
  int bodyTop=advanced?82:56,y=bodyTop-scroll;
  if(shown!=null){var v=shown.values();if(!advanced){
   y+=row("blockDestruction",L.s("Разрушение блоков"),!v.blockDestruction()?L.s("Выключено"):v.forceAttackDestruction()?L.s("Ctrl и супероружие"):L.s("Любая атака"),L.s("Определяет, какие атаки могут разрушать блоки."),x,y,w,false);
   y+=row("naturalResources",L.s("Ресурсы"),v.naturalResources()?L.s("Мир + маркеры"):L.s("Только маркеры"),L.s("Выберите автоматическое размещение или свои маркеры."),x,y,w,shown.resourcesLocked());
   y+=row("prepareStarts",L.s("Стартовые площадки"),v.prepareStarts()?L.s("Расчистить"):L.s("Только проверять"),L.s("Расчистка выровняет землю и уберёт препятствия вокруг стартов перед матчем."),x,y,w,shown.resourcesLocked());
   y+=row("eliminationVictory",L.s("Победа по зданиям"),yes(v.eliminationVictory()),L.s("Армия выбывает после потери основных зданий."),x,y,w,false);
  }else if(category==0){
   y+=row("blockDestruction",L.s("Разрушение блоков"),!v.blockDestruction()?L.s("Выключено"):v.forceAttackDestruction()?L.s("Ctrl и супероружие"):L.s("Любая атака"),L.s("Определяет, какие атаки могут разрушать блоки."),x,y,w,false);
   y+=row("fireSpread",L.s("Поджигать мир"),yes(v.fireSpread()),L.s("Огнемёты оставляют огонь на блоках."),x,y,w,false);
   y+=row("eliminationVictory",L.s("Победа по зданиям"),yes(v.eliminationVictory()),L.s("Армия выбывает после потери основных зданий."),x,y,w,false);
   if(v.blockDestruction()){y+=row("blastRadius",L.s("Радиус разрушений"),"×"+v.blastRadius(),L.s("Влияет на блоки, не на урон юнитам."),x,y,w,false);y+=row("blastPower",L.s("Пробитие блоков"),"×"+v.blastPower(),"",x,y,w,false);}
  }else if(category==1){
   y+=row("hostileMobs",L.s("Монстры атакуют армию"),yes(v.hostileMobs()),L.s("Монстры выбирают видимые юниты своей целью."),x,y,w,false);
   y+=row("neutralRetaliation",L.s("Нейтральные отвечают"),yes(v.neutralRetaliation()),L.s("Големы и нейтральные мобы защищаются при нападении."),x,y,w,false);
   y+=row("environmentDamage",L.s("Урон от среды"),yes(v.environmentDamage()),L.s("Огонь, лава и вода повреждают армию."),x,y,w,false);
   y+=row("mobGeneralExperience",L.s("Опыт за мобов"),yes(v.mobGeneralExperience()),L.s("Убийства мобов повышают ранг генерала."),x,y,w,false);
   y+=row("microwaveMobs",L.s("Микроволны оглушают мобов"),yes(v.options().microwaveMobs()),L.s("Микроволновое поле временно останавливает мобов без урона здоровью."),x,y,w,false);
   y+=row("ecmArrows",L.s("ECM отклоняет стрелы мобов"),yes(v.options().ecmArrows()),L.s("Движущиеся стрелы отклоняются от техники в зоне ECM."),x,y,w,false);
  }else if(category==2){
   y+=row("prepareStarts",L.s("Стартовые площадки"),v.prepareStarts()?L.s("Расчистить"):L.s("Только проверять"),L.s("Расчистка выровняет землю и уберёт препятствия вокруг стартов перед матчем."),x,y,w,shown.resourcesLocked());
   y+=row("naturalResources",L.s("Ресурсы"),v.naturalResources()?L.s("Мир + маркеры"):L.s("Только маркеры"),L.s("Выберите автоматическое размещение или свои маркеры."),x,y,w,shown.resourcesLocked());
  }else{
   y+=row("empVehicles",L.s("Техника Generals"),yes(v.emp().vehicles()),L.s("Штатные ЭМИ отключают технику и здания."),x,y,w,false);
   y+=row("empMobs",L.s("Оглушать мобов"),yes(v.emp().mobs()),L.s("Мобы перестают двигаться и атаковать до окончания ЭМИ."),x,y,w,false);
   y+=row("empRedstone",L.s("Подавлять редстоун"),yes(v.emp().redstone()),L.s("Сигнал временно пропадает. Механизмы остаются на месте."),x,y,w,false);
  }}
  contentHeight=y+scroll-bodyTop;int oldScroll=scroll;scroll=Math.clamp(scroll,0,Math.max(0,contentHeight-(height-bodyTop-58)));int shift=oldScroll-scroll;
  for(var r:rows){r.setY(r.getY()+shift);r.visible=r.getY()>=bodyTop-2&&r.getBottom()<=height-58;}
  for(int i=0;i<descriptionsY.size();i++)descriptionsY.set(i,descriptionsY.get(i)+shift);
  int half=(w-6)/2;addRenderableWidget(MenuButton.styled(L.component(advanced?L.s("Основное"):L.s("Дополнительно…")),b->{advanced=!advanced;scroll=0;rebuildWidgets();}).bounds(x,height-28,half,20).build());
  addRenderableWidget(MenuButton.styled(L.component(L.s("Назад")),b->onClose()).quiet().bounds(x+half+6,height-28,w-half-6,20).build());
 }
 private static String yes(boolean v){return v?L.s("Включено"):L.s("Выключено");}
 private int row(String key,String label,String value,String detail,int x,int y,int w,boolean locked){
  int valueWidth=Math.min(145,w/2);boolean binary=value.equals(yes(true))||value.equals(yes(false));var builder=MenuButton.styled(L.component(value),v->NativeNetworkClient.request("rules_change",j->j.addProperty("key",key)));if(binary)builder.toggle(value.equals(yes(true)));var b=addRenderableWidget(builder.bounds(x+w-valueWidth,y,valueWidth,20).build());b.active=shown.editable()&&!locked;b.setTooltip(Tooltip.create(L.component(!shown.editable()?L.s("Настраивает только хозяин схватки"):locked?L.s("Меняется перед началом схватки"):detail)));rows.add(b);
  descriptions.add(label+"\n"+detail);descriptionsY.add(y);int labelHeight=Math.max(20,font.split(L.component(label),w-valueWidth-8).size()*font.lineHeight);return labelHeight+(detail.isEmpty()?0:font.split(L.component(detail),w).size()*font.lineHeight+4)+10;
 }
 @Override public void tick(){if(shown!=NativeNetworkClient.rules)rebuildWidgets();}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){MenuButton.panel(g,width,height,460);super.extractRenderState(g,mx,my,partial);int w=Math.min(460,width-28),x=(width-w)/2;g.centeredText(font,title,width/2,14,0xfff0d68a);g.centeredText(font,shown==null?L.s("Загрузка…"):shown.editable()?L.s("Сохраняется сразу"):L.s("Настраивает только хозяин схватки"),width/2,30,0xffbbbbbb);
  g.enableScissor(x,advanced?78:52,x+w,height-55);for(int i=0;i<descriptions.size();i++){int y=descriptionsY.get(i);String[] text=descriptions.get(i).split("\n",2);int labelWidth=w-Math.min(145,w/2)-8,labelHeight=Math.max(20,font.split(L.component(text[0]),labelWidth).size()*font.lineHeight);g.textWithWordWrap(font,L.component(text[0]),x,y+6,labelWidth,0xffeeeeee);if(text.length>1)g.textWithWordWrap(font,L.component(text[1]),x,y+labelHeight+6,w,0xffaaaaaa);}g.disableScissor();
  int bodyTop=advanced?80:54,viewport=height-(advanced?140:114);if(contentHeight>viewport){int bottom=height-59,track=bottom-bodyTop,thumb=Math.max(12,track*viewport/contentHeight),sy=bodyTop+(track-thumb)*scroll/Math.max(1,contentHeight-viewport);g.fill(x+w+3,bodyTop,x+w+5,bottom,UiPaint.BORDER);g.fill(x+w+3,sy,x+w+5,sy+thumb,UiPaint.BLUE);}
  if(!NativeNetworkClient.error.isBlank())g.text(font,font.plainSubstrByWidth(NativeNetworkClient.error,w),x,height-48,0xffff8888);
 }
 @Override public boolean mouseScrolled(double x,double y,double h,double v){int next=Math.clamp(scroll-(int)(v*30),0,Math.max(0,contentHeight-(height-(advanced?140:114))));if(next!=scroll){scroll=next;rebuildWidgets();}return v!=0;}
 @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
