package local.generalsmc.client;

import local.generalsmc.nativebridge.NativeSession;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.util.*;

/** Minecraft world picking and native orders. Only the original GUI strip is composited. */
public final class NativeWorldScreen extends NativeFrameScreen {
    
    private double startX,startY;
    
    private boolean rightDown,rightDragged,nativeLeft,middleDown,leftCancelPending,leftReleasePending,removing;
    private long lastWorldCommand,cancelAfter;
    private double releaseX,releaseY;
    private double rightX,rightY;
    private double worldPointerX,worldPointerY;
    private long lastWorldMotion;
    private long lastProjection;
    private int guiButtons;
    private float renderPartialTick;
    private net.minecraft.world.entity.Entity hoveredMob;
    private UUID pendingMob,priorityMob;private double pendingMobX,pendingMobY;private long priorityAt,pendingAt;
    private void prioritizeMob(net.minecraft.world.entity.Entity entity){long now=System.currentTimeMillis();if(!entity.getUUID().equals(priorityMob)||now-priorityAt>1000){priorityMob=entity.getUUID();priorityAt=now;NativeNetworkClient.request("mob_priority",j->j.addProperty("mob",entity.getUUID().toString()));}}
    private void replayMobClick(NativeWorldView view){
        if(pendingMob==null||view==null||view.state==null)return;var id=pendingMob.toString();var actor=view.state.objects().stream().filter(a->id.equals(a.externalKey())).findFirst().orElse(null);
        var entity=java.util.stream.StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(),false).filter(e->pendingMob.equals(e.getUUID())).findFirst().orElse(null);
        if(entity==null||!entity.isAlive()||System.currentTimeMillis()-pendingAt>3000){pendingMob=null;return;}if(actor==null)return;
        var p=view.origin.nativePoint(entity.getPosition(renderPartialTick));int x=(int)(pendingMobX*input.width/width),y=(int)(pendingMobY*input.height/height);pendingMob=null;
        try{input.send("WORLD_MOUSE 513 "+p.x+" "+p.y+" "+p.z+" "+actor.id()+" 1 "+x+" "+y);input.send("WORLD_MOUSE 514 "+p.x+" "+p.y+" "+p.z+" "+actor.id()+" 0 "+x+" "+y);}catch(IOException error){onClose();}
    }
    private final NativeGuiLayer gui;
    private final NativeGuiLayer cursor;
    private String minecraftLabel(){return L.s("В Minecraft")+" · "+GeneralsClient.nativeToggle.getTranslatedKeyMessage().getString();}
    private boolean minecraftButton(double x,double y){return x>=width-font.width(minecraftLabel())-20&&x<=width-6&&y>=6&&y<=24;}
    public NativeWorldScreen(NativeSession session){this(session,false);}
    public NativeWorldScreen(NativeSession session,boolean close){super(session,close);gui=new NativeGuiLayer(session.directory);cursor=new NativeGuiLayer(session.directory,true);}
    private boolean inPanel(double x,double y){int nx=(int)(x*input.width/width),ny=(int)(y*input.height/height);var view=NativeWorldView.current;
        return gui.opaque(nx,ny)&&view!=null&&view.state!=null&&view.state.controls()!=null&&view.state.controls().stream().anyMatch(c->nx>=c.x()&&ny>=c.y()&&nx<c.x()+c.width()&&ny<c.y()+c.height());}
    private void guiMouse(int message,double x,double y,int wheel) {
        int nx=Math.max(0,Math.min(input.width-1,(int)(x*input.width/width)));
        int ny=Math.max(0,Math.min(input.height-1,(int)(y*input.height/height)));
        try{input.mouse(message,nx,ny,guiButtons,wheel);}catch(IOException e){onClose();}
    }
    @Override void clickOriginal(int x,int y,boolean down){double sx=x*width/800.0,sy=y*height/600.0;mouseMoved(sx,sy);if(down)mouseClicked(sx,sy,0);else mouseReleased(sx,sy,0);}
    void clickCommand(String command,boolean down){var v=NativeWorldView.current;var c=v.state.controls().stream().filter(w->command.equals(w.command())).findFirst().orElseThrow();double x=(c.x()+c.width()/2.0)*width/input.width,y=(c.y()+c.height()/2.0)*height/input.height;mouseMoved(x,y);if(down)mouseClicked(x,y,0);else mouseReleased(x,y,0);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float partialTick) {
        NativeNetworkClient.pumpOrders();
        renderPartialTick=partialTick;
        gui.update();cursor.update();
        drawHealthBars(g);
        gui.draw(g,0,0,width,height);drawMobPortrait(g);
        if(GeneralsClient.noticeTicks>0){int w=Math.min(width-20,font.width(GeneralsClient.notice)+16);g.fill((width-w)/2,28,(width+w)/2,49,0xdd20242a);g.centeredText(font,GeneralsClient.notice,width/2,34,0xffffdd99);}
        var view=NativeWorldView.current;
        replayMobClick(view);
        int back=width-font.width(minecraftLabel())-20;
        g.fill(back,6,width-6,24,minecraftButton(x,y)?0xee59616d:0xcc252b33);
        g.text(font,minecraftLabel(),back+7,11,0xffffffff);
        if(leftReleasePending&&view!=null&&view.state!=null&&view.state.consumed()>=cancelAfter&&!pendingCommand()){leftReleasePending=false;leftCancelPending=false;worldMouse(0x202,releaseX,releaseY,0);}
        if(view!=null&&System.currentTimeMillis()-lastProjection>=33){lastProjection=System.currentTimeMillis();var c=minecraft.gameRenderer.mainCamera();var e=view.origin.nativePoint(c.position());var f=c.forwardVector();var l=c.leftVector();var u=c.upVector();try{input.send("HOST_VIEW "+e.x+" "+e.y+" "+e.z+" "+f.x()+" "+f.z()+" "+f.y()+" "+l.x()+" "+l.z()+" "+l.y()+" "+u.x()+" "+u.z()+" "+u.y()+" "+Math.tan(Math.toRadians(30))+" "+(double)width/height+" "+view.renderZoom/25);}catch(IOException err){onClose();}}
        if(!GeneralsClient.blocksInput()&&!Boolean.getBoolean("generalsmc.nativeBench")&&!minecraftButton(x,y)&&!inPanel(x,y)&&System.currentTimeMillis()-lastWorldMotion>=40){lastWorldMotion=System.currentTimeMillis();worldMouse(0x200,x,y,middleDown?16:rightDown?2:nativeLeft?1:0);}
        if(cursor.available()){org.lwjgl.sdl.SDLMouse.SDL_HideCursor();cursor.drawCursor(g,x,y);}
    }
    private void drawMobPortrait(GuiGraphicsExtractor g){var view=NativeWorldView.current;if(view==null||view.state==null||view.state.portrait()==null||view.state.selected()==null||view.state.selected().size()!=1)return;int selected=view.state.selected().get(0);var actor=view.state.objects().stream().filter(o->o.id()==selected&&o.externalKey()!=null&&!o.externalKey().isEmpty()).findFirst().orElse(null);if(actor==null)return;var entity=java.util.stream.StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(),false).filter(e->actor.externalKey().equals(e.getUUID().toString())).findFirst().orElse(null);if(!(entity instanceof net.minecraft.world.entity.LivingEntity living))return;var box=view.state.portrait();int left=box.x()*width/input.width,top=box.y()*height/input.height,right=(box.x()+box.width())*width/input.width,bottom=(box.y()+box.height()-36)*height/input.height;float previewHeight=living.getBbHeight();int size=Math.max(1,(int)((bottom-top)/(previewHeight*1.25)));net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(g,left,top,right,bottom,size,(previewHeight-living.getBbHeight())*.5f,(left+right)/2f,(top+bottom)/2f,living);}
    private void drawHealthBars(GuiGraphicsExtractor g){var view=NativeWorldView.current;if(view==null||view.state==null||view.state.healthBars()==null)return;
        float sx=(float)width/input.width,sy=(float)height/input.height;
        // Preserve the source's three physical pixels; rounding in scaled GUI coordinates erased the interior.
        g.pose().pushMatrix();g.pose().scale(sx,sy);
        for(var b:view.state.healthBars()){var actor=view.actor(b.id());if(actor==null||actor.health()<=0)continue;var location=view.origin.world(actor.x(),actor.y(),actor.z());if(actor.owner()!=view.state.localPlayerIndex()&&!NativeFog.visible(location.x,location.z))continue;var p=project(view.origin.world(b.x(),b.y(),b.z()));
            int x=(int)Math.round(p[0]/sx+b.dx()),y=(int)Math.round(p[1]/sy+b.dy()),w=Math.max(3,(int)Math.round(b.width())),h=Math.max(3,(int)Math.round(b.height()));int outline=(int)b.outline(),color=(int)b.color();
            g.fill(x,y,x+w,y+1,outline);g.fill(x,y+h-1,x+w,y+h,outline);g.fill(x,y,x+1,y+h,outline);g.fill(x+w-1,y,x+w,y+h,outline);
            int fill=healthFill(w,b.ratio());if(fill>0)g.fill(x+1,y+1,x+1+fill,y+h-1,color);
        }g.pose().popMatrix();
    }
    static int healthFill(int width,double ratio){return (int)Math.round((width-2)*Math.clamp(ratio,0,1));}
    public Vec3 ray(double x,double y) {
        var camera=minecraft.gameRenderer.mainCamera();double tan=Math.tan(Math.toRadians(30));
        return new Vec3(camera.forwardVector()).add(new Vec3(camera.leftVector()).scale(-(x/width*2-1)*tan*width/height)).add(new Vec3(camera.upVector()).scale((1-y/height*2)*tan)).normalize();
    }
    BlockHitResult hit(double x,double y) {
        var eye=minecraft.gameRenderer.mainCamera().position();
        return minecraft.level.clip(new ClipContext(eye,eye.add(ray(x,y).scale(200)),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,minecraft.player));
    }
    public double[] project(Vec3 p) {
        var camera=minecraft.gameRenderer.mainCamera();var delta=p.subtract(camera.position());double depth=delta.dot(new Vec3(camera.forwardVector())),tan=Math.tan(Math.toRadians(30));
        if(depth<=0)return new double[]{-10000,-10000};
        return new double[]{(1-delta.dot(new Vec3(camera.leftVector()))/depth/tan*height/width)*width/2,(1-delta.dot(new Vec3(camera.upVector()))/depth/tan)*height/2};
    }
    int actorAt(double x,double y) {
        var view=NativeWorldView.current;if(view==null||view.state==null)return 0;
        var eye=minecraft.gameRenderer.mainCamera().position();var end=eye.add(ray(x,y).scale(200));var block=hit(x,y);
        double nearest=block.getType()==HitResult.Type.MISS?200:eye.distanceTo(block.getLocation())+.05;int chosen=0;
        var direction=ray(x,y);int picked=view.pick(eye,direction,nearest);
        if(picked!=0){var box=view.bounds(picked);if(box!=null){var point=box.clip(eye,end);if(point.isPresent())nearest=Math.min(nearest,eye.distanceTo(point.get()));}}
        hoveredMob=null;for(var entity:minecraft.level.getEntities(minecraft.player,new AABB(eye,end).inflate(1),e->e instanceof net.minecraft.world.entity.LivingEntity&&e.isAlive()&&!(e instanceof net.minecraft.world.entity.player.Player)&&!(e instanceof local.generalsmc.nativebridge.NativeActorEntity))){
            if(!NativeFog.visible(entity.getX(),entity.getZ()))continue;var point=entity.getBoundingBox().move(entity.getPosition(renderPartialTick).subtract(entity.position())).inflate(.10).clip(eye,end);if(point.isPresent()&&eye.distanceTo(point.get())<nearest){int id=view.state.objects().stream().filter(o->entity.getUUID().toString().equals(o.externalKey())).mapToInt(NativeSession.Actor::id).findFirst().orElse(0);nearest=eye.distanceTo(point.get());picked=id;hoveredMob=entity;if(id==0)prioritizeMob(entity);}
        }NativeSession.Actor actor=null;for(var candidate:view.state.objects())if(candidate.id()==picked){actor=candidate;break;}if(actor!=null&&actor.owner()!=view.state.localPlayerIndex()){var p=view.origin.world(actor.x(),actor.y(),actor.z());if(!NativeFog.visible(p.x,p.z))return 0;}return picked;
    }
    private boolean worldMouse(int message,double x,double y,int buttons) {
        worldPointerX=x;worldPointerY=y;
        var view=NativeWorldView.current;if(view==null||view.state==null)return false;
        var point=hit(x,y);int id=actorAt(x,y);
        if(hoveredMob!=null&&id==0){prioritizeMob(hoveredMob);if(message==0x201){pendingMob=hoveredMob.getUUID();pendingMobX=x;pendingMobY=y;pendingAt=System.currentTimeMillis();return false;}if(message==0x200&&buttons==0)return false;}
        var actor=view.state.objects().stream().filter(o->o.id()==id).findFirst().orElse(null);
        // No ground hit means unloaded terrain/sky. Never issue a synthetic airborne movement order.
        if(point.getType()==HitResult.Type.MISS&&actor==null){
            if(message==0x201||message==0x203){GeneralsClient.notify(L.s("Местность ещё загружается. Выберите видимую землю."));return false;}
            // Releases/camera gestures still reach the original input state, even over unloaded chunks.
            try{input.mouse(message,Math.clamp((int)(x*input.width/width),0,input.width-1),Math.clamp((int)(y*input.height/height),0,input.height-1),buttons,0);}catch(IOException e){onClose();}return false;
        }
        var world=actor!=null&&point.getType()==HitResult.Type.MISS?view.origin.world(actor.x(),actor.y(),actor.z()):point.getLocation();
        double surface=local.generalsmc.nativebridge.WorldSurface.ground(minecraft.level,(int)Math.floor(world.x),(int)Math.floor(world.z));
        if(Double.isFinite(surface))world=new Vec3(world.x,surface,world.z);
        if(hoveredMob!=null)world=hoveredMob.getPosition(renderPartialTick);
        else if(point.getType()==HitResult.Type.BLOCK){
            var normal=new Vec3(point.getDirection().getStepX(),point.getDirection().getStepY(),point.getDirection().getStepZ());
            var inside=point.getLocation().subtract(normal.scale(.001));double top=local.generalsmc.nativebridge.WorldSurface.ground(minecraft.level,(int)Math.floor(inside.x),(int)Math.floor(inside.z));
            if(view.state.forceAttack())world=point.getLocation(); // A targeted block keeps its actual impact height.
            else if(Double.isFinite(top)){
                world=new Vec3(inside.x,top,inside.z);
                if(id==0&&!pendingCommand()&&normal.y==0&&!standable(world)){
                    var outside=point.getLocation().add(normal.scale(.301));double floor=local.generalsmc.nativebridge.WorldSurface.ground(minecraft.level,(int)Math.floor(outside.x),(int)Math.floor(outside.z));
                    if(Double.isFinite(floor))world=new Vec3(outside.x,floor,outside.z);
                }
            }
        }
        var p=view.origin.nativePoint(world);
        try{lastWorldCommand=input.send("WORLD_MOUSE "+message+" "+p.x+" "+p.y+" "+p.z+" "+id+" "+buttons+" "+(int)(x*input.width/width)+" "+(int)(y*input.height/height));return true;}catch(IOException e){onClose();return false;}
    }
    private boolean standable(Vec3 p){return minecraft.level.noCollision(new AABB(p.x-.3,p.y+.001,p.z-.3,p.x+.3,p.y+1.8,p.z+.3));}
    private boolean pendingCommand(){var v=NativeWorldView.current;return v!=null&&v.state!=null&&(v.state.pendingBuild()||(v.state.pendingCommand()!=null&&!v.state.pendingCommand().isEmpty()));}
    @Override public void mouseMoved(double x,double y){if(GeneralsClient.blocksInput())return;if(middleDown||rightDown){worldMouse(0x200,x,y,middleDown?16:2);return;}if(inPanel(x,y))guiMouse(0x200,x,y,0);else if(System.currentTimeMillis()-lastWorldMotion>33){lastWorldMotion=System.currentTimeMillis();worldMouse(0x200,x,y,nativeLeft?1:0);}}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(GeneralsClient.blocksInput())return true;
        if(GeneralsClient.nativeMenu.matchesMouse(new net.minecraft.client.input.MouseButtonEvent(0,0,new net.minecraft.client.input.MouseButtonInfo(CompatScreen.sdlButton(button),0)))){NativeRtsController.toggle();minecraft.gui.setScreen(new GeneralsSetupScreen(null));return true;}
        if(GeneralsClient.nativeToggle.matchesMouse(new net.minecraft.client.input.MouseButtonEvent(0,0,new net.minecraft.client.input.MouseButtonInfo(CompatScreen.sdlButton(button),0)))){NativeRtsController.toggle();return true;}
        if(button==0&&minecraftButton(x,y)){NativeRtsController.toggle();return true;}
        int scan=RtsBindings.mouseScan(button);if(scan>0){scanInput(scan,true);return true;}
        button=RtsBindings.button(button);
        if(button==0||button==1)pendingMob=null;
        if(GeneralsClient.blocksInput())return true;
        if(inPanel(x,y)){int mask=button==0?1:button==1?2:16;guiButtons|=mask;guiMouse(button==0?0x201:button==1?0x204:0x207,x,y,0);return true;}
        if(button==0){nativeLeft=worldMouse(0x201,x,y,1);return true;}
        if(button==2){middleDown=true;worldMouse(0x207,x,y,16);return true;}
        if(button==1){rightDown=true;rightX=x;rightY=y;rightDragged=false;worldMouse(0x204,x,y,2);return true;}
        return false;
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        if(GeneralsClient.blocksInput())return true;
        int scan=RtsBindings.mouseScan(button);if(scan>0){scanInput(scan,false);return true;}
        button=RtsBindings.button(button);
        if(GeneralsClient.blocksInput())return true;
        if(guiButtons!=0){int mask=button==0?1:button==1?2:16;guiButtons&=~mask;guiMouse(button==0?0x202:button==1?0x205:0x208,x,y,0);return true;}
        if(button==0&&nativeLeft){nativeLeft=false;if(leftCancelPending){leftReleasePending=true;releaseX=x;releaseY=y;}else worldMouse(0x202,x,y,0);return true;}
        if(button==2&&middleDown){middleDown=false;worldMouse(0x208,x,y,0);return true;}
        if(button==1&&rightDown){rightDown=false;if(!rightDragged&&nativeLeft&&pendingCommand())leftCancelPending=true;worldMouse(0x205,x,y,0);cancelAfter=lastWorldCommand;return true;}
        return false;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(GeneralsClient.blocksInput())return true;
        if(guiButtons!=0)guiMouse(0x200,x,y,0);else if(middleDown)worldMouse(0x200,x,y,16);else if(rightDown){rightDragged|=Math.hypot(x-rightX,y-rightY)>4;worldMouse(0x200,x,y,2);}else if(nativeLeft)worldMouse(0x200,x,y,1);return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(GeneralsClient.blocksInput())return true;if(inPanel(x,y))guiMouse(0x20a,x,y,(int)(vertical*120));else if(NativeWorldView.current!=null)NativeWorldView.current.zoom=Math.max(10,Math.min(60,NativeWorldView.current.zoom-vertical*2));return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(GeneralsClient.blocksInput())return true;if(GeneralsClient.nativeMenu!=null&&GeneralsClient.nativeMenu.matches(new net.minecraft.client.input.KeyEvent(key,scan,mods))){NativeRtsController.toggle();minecraft.gui.setScreen(new GeneralsSetupScreen(null));return true;}if(GeneralsClient.nativeToggle!=null&&GeneralsClient.nativeToggle.matches(new net.minecraft.client.input.KeyEvent(key,scan,mods))){NativeRtsController.toggle();return true;}if(key==InputConstants.KEY_F10&&(mods&InputConstants.MOD_CONTROL)!=0){NativeRtsController.stop();return true;}return super.keyPressed(key,scan,mods);}
    @Override public boolean keyReleased(int key,int scan,int mods){if(GeneralsClient.blocksInput())return true;return super.keyReleased(key,scan,mods);}
    int cursorType(){return cursor.cursorType();}
    long guiUploads(){return gui.uploads();}long guiAgeMillis(){return gui.ageMillis();}
    int cursorStep(){return cursor.cursorStep();}int cursorSteps(){return cursor.cursorSteps();}int cursorHash(){return cursor.pixelHash();}
    @Override public void onClose(){if(removing)return;if(!input.alive()){try{input.read();}catch(Exception error){NativeRtsController.failed(error);}return;}super.onClose();}
    @Override public void removed(){removing=true;if(guiButtons!=0)try{input.mouse(0x202,400,200,0,0);input.mouse(0x205,400,200,0,0);}catch(IOException ignored){}if(nativeLeft||leftReleasePending)worldMouse(0x202,worldPointerX,worldPointerY,0);if(rightDown)worldMouse(0x205,worldPointerX,worldPointerY,0);if(middleDown)worldMouse(0x208,worldPointerX,worldPointerY,0);gui.close();cursor.close();org.lwjgl.sdl.SDLMouse.SDL_ShowCursor();super.removed();}
}
