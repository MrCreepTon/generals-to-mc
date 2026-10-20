package local.generalsmc.client;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.*;
import java.util.*;

/** Original keyboard messages reach the chosen game's own CommandMap and hotkeys. */
final class RtsBindings {
    static KeyMapping primary,pan,rotate;
    static void install(){primary=mouse("primary",0);pan=mouse("camera_pan",1);rotate=mouse("camera_rotate",2);KeyMapping.resetMapping();}
    static KeyMapping mouse(String id,int button){return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.generalsmc."+id,InputConstants.Type.MOUSE,button,GeneralsClient.CategoryHolder.GENERALS));}
    static int scan(int key,int scan){return NativeFrameScreen.scanCode(key,scan);}
    static int button(int physical){if(primary==null)return physical;if(primary.matchesMouse(new net.minecraft.client.input.MouseButtonEvent(0,0,new net.minecraft.client.input.MouseButtonInfo(physical,0))))return 0;if(pan.matchesMouse(new net.minecraft.client.input.MouseButtonEvent(0,0,new net.minecraft.client.input.MouseButtonInfo(physical,0))))return 1;if(rotate.matchesMouse(new net.minecraft.client.input.MouseButtonEvent(0,0,new net.minecraft.client.input.MouseButtonInfo(physical,0))))return 2;return -1;}
    static int mouseScan(int physical){return 0;}
    static String summary(){return GeneralsClient.nativeToggle.getTranslatedKeyMessage().getString()+" — Minecraft / RTS • "+GeneralsClient.nativeMenu.getTranslatedKeyMessage().getString()+" — Generals";}
    static List<net.minecraft.network.chat.Component> descriptions(){var list=new ArrayList<net.minecraft.network.chat.Component>();list.add(L.component(L.s("Клавиши Generals берутся из выбранной игры. Подсказки действий показаны в её интерфейсе.")));list.add(L.component(L.s("Клавиши переключения и мышь можно изменить в настройках Minecraft.")));for(var key:List.of(GeneralsClient.nativeToggle,GeneralsClient.nativeMenu,primary,pan,rotate))list.add(net.minecraft.network.chat.Component.translatable(key.getName()).append(": ").append(key.getTranslatedKeyMessage()));return list;}
}
