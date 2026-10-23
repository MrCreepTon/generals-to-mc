package local.generalsmc.nativebridge;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** World-owned settings: every commander uses the server's choices, never a client preference. */
public final class WorldRules extends SavedData {
    public record Emp(boolean vehicles,boolean mobs,boolean redstone){public static Emp defaults(){return new Emp(true,true,true);}}
    public record Values(boolean blockDestruction,boolean fireSpread,boolean hostileMobs,boolean neutralRetaliation,boolean environmentDamage,boolean naturalResources,int blastRadius,int blastPower,boolean mobGeneralExperience,boolean eliminationVictory,boolean forceAttackDestruction,boolean prepareStarts,Emp emp){
        public Values(boolean a,boolean b,boolean c,boolean d,boolean e,boolean f,int r,int p,boolean xp,boolean victory,boolean force,boolean prepare){this(a,b,c,d,e,f,r,p,xp,victory,force,prepare,Emp.defaults());}
        public Values(boolean a,boolean b,boolean c,boolean d,boolean e,boolean f,int radius,int power,boolean experience,boolean victory){this(a,b,c,d,e,f,radius,power,experience,victory,true,false);}
        public boolean affectsTerrain(boolean forced){return blockDestruction&&(!forceAttackDestruction||forced);}
        public Values(boolean a,boolean b,boolean c,boolean d,boolean e,boolean f,int radius,int power){this(a,b,c,d,e,f,radius,power,true,true);}
        public Values(boolean a,boolean b,boolean c,boolean d,boolean e,boolean f){this(a,b,c,d,e,f,2,1);}
        public static Values defaults(){return new Values(true,true,true,true,true,true);}
        public Values toggle(String key){return switch(key){
            case "blockDestruction"->new Values(!blockDestruction||forceAttackDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory,!blockDestruction,prepareStarts,emp);
            case "prepareStarts"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory,forceAttackDestruction,!prepareStarts,emp);
            case "fireSpread"->new Values(blockDestruction,!fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory,forceAttackDestruction,prepareStarts,emp);
            case "hostileMobs"->new Values(blockDestruction,fireSpread,!hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory,forceAttackDestruction,prepareStarts,emp);
            case "neutralRetaliation"->new Values(blockDestruction,fireSpread,hostileMobs,!neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory,forceAttackDestruction,prepareStarts,emp);
            case "environmentDamage"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,!environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory,forceAttackDestruction,prepareStarts,emp);
            case "naturalResources"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,!naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory,forceAttackDestruction,prepareStarts,emp);
            case "blastRadius"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius%4+1,blastPower,mobGeneralExperience,eliminationVictory,forceAttackDestruction,prepareStarts,emp);
            case "blastPower"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower%4+1,mobGeneralExperience,eliminationVictory,forceAttackDestruction,prepareStarts,emp);
            case "mobGeneralExperience"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,!mobGeneralExperience,eliminationVictory,forceAttackDestruction,prepareStarts,emp);
            case "eliminationVictory"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,!eliminationVictory,forceAttackDestruction,prepareStarts,emp);
            case "empVehicles"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory,forceAttackDestruction,prepareStarts,new Emp(!emp.vehicles(),emp.mobs(),emp.redstone()));
            case "empMobs"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory,forceAttackDestruction,prepareStarts,new Emp(emp.vehicles(),!emp.mobs(),emp.redstone()));
            case "empRedstone"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory,forceAttackDestruction,prepareStarts,new Emp(emp.vehicles(),emp.mobs(),!emp.redstone()));
            default->throw new IllegalArgumentException(local.generalsmc.LocalText.token("Неизвестная настройка"));};}
    }
    private volatile Values values=Values.defaults();
    private static final Factory<WorldRules> FACTORY=new Factory<>(WorldRules::new,(tag,lookup)->load(tag),null);
    public static WorldRules get(ServerLevel level){return level.getDataStorage().computeIfAbsent(FACTORY,"generals_rules");}
    public Values values(){return values;}
    public void update(Values next){if(!next.equals(values)){values=next;setDirty();}}
    public static WorldRules load(CompoundTag tag){var rules=new WorldRules();var d=Values.defaults();rules.values=new Values(read(tag,"blockDestruction",d.blockDestruction),read(tag,"fireSpread",d.fireSpread),read(tag,"hostileMobs",d.hostileMobs),read(tag,"neutralRetaliation",d.neutralRetaliation),read(tag,"environmentDamage",d.environmentDamage),read(tag,"naturalResources",d.naturalResources),tag.contains("blastRadius")?Math.clamp(tag.getInt("blastRadius"),1,4):d.blastRadius,tag.contains("blastPower")?Math.clamp(tag.getInt("blastPower"),1,4):d.blastPower,read(tag,"mobGeneralExperience",d.mobGeneralExperience),read(tag,"eliminationVictory",d.eliminationVictory),read(tag,"forceAttackDestruction",true),read(tag,"prepareStarts",false),new Emp(read(tag,"empVehicles",true),read(tag,"empMobs",true),read(tag,"empRedstone",true)));return rules;}
    private static boolean read(CompoundTag tag,String key,boolean fallback){return tag.contains(key)?tag.getBoolean(key):fallback;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){var v=values;tag.putBoolean("blockDestruction",v.blockDestruction);tag.putBoolean("fireSpread",v.fireSpread);tag.putBoolean("hostileMobs",v.hostileMobs);tag.putBoolean("neutralRetaliation",v.neutralRetaliation);tag.putBoolean("environmentDamage",v.environmentDamage);tag.putBoolean("naturalResources",v.naturalResources);tag.putInt("blastRadius",v.blastRadius);tag.putInt("blastPower",v.blastPower);tag.putBoolean("mobGeneralExperience",v.mobGeneralExperience);tag.putBoolean("eliminationVictory",v.eliminationVictory);tag.putBoolean("forceAttackDestruction",v.forceAttackDestruction);tag.putBoolean("prepareStarts",v.prepareStarts);tag.putBoolean("empVehicles",v.emp.vehicles());tag.putBoolean("empMobs",v.emp.mobs());tag.putBoolean("empRedstone",v.emp.redstone());return tag;}
}
