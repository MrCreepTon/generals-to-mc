package local.generalsmc.nativebridge;

import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.*;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;

/** Vanilla combat/navigation presence; the original engine owns its pose, rules and visible mesh. */
public final class NativeActorEntity extends PathfinderMob {
    private static final EntityDataAccessor<Float> WIDTH=SynchedEntityData.defineId(NativeActorEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DEPTH=SynchedEntityData.defineId(NativeActorEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BASE_WIDTH=SynchedEntityData.defineId(NativeActorEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BASE_DEPTH=SynchedEntityData.defineId(NativeActorEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ANGLE=SynchedEntityData.defineId(NativeActorEntity.class,EntityDataSerializers.FLOAT);
    int footprintKey(){return java.util.Objects.hash(entityData.get(BASE_WIDTH),entityData.get(BASE_DEPTH),entityData.get(ANGLE));}
    net.minecraft.world.phys.shapes.VoxelShape structureShape(){return BuildingFootprint.shape(position(),entityData.get(BASE_WIDTH)/2.,entityData.get(BASE_DEPTH)/2.,entityData.get(ANGLE),entityData.get(HEIGHT));}
    private static final EntityDataAccessor<Float> HEIGHT=SynchedEntityData.defineId(NativeActorEntity.class,EntityDataSerializers.FLOAT);
    public static final ResourceKey<EntityType<?>> KEY=ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath("generalsmc","native_actor"));
    public static final EntityType<NativeActorEntity> TYPE=Registry.register(BuiltInRegistries.ENTITY_TYPE,KEY.identifier(),EntityType.Builder.of(NativeActorEntity::new,MobCategory.MISC).sized(.6f,1.8f).noLootTable().clientTrackingRange(12).build(KEY));
    public int nativeId;
    private static final EntityDataAccessor<Integer> NATIVE_ID=SynchedEntityData.defineId(NativeActorEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DRIVABLE=SynchedEntityData.defineId(NativeActorEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> ARROWS=SynchedEntityData.defineId(NativeActorEntity.class,EntityDataSerializers.STRING);
    public String stuckArrows(){return entityData.get(ARROWS);}
    public void stickArrow(net.minecraft.world.phys.Vec3 point,net.minecraft.world.phys.Vec3 velocity){
        if(level().isClientSide()||velocity.lengthSqr()<1e-8)return;var offset=point.subtract(position());double yaw=entityData.get(ANGLE),c=Math.cos(yaw),s=Math.sin(yaw);var d=velocity.normalize();
        String value=(offset.x*c+offset.z*s)+","+offset.y+","+(-offset.x*s+offset.z*c)+","+(d.x*c+d.z*s)+","+d.y+","+(-d.x*s+d.z*c);String old=entityData.get(ARROWS);var entries=new java.util.ArrayList<>(java.util.Arrays.asList(old.isEmpty()?new String[0]:old.split(";")));entries.add(value);while(entries.size()>16)entries.removeFirst();entityData.set(ARROWS,String.join(";",entries));
    }
    public boolean structure(){return entityData.get(STRUCTURE);}
    public boolean drivable(){return entityData.get(DRIVABLE);}
    @Override protected net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){
        if(!drivable())return super.mobInteract(player,hand);
        if(player instanceof net.minecraft.server.level.ServerPlayer server){if(combat!=null&&isAlive()&&getPassengers().isEmpty()&&combat.canControl(server,nativeId)&&player.distanceToSqr(this)<64)player.startRiding(this);else server.sendSystemMessage(net.minecraft.network.chat.Component.translatable("generalsmc.vehicle.denied"),true);}
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
    @Override protected boolean canAddPassenger(Entity passenger){return getPassengers().isEmpty()&&passenger instanceof net.minecraft.world.entity.player.Player&&drivable();}

    private boolean army;
    private static final EntityDataAccessor<Boolean> STRUCTURE=SynchedEntityData.defineId(NativeActorEntity.class,EntityDataSerializers.BOOLEAN);
    public boolean army(){return army;}
    public double hazardRadius(){return Math.hypot(entityData.get(BASE_WIDTH),entityData.get(BASE_DEPTH))*.5;}
    private WorldCombat combat;
    private long pendingDamage;
    public static void register(){FabricDefaultAttributeRegistry.register(TYPE,Mob.createMobAttributes());}
    public NativeActorEntity(EntityType<? extends PathfinderMob> type,Level level){super(type,level);setNoAi(true);setNoGravity(true);setPersistenceRequired();}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(WIDTH,.6f);builder.define(DEPTH,.6f);builder.define(HEIGHT,1.8f);builder.define(STRUCTURE,false);builder.define(BASE_WIDTH,.6f);builder.define(BASE_DEPTH,.6f);builder.define(ANGLE,0f);builder.define(NATIVE_ID,0);builder.define(DRIVABLE,false);builder.define(ARROWS,"");}
    @Override protected net.minecraft.world.phys.AABB makeBoundingBox(net.minecraft.world.phys.Vec3 position){if(entityData==null)return super.makeBoundingBox(position);double x=entityData.get(WIDTH)/2.0,z=entityData.get(DEPTH)/2.0;return new net.minecraft.world.phys.AABB(position.x-x,position.y,position.z-z,position.x+x,position.y+entityData.get(HEIGHT),position.z+z);}
    @Override protected EntityDimensions getDefaultDimensions(Pose pose){return entityData==null?super.getDefaultDimensions(pose):EntityDimensions.scalable(entityData.get(WIDTH),entityData.get(HEIGHT));}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> field){super.onSyncedDataUpdated(field);if(field==NATIVE_ID)nativeId=entityData.get(NATIVE_ID);if(field==STRUCTURE)blocksBuilding=!entityData.get(STRUCTURE);if(field==WIDTH||field==DEPTH||field==HEIGHT){refreshDimensions();setBoundingBox(makeBoundingBox());}if(field==STRUCTURE||field==WIDTH||field==DEPTH||field==HEIGHT||field==BASE_WIDTH||field==BASE_DEPTH||field==ANGLE)StructureCollisions.update(this);}
    @Override public void setPos(double x,double y,double z){super.setPos(x,y,z);if(entityData!=null)StructureCollisions.update(this);}
    @Override public void remove(RemovalReason reason){StructureCollisions.remove(this);super.remove(reason);}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public boolean isPushable(){return false;}
    // LivingEntity still actively pushes its neighbours when isPushable is false.
    // A broad, invisible base must not shove the commander or apply mob cramming.
    // Solid building movement collisions remain in StructureCollisions.
    @Override protected void pushEntities(){}
    // This invisible collision body has no Minecraft mesh. Its vanilla fire quads
    // scale to the proxy AABB (including whole bases), not to the source model.
    // Keep actual ignition and fire damage; the original renderer owns visible FX.
    @Override public boolean displayFireAnimation(){return false;}
    public boolean canBeCollidedWith(){return isAlive();}
    @Override public boolean canBeCollidedWith(Entity other){return canBeCollidedWith()&&!other.isPassengerOfSameVehicle(this);}
    @Override public boolean canCollideWith(Entity other){return canBeCollidedWith()&&other!=this&&!other.isPassengerOfSameVehicle(this);}
    @Override public boolean canBeLeashed(){return false;}
    void synchronize(WorldCombat service,NativeSession.Actor actor,NativeSession.State state,double x,double y,double z){
        entityData.set(STRUCTURE,actor.structure());combat=service;nativeId=actor.id();entityData.set(NATIVE_ID,nativeId);entityData.set(DRIVABLE,actor.vehicle()&&!actor.structure()&&!actor.contained());army=actor.owner()>=2;setInvisible(actor.cloaked());setPos(x,y,z);setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        entityData.set(BASE_WIDTH,(float)(actor.geometryMajor()*2/WorldScale.UNITS_PER_BLOCK));entityData.set(BASE_DEPTH,(float)(actor.geometryMinor()*2/WorldScale.UNITS_PER_BLOCK));entityData.set(ANGLE,(float)actor.orientation());
        double c=Math.abs(Math.cos(actor.orientation())),s=Math.abs(Math.sin(actor.orientation()));
        float width=(float)Math.max(.1,(c*actor.geometryMajor()+s*actor.geometryMinor())*2/WorldScale.UNITS_PER_BLOCK),depth=(float)Math.max(.1,(s*actor.geometryMajor()+c*actor.geometryMinor())*2/WorldScale.UNITS_PER_BLOCK),height=(float)Math.max(.1,actor.geometryHeight()/WorldScale.UNITS_PER_BLOCK);
        if(actor.structure())height=Math.max(2.1f,height);
        if(entityData.get(WIDTH)!=width||entityData.get(DEPTH)!=depth||entityData.get(HEIGHT)!=height){entityData.set(WIDTH,width);entityData.set(DEPTH,depth);entityData.set(HEIGHT,height);setBoundingBox(makeBoundingBox());StructureCollisions.update(this);}
        var max=getAttribute(Attributes.MAX_HEALTH);double maxHealth=Math.max(1,actor.maxHealth()/state.healthUnitsPerPoint());if(max.getBaseValue()!=maxHealth)max.setBaseValue(maxHealth);
        if(service.acknowledged(pendingDamage,state))setHealth((float)Math.max(0,actor.health()/state.healthUnitsPerPoint()));
    }
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){
        if(combat==null||!combat.alive())return false;
        // This body follows source poses and broad collision bounds; vanilla wall,
        // crowd and fall checks do not describe its actual native locomotion.
        if(source.is(net.minecraft.world.damagesource.DamageTypes.FALL)||source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)||source.is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL)||source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING))return false;
        if(!WorldRules.get(level).values().environmentDamage()&&(source.is(DamageTypeTags.IS_FIRE)||source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)))return false;
        float before=getHealth();boolean accepted=super.hurtServer(level,source,amount);
        if(accepted&&before>getHealth())pendingDamage=combat.hurt(nativeId,before-getHealth(),source.is(DamageTypeTags.IS_EXPLOSION),source.is(DamageTypeTags.IS_FIRE),source.is(net.minecraft.world.damagesource.DamageTypes.DROWN));
        return accepted;
    }
}
