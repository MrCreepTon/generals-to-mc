package local.generalsmc.nativebridge;

import local.generalsmc.GeneralsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.io.IOException;

/** Local integrated-server bridge. Minecraft owns mobs/blocks; original weapons own native impacts. */
public final class WorldCombat implements AutoCloseable {
    private static volatile WorldCombat active;
    public static boolean busy(){return active!=null;}
    private final ServerLevel level;
    private final NativeDriving driving=new NativeDriving(this);
    public static void driveInput(net.minecraft.server.level.ServerPlayer player,com.google.gson.JsonObject data){var service=active;if(service!=null&&service.level==player.level())service.driving.receive(player,data);}
    net.minecraft.server.level.ServerPlayer serverPlayer(UUID id){return level.getServer().getPlayerList().getPlayer(id);}
    public boolean canControl(net.minecraft.server.level.ServerPlayer player,int id){var value=state;return !closed&&value!=null&&player.level()==level&&value.objects().stream().anyMatch(a->a.id()==id&&a.vehicle()&&!a.contained()&&a.health()>0&&NativeNetworkServer.canCommand(player,a.owner(),value.localPlayerIndex()));}
    int drivingTarget(UUID id){var entity=level.getEntity(id);return entity instanceof NativeActorEntity actor&&actor.isAlive()?actor.nativeId:entity instanceof LivingEntity living&&living.isAlive()?nativeMobId(id):0;}
    void drive(int id,boolean active,double x,double z,boolean fire,double ax,double ay,double az,int target,long lease){var aim=new Vec3((ax-originX)*WorldScale.UNITS_PER_BLOCK,(az-originZ)*WorldScale.UNITS_PER_BLOCK,(ay-originY)*WorldScale.UNITS_PER_BLOCK);send("HOST_DRIVE "+id+" "+(active?1:0)+" "+x+" "+z+" "+(fire?1:0)+" "+aim.x+" "+aim.y+" "+aim.z+" "+target+" "+lease);}

