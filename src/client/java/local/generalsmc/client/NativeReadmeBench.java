package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Repeatable documentation capture. Runs only in the separate private test driver. */
final class NativeReadmeBench {
    static int stage,age;
    static CompletableFuture<?> work;
    static void next(int value){stage=value;age=0;}
    static void shot(Minecraft mc,String name)throws Exception{
        String file="readme-"+name+".png";
        Files.deleteIfExists(mc.gameDirectory.toPath().resolve("screenshots").resolve(file));
        Screenshot.grab(mc.gameDirectory,file,mc.getMainRenderTarget(),c->{});
    }
    static void spawn(NativeWorldView v,String type,double x,double z,int owner)throws Exception{
        var p=v.origin.nativePoint(new Vec3(x,64,z));
        v.session.send("TEST_SPAWN "+type+" "+p.x+" "+p.y+" "+p.z+" "+owner);
    }
    static boolean tick(Minecraft mc,List<String> checks)throws Exception{
        age++;
        if(age>1500)throw new IllegalStateException("README capture timeout: "+stage);
        if(stage==0){
            mc.options.guiScale().set(2);mc.resizeDisplay();
            mc.setScreen(new GeneralsGuide(new GeneralsSetupScreen(null)));next(1);
        }else if(stage==1){
            var guide=(GeneralsGuide)MenuScreens.active(mc.screen);
            if(age==20)shot(mc,"guide-"+guide.step);
            if(age==40){if(guide.step<3){NativeSetupBench.click(guide,"Далее");age=0;}
                else{NativeSetupBench.click(guide,"Готово");mc.setScreen(new MarkerFinderScreen(null));NativeReleaseUxBench.marker("marker_info",NativeReleaseBench.SUPPLY);next(2);}}
        }else if(stage==2&&MenuScreens.active(mc.screen) instanceof MarkerDetailsScreen s&&age>10){
            s.name.setValue("Village supplies");s.note.setValue("Reserve for village defence");next(3);
        }else if(stage==3){
            var s=(MarkerDetailsScreen)MenuScreens.active(mc.screen);
            if(age==15)shot(mc,"marker-0");
            if(age==30)s.note.setValue("Village defence");
            if(age==40)shot(mc,"marker-1");
            if(age==50)NativeSetupBench.click(s,"Сохранить");
            if(age==70)shot(mc,"marker-2");
            if(age==80){
                if(Boolean.getBoolean("generalsmc.readmeUiOnly")){checks.add("Captured localized guide and marker editing in a private Minecraft world");return true;}
                work=CompletableFuture.runAsync(()->{
                    var l=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);
                    l.setDayTime(6000);l.setWeatherParameters(6000,0,false,false);
                    for(int[] spot:new int[][]{{-23,-26},{16,-29},{20,-10}}){
                        int cx=spot[0],cz=spot[1];
                        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
                            l.setBlock(new BlockPos(cx+x,63,cz+z),Blocks.COBBLESTONE.defaultBlockState(),3);
                            for(int y=64;y<=67;y++){
                                boolean wall=Math.abs(x)==3||Math.abs(z)==3;
                                var block=wall?(Math.abs(x)==3&&Math.abs(z)==3?Blocks.OAK_LOG:Blocks.OAK_PLANKS):Blocks.AIR;
                                if(y==65&&((x==0&&z==-3)||(z==0&&x==3)))block=Blocks.GLASS;
                                if(x==0&&z==3&&y<=65)block=Blocks.AIR;
                                l.setBlock(new BlockPos(cx+x,y,cz+z),block.defaultBlockState(),3);
                            }
                        }
                        for(int layer=0;layer<4;layer++)for(int x=-4+layer;x<=4-layer;x++)for(int z=-4;z<=4;z++)
                            l.setBlock(new BlockPos(cx+x,68+layer,cz+z),Blocks.SPRUCE_PLANKS.defaultBlockState(),3);
                    }
                    for(int[] spot:new int[][]{{-31,-17},{-13,-35},{30,-23},{7,-34}}){
                        int x=spot[0],z=spot[1];
                        for(int y=64;y<69;y++)l.setBlock(new BlockPos(x,y,z),Blocks.OAK_LOG.defaultBlockState(),3);
                        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int y=68;y<=70;y++)
                            if(Math.abs(dx)+Math.abs(dz)<4)l.setBlock(new BlockPos(x+dx,y,z+dz),Blocks.OAK_LEAVES.defaultBlockState(),3);
                    }
                },mc.getSingleplayerServer());
                next(4);
            }
        }else if(stage==4&&work.isDone()){
            work.join();mc.setScreen(null);NativeRtsController.open("FactionAmerica",0);next(5);
        }else if(stage==5&&NativeWorldView.current!=null&&NativeWorldView.current.scene!=null&&NativeWorldView.current.state.frame()>100&&age>100){
            var v=NativeWorldView.current;v.cameraX=0;v.cameraZ=-19;v.zoom=31;
            for(int i=0;i<6;i++){
                spawn(v,"AmericaTankCrusader",-13+(i%2)*4,-24+(i/2)*6,2);
                spawn(v,"ChinaTankBattleMaster",8+(i%2)*4,-24+(i/2)*6,3);
            }
            for(int i=0;i<8;i++){
                spawn(v,"AmericaInfantryRanger",-5,-27+i*2,2);
                spawn(v,"ChinaInfantryRedguard",5,-27+i*2,3);
            }
            next(6);
        }else if(stage==6){
            var v=NativeWorldView.current;
            if(age==20){var tank=v.state.objects().stream().filter(a->a.template().equals("AmericaTankCrusader")).findFirst().orElseThrow();v.session.select(tank.id());}
            if(age>=25&&age<=180&&age%5==0)shot(mc,String.format("battle-%03d",age));
            if(age>190){checks.add("Captured English guide, marker editing and an actual opposing-army battle in a private Minecraft world");return true;}
        }
        return false;
    }
}
