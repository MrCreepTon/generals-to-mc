package local.generalsmc.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** The Minecraft connection carries lobby, authenticated orders and committed original-engine frames. */
public final class NativePackets {
    private NativePackets(){}
    public record Control(String json) implements CustomPacketPayload {
        public static final Type<Control> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("generalsmc","native_control_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Control> CODEC=StreamCodec.of((b,v)->b.writeUtf(v.json,30000),b->new Control(b.readUtf(30000)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Status(String json) implements CustomPacketPayload {
        public static final Type<Status> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("generalsmc","native_status_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Status> CODEC=StreamCodec.of((b,v)->b.writeUtf(v.json,250000),b->new Status(b.readUtf(250000)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Frame(UUID match,int frame,int part,int parts,byte[] bytes) implements CustomPacketPayload {
        public static final Type<Frame> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("generalsmc","native_frame_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Frame> CODEC=StreamCodec.of((b,v)->{b.writeUUID(v.match);b.writeVarInt(v.frame);b.writeVarInt(v.part);b.writeVarInt(v.parts);b.writeByteArray(v.bytes);},b->new Frame(b.readUUID(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readByteArray(48000)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record File(UUID match,String name,byte[] bytes) implements CustomPacketPayload {
        public static final Type<File> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("generalsmc","native_file_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf,File> CODEC=StreamCodec.of((b,v)->{b.writeUUID(v.match);b.writeUtf(v.name,32);b.writeByteArray(v.bytes);},b->new File(b.readUUID(),b.readUtf(32),b.readByteArray(70000)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(){PayloadTypeRegistry.playC2S().register(Control.TYPE,Control.CODEC);PayloadTypeRegistry.playS2C().register(Status.TYPE,Status.CODEC);PayloadTypeRegistry.playS2C().register(Frame.TYPE,Frame.CODEC);PayloadTypeRegistry.playS2C().register(File.TYPE,File.CODEC);}
}
