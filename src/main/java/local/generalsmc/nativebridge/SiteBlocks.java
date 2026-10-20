package local.generalsmc.nativebridge;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

/** A visible editor marker; the actual derrick is the original native template and capture economy. */
public final class SiteBlocks {
    public static final ResourceKey<Block> OIL_KEY=ResourceKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("generalsmc","oil_derrick_marker"));
    public static final Block OIL=new Marker(BlockBehaviour.Properties.of().setId(OIL_KEY).strength(2).noCollission().noOcclusion());
    public static final ResourceKey<Block> START_KEY=ResourceKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("generalsmc","army_start_marker"));
    public static final Block START=new StartMarker(BlockBehaviour.Properties.of().setId(START_KEY).strength(2).noCollission().noOcclusion());
    public static final ResourceKey<Block> SUPPLY_KEY=ResourceKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("generalsmc","supply_marker"));
    public static final Block SUPPLY=new SupplyMarker(BlockBehaviour.Properties.of().setId(SUPPLY_KEY).strength(2).noCollission().noOcclusion());
    public static void register(){
        Registry.register(BuiltInRegistries.BLOCK,OIL_KEY.location(),OIL);
        var key=ResourceKey.create(Registries.ITEM,OIL_KEY.location());var item=Registry.register(BuiltInRegistries.ITEM,key.location(),new BlockItem(OIL,new Item.Properties().setId(key).useBlockDescriptionPrefix()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries->entries.accept(item));
        Registry.register(BuiltInRegistries.BLOCK,START_KEY.location(),START);
        var startKey=ResourceKey.create(Registries.ITEM,START_KEY.location());var startItem=Registry.register(BuiltInRegistries.ITEM,startKey.location(),new BlockItem(START,new Item.Properties().setId(startKey).useBlockDescriptionPrefix()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries->entries.accept(startItem));
        Registry.register(BuiltInRegistries.BLOCK,SUPPLY_KEY.location(),SUPPLY);
        SiteMarkerEntity.TYPE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath("generalsmc","site_marker"),net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(SiteMarkerEntity::new,OIL,START,SUPPLY).build());
        var supplyKey=ResourceKey.create(Registries.ITEM,SUPPLY_KEY.location());var supplyItem=Registry.register(BuiltInRegistries.ITEM,supplyKey.location(),new BlockItem(SUPPLY,new Item.Properties().setId(supplyKey).useBlockDescriptionPrefix()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries->entries.accept(supplyItem));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,ResourceLocation.fromNamespaceAndPath("generalsmc","markers"),net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder().title(net.minecraft.network.chat.Component.translatable("itemGroup.generalsmc")).icon(()->new ItemStack(startItem)).displayItems((parameters,entries)->{entries.accept(startItem);entries.accept(item);entries.accept(supplyItem);}).build());
    }
    private abstract static class VisibleMarker extends Block implements EntityBlock {
        VisibleMarker(Properties p){super(p.lightLevel(s->10));}
        @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new SiteMarkerEntity(pos,state);}
    }
    private static final class SupplyMarker extends VisibleMarker {
        SupplyMarker(Properties p){super(p);}
        @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){super.onPlace(state,level,pos,old,moved);if(level instanceof net.minecraft.server.level.ServerLevel server&&old.getBlock()!=this)WorldSiteStore.get(server).put(pos,"SupplyDock",0,"resource");}
        @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){super.onRemove(state,level,pos,next,moved);if(level instanceof net.minecraft.server.level.ServerLevel server&&next.getBlock()!=this)WorldSiteStore.get(server).remove(pos);}
    }
    private static final class StartMarker extends VisibleMarker {
        StartMarker(Properties p){super(p);}
        @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){super.onPlace(state,level,pos,old,moved);if(level instanceof net.minecraft.server.level.ServerLevel server&&old.getBlock()!=this)WorldSiteStore.get(server).newStart(pos);}
        @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){super.onRemove(state,level,pos,next,moved);if(level instanceof net.minecraft.server.level.ServerLevel server&&next.getBlock()!=this)WorldSiteStore.get(server).remove(pos);}
        @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.phys.BlockHitResult hit){if(player instanceof net.minecraft.server.level.ServerPlayer server)NativeNetworkServer.openStart(server,pos);return net.minecraft.world.InteractionResult.SUCCESS;}
    }
    private static final class Marker extends VisibleMarker {
        static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
        Marker(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
        @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){super.onPlace(state,level,pos,old,moved);if(level instanceof net.minecraft.server.level.ServerLevel server&&old.getBlock()!=this)WorldSiteStore.get(server).put(pos,"TechOilDerrick",(float)Math.toRadians(state.getValue(FACING).toYRot()),"structure");}
        @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){super.onRemove(state,level,pos,next,moved);if(level instanceof net.minecraft.server.level.ServerLevel server&&next.getBlock()!=this)WorldSiteStore.get(server).remove(pos);}
    }
}
