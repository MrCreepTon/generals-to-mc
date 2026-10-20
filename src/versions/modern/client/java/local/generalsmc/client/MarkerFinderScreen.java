package local.generalsmc.client;
import local.generalsmc.nativebridge.NativeNetworkServer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
/** Server's saved marker index, sorted by distance; no scan over world blocks. */
final class MarkerFinderScreen extends CompatScreen {
    final Screen parent;NativeNetworkServer.MarkerIndex shown;int page,filter;String status="";
    MarkerFinderScreen(Screen parent){super(L.component(L.s("Найти маркеры")));this.parent=parent;NativeNetworkClient.markers=null;NativeNetworkClient.request("marker_points");}
    static String kind(String type){return switch(type){case "start"->L.s("Старт армии");case "oil"->L.s("Нефть");default->L.s("Склад");};}
    void arrived(String coordinates){status=L.s("Вы у маркера: ")+coordinates;rebuildWidgets();}
    List<NativeNetworkServer.MapPoint> points(){if(shown==null)return List.of();return shown.points().stream().filter(p->filter==0||p.kind().equals(filter==1?"start":filter==2?"supply":"oil")).sorted(Comparator.comparingDouble(this::distance)).toList();}
    double distance(NativeNetworkServer.MapPoint p){return Math.hypot(p.x()-minecraft.player.getX(),p.z()-minecraft.player.getZ());}
    @Override public void tick(){if(shown!=NativeNetworkClient.markers){shown=NativeNetworkClient.markers;rebuildWidgets();}}
    @Override protected void init(){
        shown=NativeNetworkClient.markers;int w=Math.min(460,width-24),x=(width-w)/2,rows=Math.max(1,(height-142)/26);var points=points();page=Math.clamp(page,0,Math.max(0,(points.size()-1)/rows));
        String[] filters={L.s("Все маркеры"),L.s("Старт армии"),L.s("Склад"),L.s("Нефть")};int fw=(w-12)/4;
        for(int i=0;i<4;i++){final int at=i;var b=addRenderableWidget(Button.builder(L.component(filters[i]),v->{filter=at;page=0;rebuildWidgets();}).bounds(x+i*(fw+4),38,fw,20).build());b.active=filter!=i;}
        for(int i=page*rows;i<Math.min(points.size(),(page+1)*rows);i++){
            var point=points.get(i);int y=69+(i-page*rows)*26;String coordinates=point.x()+", "+point.y()+", "+point.z();
            String label=(point.name()==null||point.name().isBlank()?kind(point.kind()):point.name())+" • "+Math.round(distance(point))+" m • "+coordinates;
            var locate=addRenderableWidget(Button.builder(L.component(font.plainSubstrByWidth(label,w-94)),b->NativeNetworkClient.request("marker_info",j->{j.addProperty("x",point.x());j.addProperty("y",point.y());j.addProperty("z",point.z());})).bounds(x,y,w-84,20).build());locate.setTooltip(Tooltip.create(L.component(label+"\n"+L.s("Открыть маркер"))));
            var teleport=addRenderableWidget(Button.builder(L.component(L.s("Перейти")),b->{NativeNetworkClient.request("marker_teleport",j->{j.addProperty("x",point.x());j.addProperty("y",point.y());j.addProperty("z",point.z());});status=L.s("Переход к маркеру…");}).bounds(x+w-80,y,80,20).build());
            teleport.active=shown.teleportAllowed();teleport.setTooltip(Tooltip.create(L.component(shown.teleportAllowed()?L.s("Телепортироваться в безопасное место рядом с маркером."):L.s("Телепортация доступна владельцу мира, оператору или в творческом режиме."))));
        }
        if(points.size()>rows){addRenderableWidget(Button.builder(L.component("←"),b->{page--;rebuildWidgets();}).bounds(x,height-62,32,20).build()).active=page>0;addRenderableWidget(Button.builder(L.component("→"),b->{page++;rebuildWidgets();}).bounds(x+w-32,height-62,32,20).build()).active=(page+1)*rows<points.size();}
        addRenderableWidget(Button.builder(L.component(L.s("Обновить")),b->{NativeNetworkClient.request("marker_points");status="";}).bounds(x+w/2-50,height-62,100,20).build());
        addRenderableWidget(Button.builder(L.component(L.s("Назад")),b->onClose()).bounds(x,height-35,w,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float d){super.render(g,mx,my,d);g.drawCenteredString(font,title,width/2,16,0xfff0d68a);String text=!NativeNetworkClient.error.isEmpty()?NativeNetworkClient.error:!status.isEmpty()?status:shown==null?L.s("Загрузка маркеров…"):points().isEmpty()?L.s("Маркеров нет. Они находятся во вкладке Generals творческого инвентаря."):L.s("Выберите маркер");int w=Math.min(460,width-24),x=(width-w)/2;g.enableScissor(x,height-90,x+w,height-66);g.drawWordWrap(font,L.component(text),x,height-89,w,NativeNetworkClient.error.isEmpty()?0xffdddddd:0xffff8888);g.disableScissor();if(my>=height-90&&my<height-66)g.setTooltipForNextFrame(font,font.split(L.component(text),w),mx,my);}
    @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