    private final NativeSession session;private final WorldEmp emp;private final WorldTechnology technology;private Boolean empVehicles;
    private final int originX,originY,originZ;
    private final ArmyChunkSubscription chunks;
    private final Map<Integer,NativeActorEntity> actors=new HashMap<>();
    private final Set<UUID> imported=new HashSet<>();
    private final Set<Integer> clearedFoundations=new HashSet<>();
    private record NativeHit(int source,long tick){}
    private final Map<UUID,NativeHit> nativeHits=new HashMap<>();
    private final Map<UUID,Long> priorityMobs=new HashMap<>();
    private UUID applyingMob;
    public static void prioritize(ServerLevel world,UUID id){var service=active;if(service!=null&&service.level==world){var entity=world.getEntity(id);if(entity instanceof LivingEntity living&&living.isAlive()&&!(living instanceof Player)&&!(living instanceof NativeActorEntity))service.priorityMobs.put(id,world.getGameTime()+100);}}
    public static void entityDied(LivingEntity entity,net.minecraft.world.damagesource.DamageSource damage){
        var service=active;if(service==null||service.level!=entity.level()||entity instanceof NativeActorEntity)return;
        var hit=service.nativeHits.remove(entity.getUUID());if(hit==null||service.level.getGameTime()-hit.tick()>100)return;
        if(!entity.getUUID().equals(service.applyingMob)&&!(damage.getEntity() instanceof NativeActorEntity)&&!(damage.getEntity()==null&&damage.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)))return;
        int points=entity instanceof Mob&&WorldRules.get(service.level).values().mobGeneralExperience()?Math.clamp(Math.round(entity.getMaxHealth()),1,2000):0;
        service.send("MOB_KILLED "+entity.getUUID()+" "+hit.source()+" "+points);
    }
    private volatile NativeSession.State state;
    private volatile boolean closed;
    private int frame=-1;
    private volatile long eventSequence;
    public long appliedCombatSequence(){return eventSequence;}
    private StringBuilder batch;
    public long outgoingMobDamage,incomingNativeDamage,acceptedNativeDamage,worldExplosions,worldIgnitions;
    public WorldCombat(NativeSession session,ServerLevel level,int x,int y,int z){this.session=session;this.level=level;emp=new WorldEmp(level);technology=new WorldTechnology(level);originX=x;originY=y;originZ=z;chunks=new ArmyChunkSubscription(level,x,z);level.getServer().execute(()->{if(!closed)active=this;});}
    public void snapshot(NativeSession.State state){this.state=state;}
    public boolean alive(){return !closed;}
    public boolean acknowledged(long ticket,NativeSession.State value){return session.acknowledged(ticket,value);}
    public static void worldTick(ServerLevel level){var service=active;if(service!=null&&service.level==level)service.tick();}
    private static boolean neutral(Entity entity){return entity instanceof NeutralMob||entity instanceof net.minecraft.world.entity.animal.IronGolem||entity.getType().getDescriptionId().equals("entity.minecraft.slime");}
    public static void entityLoaded(Entity entity,ServerLevel level){if(entity instanceof Monster monster&&!neutral(entity)){((local.generalsmc.mixin.MobGoalsAccessor)monster).generalsTargetGoals().addGoal(3,new NearestAttackableTargetGoal<>(monster,NativeActorEntity.class,10,true,false,(target,world)->WorldRules.get(level).values().hostileMobs()&&target instanceof NativeActorEntity proxy&&proxy.army()&&!proxy.isInvisible()));}}
    public long hurt(int id,float amount,boolean explosive){return hurt(id,amount,explosive,false,false);}
    public long hurt(int id,float amount,boolean explosive,boolean fire,boolean water){outgoingMobDamage++;return send("HOST_DAMAGE "+id+" "+amount+" "+(explosive?1:0)+" "+(fire?6:water?12:11));}
    private long send(String command){if(batch!=null){batch.append(command).append("\n");return 0;}try{return session.send(command);}catch(IOException e){GeneralsMod.LOG.error("World combat transport failed",e);close();return Long.MAX_VALUE;}}
    private Vec3 world(double x,double y,double z){return new Vec3(originX+x/WorldScale.UNITS_PER_BLOCK,originY+z/WorldScale.UNITS_PER_BLOCK,originZ+y/WorldScale.UNITS_PER_BLOCK);}
    private void tick(){
        if(!closed)driving.tick();
        if(!closed&&state!=null&&state.inGame())chunks.tick(state);
        technology.tick();var current=state;if(closed||current==null||!current.inGame()||current.healthUnitsPerPoint()<=0)return;if(current.frame()==frame&&(current.combatEvents()==null||current.combatEvents().stream().noneMatch(e->e.sequence()>eventSequence)))return;frame=current.frame();batch=new StringBuilder("HOST_BATCH\n");
        var retained=new HashSet<Integer>();
        for(var a:current.objects())if(a.owner()>=0&&(a.externalKey()==null||a.externalKey().isEmpty())&&a.health()>0&&a.maxHealth()>0&&!a.contained()){
            retained.add(a.id());var p=world(a.x(),a.y(),a.z());if(!level.hasChunkAt(net.minecraft.core.BlockPos.containing(p)))continue;
            if(a.structure()&&clearedFoundations.add(a.id()))clearFoundation(a,p);
            var proxy=actors.get(a.id());if(proxy==null||proxy.isRemoved()){proxy=new NativeActorEntity(NativeActorEntity.TYPE,level);actors.put(a.id(),proxy);proxy.synchronize(this,a,current,p.x,p.y,p.z);level.addFreshEntity(proxy);}else proxy.synchronize(this,a,current,p.x,p.y,p.z);
        }
        actors.entrySet().removeIf(e->{if(retained.contains(e.getKey()))return false;e.getValue().discard();return true;});
        // Source units have authoritative SAGE poses. Forward real host water currents
        // instead of letting the next pose erase vanilla's fluid push.
        for(var a:current.objects())if(a.owner()>=2&&a.health()>0&&!a.structure()&&!a.contained()&&(a.externalKey()==null||a.externalKey().isEmpty())){
            var p=world(a.x(),a.y(),a.z());var block=net.minecraft.core.BlockPos.containing(p.x,p.y-.01,p.z);if(level.getChunkSource().getChunkNow(Math.floorDiv(block.getX(),16),Math.floorDiv(block.getZ(),16))==null)continue;var fluid=level.getFluidState(block);
            if(!fluid.is(net.minecraft.tags.FluidTags.WATER)){block=block.above();fluid=level.getFluidState(block);}
            if(fluid.is(net.minecraft.tags.FluidTags.WATER)){var flow=fluid.getFlow(level,block);if(flow.horizontalDistanceSqr()>.0001){double strength=a.template().contains("Infantry")?.025:.006;send("HOST_CURRENT "+a.id()+" "+flow.x*strength*WorldScale.UNITS_PER_BLOCK+" "+flow.z*strength*WorldScale.UNITS_PER_BLOCK);}}
        }

        var rules=WorldRules.get(level).values();if(empVehicles==null||empVehicles!=rules.emp().vehicles()){send("HOST_EMP_RULES "+(rules.emp().vehicles()?1:0));empVehicles=rules.emp().vehicles();}
        for(var entity:level.getAllEntities())if(entity instanceof Mob mob&&mob.getTarget() instanceof NativeActorEntity target&&(target.isInvisible()||((neutral(mob))?!rules.neutralRetaliation():!rules.hostileMobs())))mob.setTarget(null);
        for(var entity:level.getAllEntities())if(entity instanceof Mob mob){boolean neutral=neutral(mob);boolean disabled=neutral?!rules.neutralRetaliation():mob instanceof Monster&&!rules.hostileMobs();if(disabled){if(mob.getLastHurtByMob() instanceof NativeActorEntity)mob.setLastHurtByMob(null);if(mob instanceof NeutralMob angry&&angry.getPersistentAngerTarget()!=null&&level.getEntity(angry.getPersistentAngerTarget()) instanceof NativeActorEntity){angry.setPersistentAngerTarget(null);angry.setRemainingPersistentAngerTime(0);}}}
        long applied=eventSequence;
        var events=current.combatEvents()==null?java.util.List.<NativeSession.CombatEvent>of():current.combatEvents().stream().filter(e->e.sequence()>eventSequence).sorted(java.util.Comparator.comparingInt((NativeSession.CombatEvent e)->e.target()!=null&&!e.target().isEmpty()?0:1).thenComparingLong(NativeSession.CombatEvent::sequence)).toList();
        for(var e:events){
            applied=Math.max(applied,e.sequence());if(e.damageType()==35){if(e.radius()>0)technology.microwave(world(e.x(),e.y(),e.z()),e.radius()/WorldScale.UNITS_PER_BLOCK);continue;}if(e.damageType()==31&&e.radius()>0){technology.jam(e.source(),world(e.x(),e.y(),e.z()),e.radius()/WorldScale.UNITS_PER_BLOCK);continue;}if(e.disabledFrames()>0){emp.pulse(world(e.x(),e.y(),e.z()),e.radius()/WorldScale.UNITS_PER_BLOCK,(int)Math.ceil(e.disabledFrames()*20./30));continue;}var attacker=actors.get(e.source());
            if(e.target()!=null&&!e.target().isEmpty()){
                var target=level.getEntity(UUID.fromString(e.target()));if(target instanceof LivingEntity living&&living.isAlive()){
                    if(e.damageType()==31)continue;if(e.damageType()>=32&&e.damageType()<=34){if(living instanceof Mob mob)technology.disable(mob,e.amount(),living.getMaxHealth()*current.healthUnitsPerPoint(),current.objects().stream().anyMatch(a->a.id()==e.source()&&a.template().contains("ECM")));continue;}if(e.damageType()==37||e.damageType()==20||e.damageType()==21)continue;
                    boolean neutralTarget=neutral(living);
                    boolean canRetaliate=neutralTarget?rules.neutralRetaliation():!(living instanceof Monster)||rules.hostileMobs();
                    var source=NativeDamage.source(level,e,canRetaliate?attacker:null,living);
                    var previousHit=nativeHits.put(living.getUUID(),new NativeHit(e.source(),level.getGameTime()));applyingMob=living.getUUID();float before=living.getHealth();boolean accepted;
                    try{accepted=living.hurtServer(level,source,(float)(e.amount()/current.healthUnitsPerPoint()));}finally{applyingMob=null;}
                    if(!accepted){if(previousHit==null)nativeHits.remove(living.getUUID());else nativeHits.put(living.getUUID(),previousHit);}
                    if(accepted){acceptedNativeDamage++;if(rules.neutralRetaliation()&&attacker!=null&&living instanceof Mob mob&&(neutral(mob))){mob.setLastHurtByMob(attacker);mob.setTarget(attacker);if(mob instanceof NeutralMob neutral){neutral.setPersistentAngerTarget(attacker.getUUID());neutral.startPersistentAngerTimer();}}}incomingNativeDamage++;
                    if(Boolean.getBoolean("generalsmc.nativeManualBench"))try{java.nio.file.Files.writeString(session.directory.resolve("host-damage-test.txt"),e.sequence()+" "+e.target()+" "+e.amount()+" "+current.healthUnitsPerPoint()+" "+before+" "+living.getHealth()+" "+accepted+"\n",java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);}catch(java.io.IOException exception){GeneralsMod.LOG.warn("Fixture damage trace unavailable",exception);}
                    if(e.damageType()==6||e.damageType()==29)living.igniteForSeconds(5);
                }
            }else if(e.radius()>0){
                // Original AoE already handled represented entities. Only host blocks receive this second pass.
                var impact=world(e.x(),e.y(),e.z());double originalRadius=e.radius()/WorldScale.UNITS_PER_BLOCK;
                if(e.damageType()==6||e.damageType()==29){if(rules.fireSpread())ignite(impact,originalRadius);continue;}
                if(!rules.affectsTerrain(e.forcedAttack()))continue;
                double radius=Math.min(64,Math.max(.75,originalRadius)*rules.blastRadius());
                float pressure=(float)Math.min(64,Math.min(radius+2,Math.max(radius/2,e.amount()/current.healthUnitsPerPoint()/2))*rules.blastPower());
                double penetration=Math.max(radius,pressure);
                var calculator=new ExplosionDamageCalculator(){
                    @Override public boolean shouldDamageEntity(Explosion explosion,Entity entity){return false;}
                    @Override public float getKnockbackMultiplier(Entity entity){return 0;}
                    @Override public boolean shouldBlockExplode(Explosion explosion,BlockGetter world,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState block,float power){
                        // Pressure may penetrate deeper into soil; horizontal reach and entity damage retain original AoE.
                        double dx=Math.max(Math.max(pos.getX()-impact.x,0),impact.x-pos.getX()-1),dy=Math.max(Math.max(pos.getY()-impact.y,0),impact.y-pos.getY()-1),dz=Math.max(Math.max(pos.getZ()-impact.z,0),impact.z-pos.getZ()-1);
                        return (dx*dx+dz*dz)/(radius*radius)+dy*dy/(penetration*penetration)<=1&&super.shouldBlockExplode(explosion,world,pos,block,power);
                    }
                };
                // Host block penetration is pressure-derived; original weapon damage, range and entity splash remain unchanged.
                float power=pressure;
                level.explode(attacker,null,calculator,impact,power,false,Level.ExplosionInteraction.TNT);worldExplosions++;
            }
        }
        eventSequence=applied;
        if(eventSequence>0)send("COMBAT_ACK "+eventSequence);
        if(level.getGameTime()%20==0){nativeHits.entrySet().removeIf(e->level.getGameTime()-e.getValue().tick()>100);priorityMobs.entrySet().removeIf(e->e.getValue()<level.getGameTime());}
        var candidates=new ArrayList<LivingEntity>();for(var entity:level.getAllEntities())if(entity instanceof LivingEntity living&&!(entity instanceof Player)&&!(entity instanceof NativeActorEntity)&&living.isAlive())candidates.add(living);
        candidates.sort(Comparator.comparingInt((LivingEntity e)->priorityMobs.containsKey(e.getUUID())?0:imported.contains(e.getUUID())?1:2).thenComparingInt(Entity::getId));
        var seen=new HashSet<UUID>();int count=0;
        for(var living:candidates){if(count>=256)break;var entity=living;
            double x=(entity.getX()-originX)*WorldScale.UNITS_PER_BLOCK,y=(entity.getZ()-originZ)*WorldScale.UNITS_PER_BLOCK,z=(entity.getY()-originY)*WorldScale.UNITS_PER_BLOCK;
            if(!current.pagedWorld()&&(x<0||y<0||x>=current.terrainExtentX()*4||y>=current.terrainExtentY()*4))continue;
            seen.add(entity.getUUID());count++;send("MOB_SYNC "+entity.getUUID()+" "+x+" "+y+" "+z+" "+living.getHealth()+" "+living.getMaxHealth()+" "+(entity.getBbWidth()*WorldScale.UNITS_PER_BLOCK)+" "+(entity.getBbHeight()*WorldScale.UNITS_PER_BLOCK)+" "+(entity instanceof net.minecraft.world.entity.monster.Enemy&&!neutral(entity)||entity instanceof Mob m&&m.getTarget() instanceof NativeActorEntity?1:0)+" "+(entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss||entity instanceof net.minecraft.world.entity.FlyingMob||entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon||entity instanceof net.minecraft.world.entity.ambient.Bat?1:0)+" "+GeneralsMod.JSON.toJson(entity.getDisplayName().getString()));
        }
        for(var uuid:imported)if(!seen.contains(uuid))send("MOB_DROP "+uuid);imported.clear();imported.addAll(seen);
        String commands=batch.toString();batch=null;if(commands.length()>11){
            // Long translated mob names and crowded chunks must not exceed the native IPC limit.
            var part=new StringBuilder("HOST_BATCH\n");int lines=0;for(String line:commands.substring(11).split("\n")){if(part.length()+line.length()+1>60000||lines>=400){send(part.toString());part=new StringBuilder("HOST_BATCH\n");lines=0;}part.append(line).append('\n');lines++;}if(part.length()>11)send(part.toString());
        }
    }
    private void clearFoundation(NativeSession.Actor actor,Vec3 center){
        double major=actor.geometryMajor()/WorldScale.UNITS_PER_BLOCK,minor=actor.geometryMinor()/WorldScale.UNITS_PER_BLOCK;
        double cos=Math.cos(actor.orientation()),sin=Math.sin(actor.orientation());int reach=(int)Math.ceil(Math.hypot(major,minor));
        for(int x=(int)Math.floor(center.x)-reach;x<=center.x+reach;x++)for(int z=(int)Math.floor(center.z)-reach;z<=center.z+reach;z++){
            double dx=x+.5-center.x,dz=z+.5-center.z;
            if(Math.abs(dx*cos+dz*sin)>major||Math.abs(-dx*sin+dz*cos)>minor)continue;
            for(int y=(int)Math.floor(center.y);y<Math.min(level.getMaxY(),center.y+2);y++){
                var p=new net.minecraft.core.BlockPos(x,y,z);var block=level.getBlockState(p);
                if(!block.isAir()&&block.canBeReplaced()&&block.getFluidState().isEmpty())level.setBlock(p,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            }
        }
    }
    private void ignite(Vec3 impact,double radius){
        int reach=(int)Math.ceil(Math.min(32,radius));
        for(int x=(int)Math.floor(impact.x)-reach;x<=impact.x+reach;x++)for(int z=(int)Math.floor(impact.z)-reach;z<=impact.z+reach;z++){
            if(Math.hypot(x+.5-impact.x,z+.5-impact.z)>radius+.5)continue;
            double floor=WorldSurface.ground(level,x,z);if(!Double.isFinite(floor)||Math.abs(floor-impact.y)>3)continue;
            var p=new net.minecraft.core.BlockPos(x,(int)Math.ceil(floor),z);var block=level.getBlockState(p);
            if(block.getFluidState().isEmpty()&&block.canBeReplaced()){
                var fire=net.minecraft.world.level.block.BaseFireBlock.getState(level,p);
                if(fire.canSurvive(level,p)){level.setBlock(p,fire,3);worldIgnitions++;}
            }
        }
    }
    public int nativeMobId(UUID uuid){var s=state;if(s==null)return 0;String key=uuid.toString();for(var a:s.objects())if(key.equals(a.externalKey()))return a.id();return 0;}
    public NativeActorEntity actor(int id){return actors.get(id);}
    @Override public void close(){if(closed)return;closed=true;level.getServer().execute(()->{emp.close();technology.close();chunks.close();actors.values().forEach(Entity::discard);actors.clear();if(active==this)active=null;});}
}
