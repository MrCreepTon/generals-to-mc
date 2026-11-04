package local.generalsmc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.*;
import java.util.*;

/** Frame pixels must contain the original roof, rather than the ground behind culled faces. */
final class NativeVersionLightingBench {
    static int ticks;static boolean done;static volatile boolean beforeWritten,afterWritten;static NativeWorldView.Scene saved;
    static final List<double[]> samples=new ArrayList<>();
    static void field(NativeWorldView v,String name,Object value)throws Exception{var f=NativeWorldView.class.getDeclaredField(name);f.setAccessible(true);f.set(v,value);}
    static boolean tick(Minecraft mc,NativeWorldView view,List<String> checks)throws Exception {
        if(done)return true;var dir=mc.gameDirectory.toPath().resolve("screenshots");
        if(ticks++==0){
            Files.createDirectories(dir);Files.deleteIfExists(dir.resolve("version-lighting-before.png"));Files.deleteIfExists(dir.resolve("version-lighting-after.png"));
            if(!Arrays.equals(OriginalWorldMaterial.CORNERS,new int[]{0,2,1,1}))throw new IllegalStateException("Outward winding was lost after native Y/Z conversion");
            var cc=view.state.objects().stream().filter(a->a.owner()==2&&a.structure()).findFirst().orElseThrow();var screen=(NativeWorldScreen)MenuScreens.active(mc.gui.screen());
            if(view.scene.batches().stream().noneMatch(b->b.actor()==cc.id()&&(b.mode()&0x80000)!=0))throw new IllegalStateException("Original face culling was lost");
            for(var batch:view.scene.batches())if(batch.actor()==cc.id()&&(batch.mode()&0x800)!=0){var vertices=batch.vertices();for(int i=0;i<batch.count();i+=3){
                int k=i*8;if(vertices[k+5]<.75||vertices[k+13]<.75||vertices[k+21]<.75)continue;
                double x=0,y=0,z=0;for(int j=0;j<3;j++){int at=k+j*8;x+=vertices[at]/3.;y+=vertices[at+1]/3.;z+=vertices[at+2]/3.;}
                var point=screen.project(view.origin.world(x,y,z));if(point[0]>5&&point[0]<screen.width-5&&point[1]>5&&point[1]<screen.height*.65)samples.add(new double[]{point[0]/screen.width,point[1]/screen.height});
            }}
            // Hold just this private renderer snapshot while the engine keeps running.
            field(view,"pending",null);field(view,"nextRead",Long.MAX_VALUE);saved=view.scene;
            view.scene=new NativeWorldView.Scene(saved.frame(),saved.batches().stream().filter(b->b.actor()!=cc.id()).toList());
        }
        if(ticks==6)Screenshot.grab(mc.gameDirectory,"version-lighting-before.png",mc.gameRenderer.mainRenderTarget(),1,c->beforeWritten=true);
        if(ticks==10)view.scene=saved;
        if(ticks==16)Screenshot.grab(mc.gameDirectory,"version-lighting-after.png",mc.gameRenderer.mainRenderTarget(),1,c->afterWritten=true);
        if(ticks>200&&(!beforeWritten||!afterWritten))throw new IllegalStateException("Screenshot writer did not complete");
        if(ticks>20&&beforeWritten&&afterWritten){
            field(view,"nextRead",0L);double difference=0;int changed=0;
            try(var a=Files.newInputStream(dir.resolve("version-lighting-before.png"));var before=NativeImage.read(a);var b=Files.newInputStream(dir.resolve("version-lighting-after.png"));var after=NativeImage.read(b)){
                for(var p:samples){int x=(int)(p[0]*before.getWidth()),y=(int)(p[1]*before.getHeight()),c=before.getPixel(x,y),d=after.getPixel(x,y);double delta=(Math.abs(((c>>16)&255)-((d>>16)&255))+Math.abs(((c>>8)&255)-((d>>8)&255))+Math.abs((c&255)-(d&255)))/3.;difference+=delta;if(delta>8)changed++;}
            }
            double average=difference/Math.max(1,samples.size()),coverage=(double)changed/Math.max(1,samples.size());
            var report=Map.of("meanPixelDifference",average,"changedRoofFraction",coverage,"roofSamples",samples.size(),"scope","Same world, lighting, camera and textures; original structure hidden for the reference frame, then restored with original face culling");
            Files.writeString(Path.of(System.getProperty("generalsmc.evidence"),"native-version-lighting.json"),local.generalsmc.GeneralsMod.JSON.toJson(report));
            if(samples.size()<20||average<10||coverage<.6)throw new IllegalStateException("Original roof faces are missing: "+report);
            checks.add("Original outward roof faces remain visible with source culling after Y/Z conversion; actual frame pixels differ from the unobstructed ground");done=true;return true;
        }return false;
    }
}
