package local.generalsmc.nativebridge;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** World-owned settings: every commander uses the server's choices, never a client preference. */
public final class WorldRules extends SavedData {
    public record Values(boolean blockDestruction,boolean fireSpread,boolean hostileMobs,boolean neutralRetaliation,boolean environmentDamage,boolean naturalResources,int blastRadius,int blastPower,boolean mobGeneralExperience,boolean eliminationVictory){
        public Values(boolean a,boolean b,boolean c,boolean d,boolean e,boolean f,int radius,int power){this(a,b,c,d,e,f,radius,power,true,true);}
        public Values(boolean a,boolean b,boolean c,boolean d,boolean e,boolean f){this(a,b,c,d,e,f,2,1);}
        public static Values defaults(){return new Values(true,true,true,true,true,true);}
        public Values toggle(String key){return switch(key){
            case "blockDestruction"->new Values(!blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory);
            case "fireSpread"->new Values(blockDestruction,!fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory);
            case "hostileMobs"->new Values(blockDestruction,fireSpread,!hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory);
            case "neutralRetaliation"->new Values(blockDestruction,fireSpread,hostileMobs,!neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory);
            case "environmentDamage"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,!environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory);
            case "naturalResources"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,!naturalResources,blastRadius,blastPower,mobGeneralExperience,eliminationVictory);
            case "blastRadius"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius%4+1,blastPower,mobGeneralExperience,eliminationVictory);
            case "blastPower"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower%4+1,mobGeneralExperience,eliminationVictory);
            case "mobGeneralExperience"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,!mobGeneralExperience,eliminationVictory);
            case "eliminationVictory"->new Values(blockDestruction,fireSpread,hostileMobs,neutralRetaliation,environmentDamage,naturalResources,blastRadius,blastPower,mobGeneralExperience,!eliminationVictory);
            default->throw new IllegalArgumentException(local.generalsmc.LocalText.token("Неизвестная настройка"));};}
    }
    private volatile Values values=Values.defaults();
    private static final net.minecraft.world.level.saveddata.SavedDataType<WorldRules> FACTORY=new net.minecraft.world.level.saveddata.SavedDataType<>("generals_rules",WorldRules::new,CompoundTag.CODEC.xmap(WorldRules::load,value->value.save(new CompoundTag(),null)),null);
    public static WorldRules get(ServerLevel level){return level.getDataStorage().computeIfAbsent(FACTORY);}
    public Values values(){return values;}
    public void update(Values next){if(!next.equals(values)){values=next;setDirty();}}
    public static WorldRules load(CompoundTag tag){var rules=new WorldRules();var d=Values.defaults();rules.values=new Values(read(tag,"blockDestruction",d.blockDestruction),read(tag,"fireSpread",d.fireSpread),read(tag,"hostileMobs",d.hostileMobs),read(tag,"neutralRetaliation",d.neutralRetaliation),read(tag,"environmentDamage",d.environmentDamage),read(tag,"naturalResources",d.naturalResources),tag.contains("blastRadius")?Math.clamp(tag.getIntOr("blastRadius",0),1,4):d.blastRadius,tag.contains("blastPower")?Math.clamp(tag.getIntOr("blastPower",0),1,4):d.blastPower,read(tag,"mobGeneralExperience",d.mobGeneralExperience),read(tag,"eliminationVictory",d.eliminationVictory));return rules;}
    private static boolean read(CompoundTag tag,String key,boolean fallback){return tag.contains(key)?tag.getBooleanOr(key,false):fallback;}
    public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){var v=values;tag.putBoolean("blockDestruction",v.blockDestruction);tag.putBoolean("fireSpread",v.fireSpread);tag.putBoolean("hostileMobs",v.hostileMobs);tag.putBoolean("neutralRetaliation",v.neutralRetaliation);tag.putBoolean("environmentDamage",v.environmentDamage);tag.putBoolean("naturalResources",v.naturalResources);tag.putInt("blastRadius",v.blastRadius);tag.putInt("blastPower",v.blastPower);tag.putBoolean("mobGeneralExperience",v.mobGeneralExperience);tag.putBoolean("eliminationVictory",v.eliminationVictory);return tag;}
}
