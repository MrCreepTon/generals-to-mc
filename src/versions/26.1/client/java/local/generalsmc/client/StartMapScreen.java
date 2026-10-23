package local.generalsmc.client;
import local.generalsmc.nativebridge.WorldSiteStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;
/** Loaded-terrain overview. Panning never requests or generates world chunks. */
final class StartMapScreen extends CompatScreen {
    final Screen parent;final List<WorldSiteStore.Start> starts;final java.util.function.IntConsumer choose;
    int selected;double cx,cz,span;int left,top,side;int[] terrain;long terrainAt;
    final boolean resources;boolean initialized,touched,dragging;double downX,downY,dragDistance;
    local.generalsmc.nativebridge.NativeNetworkServer.MapPoint located;
    static StartMapScreen locate(Screen parent,local.generalsmc.nativebridge.NativeNetworkServer.MapPoint point){var map=new StartMapScreen(parent,List.of(),-1,s->{},true);map.located=point;map.initialized=map.touched=true;map.span=48;map.center(point.x(),point.z());return map;}
    List<local.generalsmc.nativebridge.NativeNetworkServer.MapPoint> shownPoints;
    @Override public void tick(){if(resources&&shownPoints!=NativeNetworkClient.mapPoints){shownPoints=NativeNetworkClient.mapPoints;if(!touched)fit();}}
    StartMapScreen(Screen parent,List<WorldSiteStore.Start> starts,int selected,java.util.function.IntConsumer choose){this(parent,starts,selected,choose,false);}
    StartMapScreen(Screen parent,List<WorldSiteStore.Start> starts,int selected,java.util.function.IntConsumer choose,boolean resources){
        super(L.component(resources?L.s("Карта складов и нефти"):L.s("Стартовые позиции на карте")));this.parent=parent;this.starts=starts;this.selected=selected;this.choose=choose;this.resources=resources;
        if(resources){NativeNetworkClient.mapPoints=List.of();NativeNetworkClient.request("map_points");}
    }
    void center(double x,double z){cx=x;cz=z;terrain=null;}
    void fit(){
        double minX=minecraft.player.getX(),maxX=minX,minZ=minecraft.player.getZ(),maxZ=minZ;
        if(resources){for(var p:NativeNetworkClient.mapPoints){minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z());}}
        else for(var p:starts){minX=Math.min(minX,p.pos().getX());maxX=Math.max(maxX,p.pos().getX());minZ=Math.min(minZ,p.pos().getZ());maxZ=Math.max(maxZ,p.pos().getZ());}
        span=Math.max(48,Math.max(maxX-minX,maxZ-minZ)/2+20);center((minX+maxX)/2,(minZ+maxZ)/2);
    }
    @Override protected void init(){
        shownPoints=NativeNetworkClient.mapPoints;if(!initialized){fit();initialized=true;}
        terrain=null;side=Math.max(40,Math.min(width-30,height-148));left=(width-side)/2;top=46;
        int x=width/2-155,y=height-83;
        addRenderableWidget(MenuButton.styled(L.component(L.s("Моя позиция")),b->{touched=true;center(minecraft.player.getX(),minecraft.player.getZ());}).bounds(x,y,130,20).build());
        addRenderableWidget(MenuButton.styled(L.component(L.s("Все точки")),b->{touched=true;fit();}).bounds(x+134,y,116,20).build());
        addRenderableWidget(MenuButton.styled(L.component("−"),b->zoom(1.5,width/2.,top+side/2.)).bounds(x+254,y,26,20).build());
        addRenderableWidget(MenuButton.styled(L.component("+"),b->zoom(1/1.5,width/2.,top+side/2.)).bounds(x+284,y,26,20).build());
        if(!resources)addRenderableWidget(MenuButton.styled(L.component(L.s("Автовыбор стартовой позиции")),b->{choose.accept(-1);onClose();}).bounds(x,height-59,310,20).build());
        addRenderableWidget(MenuButton.styled(L.component(L.s("Назад")),b->onClose()).bounds(x,height-33,310,20).build());
    }
    int px(double x){return left+(int)((x-cx+span)/(2*span)*side);}int pz(double z){return top+(int)((z-cz+span)/(2*span)*side);}
    boolean inside(double x,double y){return x>=left&&x<left+side&&y>=top&&y<top+side;}
    void zoom(double factor,double x,double y){double before=span;span=Math.clamp(span*factor,12,30000000);cx+=(x-left-side/2.)*2*(before-span)/side;cz+=(y-top-side/2.)*2*(before-span)/side;touched=true;terrain=null;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){
        MenuButton.panel(g,width,height);super.extractRenderState(g,mx,my,d);g.centeredText(font,title,width/2,12,0xfff0d68a);
        g.centeredText(font,L.s("Тяните карту мышью • колесо — масштаб"),width/2,28,0xffcccccc);
        drawMap(g,mx,my,true);
        g.centeredText(font,L.s("Позиция: ")+minecraft.player.getBlockX()+", "+minecraft.player.getBlockY()+", "+minecraft.player.getBlockZ(),width/2,height-97,0xffdddddd);
        if(resources)g.centeredText(font,L.s("Только маркеры: зелёный — склады • золотой — нефть"),width/2,height-56,0xffdddddd);
    }
    void drawMap(GuiGraphicsExtractor g,int mx,int my,boolean tooltips){
        g.fill(left-2,top-2,left+side+2,top+side+2,MenuButton.MODERN?0xff36758a:0xffb79857);g.fill(left,top,left+side,top+side,0xff20272d);
        if(terrain==null||System.currentTimeMillis()-terrainAt>2000){terrainAt=System.currentTimeMillis();terrain=new int[32*32];for(int x=0;x<32;x++)for(int z=0;z<32;z++){
            int wx=(int)Math.floor(cx-span+2*span*(x+.5)/32),wz=(int)Math.floor(cz-span+2*span*(z+.5)/32);
            var chunk=minecraft.level.getChunkSource().getChunk(wx>>4,wz>>4,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);
            if(chunk==null){terrain[x*32+z]=0xff262b30;continue;}int h=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,wx&15,wz&15);var p=new BlockPos(wx,h,wz);terrain[x*32+z]=0xff000000|chunk.getBlockState(p).getMapColor(minecraft.level,p).col;
        }}
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)g.fill(left+x*side/32,top+z*side/32,left+(x+1)*side/32,top+(z+1)*side/32,terrain[x*32+z]);
        String hover=null;g.enableScissor(left,top,left+side,top+side);
        if(resources)for(var p:NativeNetworkClient.mapPoints){int x=px(p.x()),y=pz(p.z());if(!inside(x,y))continue;dot(g,x,y,p.kind().equals("oil")?0xffffbc42:0xff62e179);if(Math.hypot(mx-x,my-y)<8)hover=(p.name()!=null&&!p.name().isBlank()?p.name():p.kind().equals("oil")?L.s("Нефть"):L.s("Склад"))+" • "+p.x()+", "+p.y()+", "+p.z()+(p.note()!=null&&!p.note().isBlank()?"\n"+p.note():"");}
        else for(var s:starts){int x=px(s.pos().getX()),y=pz(s.pos().getZ());if(!inside(x,y))continue;dot(g,x,y,s.slot()==selected?0xffffff55:s.difficulty()<0?0xff55ff88:0xffff9955);g.text(font,Integer.toString(s.slot()+1),x+6,y-4,0xffffffff);if(Math.hypot(mx-x,my-y)<8)hover=(s.difficulty()<0?L.s("Игрок"):"NPC")+" • "+NativeLobbyScreen.position(s);}
        if(located!=null){int x=px(located.x()),y=pz(located.z());g.fill(x-7,y-1,x+8,y+2,0xffffffff);g.fill(x-1,y-7,x+2,y+8,0xffffffff);g.centeredText(font,MarkerFinderScreen.kind(located.kind()),x,y+10,0xffffffff);}
        int ux=Math.clamp(px(minecraft.player.getX()),left+7,left+side-8),uy=Math.clamp(pz(minecraft.player.getZ()),top+10,top+side-8);
        g.fill(ux-4,uy-4,ux+5,uy+5,0xff111111);g.fill(ux-2,uy-2,ux+3,uy+3,0xff55ddff);
        double yaw=Math.toRadians(minecraft.player.getYRot());for(int i=3;i<8;i++){int x=ux-(int)(Math.sin(yaw)*i),y=uy+(int)(Math.cos(yaw)*i);g.fill(x-1,y-1,x+2,y+2,0xff55ddff);}
        g.centeredText(font,L.s("Вы"),ux,uy-13,0xffaaffff);g.text(font,"N ↑",left+4,top+4,0xffffffff);g.disableScissor();
        if(tooltips&&hover!=null&&!dragging&&inside(mx,my))g.setTooltipForNextFrame(font,L.component(hover),mx,my);
    }
    private static void dot(GuiGraphicsExtractor g,int x,int y,int color){g.fill(x-4,y-4,x+5,y+5,0xff111111);g.fill(x-3,y-3,x+4,y+4,color);}
    @Override public boolean mouseClicked(double x,double y,int b){if(b==0&&inside(x,y)){dragging=true;downX=x;downY=y;dragDistance=0;return true;}return super.mouseClicked(x,y,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){if(dragging&&b==0){dragDistance=Math.max(dragDistance,Math.hypot(x-downX,y-downY));if(dragDistance>3){touched=true;center(cx-dx*2*span/side,cz-dy*2*span/side);}return true;}return super.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int b){if(dragging&&b==0){dragging=false;if(dragDistance<=3&&!resources&&inside(x,y))for(var s:starts)if(Math.hypot(x-px(s.pos().getX()),y-pz(s.pos().getZ()))<8){if(s.difficulty()<0||s.slot()==selected){choose.accept(s.slot());onClose();}break;}return true;}return super.mouseReleased(x,y,b);}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){if(inside(x,y)){zoom(Math.pow(1.25,-v),x,y);return true;}return super.mouseScrolled(x,y,h,v);}
    @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
