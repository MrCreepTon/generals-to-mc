package local.generalsmc.client;

import local.generalsmc.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class GeneralsClient implements ClientModInitializer {
    public static KeyMapping nativeToggle,nativeMenu;
    public static String notice="";public static int noticeTicks;
    private boolean launchFpsApplied;
    public static void notify(String text){notice=text;noticeTicks=80;}
    public static boolean blocksInput(){return Boolean.getBoolean("generalsmc.nativeManualBench")&&(boolean)development("NativeWorldAccessBench","blocksInput",false,null);}
    private static Object development(String type,String method,Object fallback,Minecraft mc){
        try{var c=Class.forName("local.generalsmc.client."+type);var m=mc==null?c.getDeclaredMethod(method):c.getDeclaredMethod(method,Minecraft.class);m.setAccessible(true);return mc==null?m.invoke(null):m.invoke(null,mc);}
        catch(ClassNotFoundException absent){return fallback;} // Player JARs contain no automation driver.
        catch(ReflectiveOperationException error){throw new IllegalStateException("Development fixture unavailable: "+type,error);}
    }
    private static java.util.concurrent.CompletableFuture<Void> fixtureLanguage;
    private static boolean fixtureLanguage(Minecraft mc){if(fixtureLanguage==null){if(!(MenuScreens.active(mc.screen) instanceof net.minecraft.client.gui.screens.TitleScreen||MenuScreens.active(mc.screen) instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen))return false;String language=System.getProperty("generalsmc.testLanguage","ru_ru");mc.getLanguageManager().setSelected(language);mc.options.languageCode=language;fixtureLanguage=mc.reloadResourcePacks();return false;}if(!fixtureLanguage.isDone())return false;fixtureLanguage.join();return true;}
    private static void fixture(String flag,String type,Minecraft mc){if(Boolean.getBoolean("generalsmc."+flag)&&fixtureLanguage(mc))development(type,"tick",null,mc);}
    @Override public void onInitializeClient() {
        NativeNetworkClient.install();RtsBindings.install();
        BlockEntityRendererRegistry.register(local.generalsmc.nativebridge.SiteMarkerEntity.TYPE,SiteMarkerRenderer::new);
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            if(screen instanceof net.minecraft.client.gui.screens.TitleScreen||screen instanceof net.minecraft.client.gui.screens.PauseScreen)
                net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(MenuButton.styled(L.component("Generals"),b->GeneralsGuide.open(screen)).bounds(w-112,6,106,20).build());
        });
        EntityRendererRegistry.register(local.generalsmc.nativebridge.NativeActorEntity.TYPE,NativeProxyRenderer::new);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(
            ClientCommandManager.literal("zh-native").executes(context->NativeRtsController.open())
                .then(ClientCommandManager.argument("faction",com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((context,builder)->net.minecraft.commands.SharedSuggestionProvider.suggest(NativeRtsController.availableFactions(),builder))
                    .executes(context->NativeRtsController.open(com.mojang.brigadier.arguments.StringArgumentType.getString(context,"faction"),0))
                    .then(ClientCommandManager.argument("color",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                        .executes(context->NativeRtsController.open(com.mojang.brigadier.arguments.StringArgumentType.getString(context,"faction"),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context,"color")))))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(
            ClientCommandManager.literal("zh-native-stop").executes(context->NativeRtsController.stop())));
        if(Boolean.getBoolean("generalsmc.development")||Boolean.getBoolean("generalsmc.nativeManualBench"))ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(
            ClientCommandManager.literal("zh-debug")
                .executes(c->{c.getSource().sendFeedback(L.component(L.s("F8 → чат: /zh-debug money <0..1000000>, /zh-debug ready, /zh-debug cooldown <секунды>, /zh-debug heal, /zh-debug status, /zh-debug sound <имя события>, /zh-debug music, /zh-debug fog <true/false>")));return 1;})
                .then(ClientCommandManager.literal("money").then(ClientCommandManager.argument("amount",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,1000000)).executes(c->NativeRtsController.debug("DEBUG_MONEY "+com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"amount")))))
                .then(ClientCommandManager.literal("ready").executes(c->NativeRtsController.debug("DEBUG_COOLDOWN 0")))
                .then(ClientCommandManager.literal("cooldown").then(ClientCommandManager.argument("seconds",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,3600)).executes(c->NativeRtsController.debug("DEBUG_COOLDOWN "+com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"seconds")))))
                .then(ClientCommandManager.literal("heal").executes(c->NativeRtsController.debug("DEBUG_HEAL")))
                .then(ClientCommandManager.literal("fog").then(ClientCommandManager.argument("enabled",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->{var view=NativeWorldView.current;if(view==null)return 0;view.fog.enabled(com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"enabled"));c.getSource().sendFeedback(L.component(L.s("Туман войны: ")+view.fog.enabled()));return 1;})))
                .then(ClientCommandManager.literal("music").executes(c->NativeRtsController.debug("DEBUG_MUSIC")))
                .then(ClientCommandManager.literal("sound").then(ClientCommandManager.argument("name",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c->NativeRtsController.debug("DEBUG_SOUND "+com.mojang.brigadier.arguments.StringArgumentType.getString(c,"name")))))
                .then(ClientCommandManager.literal("status").executes(c->NativeRtsController.debugStatus()))));
        nativeToggle=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.generalsmc.native_toggle",GLFW.GLFW_KEY_F8,"category.generalsmc"));
        nativeMenu=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.generalsmc.menu",GLFW.GLFW_KEY_F9,"category.generalsmc"));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if(!launchFpsApplied){launchFpsApplied=true;
                if(System.getProperty("generalsmc.nativeFps")!=null)mc.options.framerateLimit().set(Math.max(30,Math.min(240,Integer.getInteger("generalsmc.nativeFps",60))));
                if(System.getProperty("generalsmc.nativeRenderDistance")!=null)mc.options.renderDistance().set(Math.max(4,Math.min(32,Integer.getInteger("generalsmc.nativeRenderDistance",8))));
                if(System.getProperty("generalsmc.nativeSimulationDistance")!=null)mc.options.simulationDistance().set(Math.max(4,Math.min(32,Integer.getInteger("generalsmc.nativeSimulationDistance",6))));
            }
            while(nativeToggle.consumeClick())if(mc.level!=null&&MenuScreens.active(mc.screen)==null)NativeRtsController.toggle();
            while(nativeMenu.consumeClick())if(MenuScreens.active(mc.screen)==null)GeneralsGuide.open(null);
            if (noticeTicks > 0) noticeTicks--;
            fixture("nativeBench","NativeClientBench",mc);
            fixture("nativeManualBench","NativeWorldAccessBench",mc);
            NativeRtsController.tick(mc);
            NativeNetworkClient.tick(mc);
            NativeDrivingClient.tick(mc);
            fixture("nativeReleaseBench","NativeReleaseBench",mc);
            fixture("nativeSetupBench","NativeSetupBench",mc);
            fixture("nativeNetworkBench","NativeNetworkBench",mc);
            fixture("nativeNetworkPeer","NativeNetworkPeerBench",mc);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(NativeWorldView::render);
        WorldRenderEvents.LAST.register(NativeFog::render);
    }
}
