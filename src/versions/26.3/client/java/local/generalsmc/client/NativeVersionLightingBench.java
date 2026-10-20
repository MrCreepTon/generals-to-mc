package local.generalsmc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.*;
import java.util.*;

/** Compare both windings in the very same rendered scene, without changing lighting or textures. */
final class NativeVersionLightingBench {
    static int ticks;static boolean done;static final List<double[]> samples=new ArrayList<>();
    static boolean tick(Minecraft mc,NativeWorldView view,List<String> checks)throws Exception {
        if(done)return true;
        var dir=mc.gameDirectory.toPath().resolve("screenshots");
        if(ticks++==0){
            Files.createDirectories(dir);Files.deleteIfExists(dir.resolve("version-lighting-before.png"));Files.deleteIfExists(dir.resolve("version-lighting-after.png"));
            var cc=view.state.objects().stream().filter(a->a.owner()==2&&a.structure()).findFirst().orElseThrow();
            var screen=(NativeWorldScreen)mc.gui.screen();
            for(var batch:view.scene.batches())if(batch.actor()==cc.id()&&(batch.mode()&0x800)!=0){
                var vertices=batch.vertices();
                for(int i=0;i<batch.count();i+=3){
                    int k=i*8;if(vertices[k+5]<.75||vertices[k+13]<.75||vertices[k+21]<.75)continue;
                    double x=0,y=0,z=0;for(int j=0;j<3;j++){int at=k+j*8;x+=vertices[at]/3.;y+=vertices[at+1]/3.;z+=vertices[at+2]/3.;}
                    var point=screen.project(view.origin.world(x,y,z));
                    if(point[0]>5&&point[0]<screen.width-5&&point[1]>5&&point[1]<screen.height*.65)samples.add(new double[]{point[0]/screen.width,point[1]/screen.height});
                }
            }
            OriginalWorldMaterial.CORNERS[1]=1;OriginalWorldMaterial.CORNERS[2]=2;OriginalWorldMaterial.CORNERS[3]=2;
        }
        if(ticks==6){Screenshot.grab(mc.gameDirectory,"version-lighting-before.png",mc.gameRenderer.mainRenderTarget(),1,c->{});}
        if(ticks==10){OriginalWorldMaterial.CORNERS[1]=2;OriginalWorldMaterial.CORNERS[2]=1;OriginalWorldMaterial.CORNERS[3]=1;}
        if(ticks==16){Screenshot.grab(mc.gameDirectory,"version-lighting-after.png",mc.gameRenderer.mainRenderTarget(),1,c->{});}
        if(ticks>20&&Files.exists(dir.resolve("version-lighting-after.png"))){
            double before=brightness(dir.resolve("version-lighting-before.png")),after=brightness(dir.resolve("version-lighting-after.png"));
            var report=Map.of("before",before,"after",after,"roofSamples",samples.size(),"scope","Same loaded scene, lightmap, texture and camera; only triangle winding changed");
            Files.writeString(Path.of(System.getProperty("generalsmc.evidence"),"native-version-lighting.json"),local.generalsmc.GeneralsMod.JSON.toJson(report));
            if(samples.size()<20||after<before*1.15)throw new IllegalStateException("Face-lighting comparison failed: "+report);
            checks.add("Correct outward winding restores daytime roof lighting without brightening textures or bypassing the world lightmap");done=true;return true;
        }
        return false;
    }
    static double brightness(Path path)throws Exception {
        try(var in=Files.newInputStream(path);var image=NativeImage.read(in)){
            double total=0;for(var p:samples){int x=(int)(p[0]*image.getWidth()),y=(int)(p[1]*image.getHeight()),color=image.getPixel(x,y);total+=(((color>>16)&255)+((color>>8)&255)+(color&255))/3.;}return total/Math.max(1,samples.size());
        }
    }
}
