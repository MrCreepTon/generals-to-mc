package local.generalsmc.client;

import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import java.util.*;

/** Real profile management callbacks, including failed replacement and delete cancellation. */
final class NativeProfilesCrudBench {
    static int stage,age,count;static NativeProfiles.Profile before;static String game,base;
    static boolean tick(Minecraft mc,List<String> checks)throws Exception{
        age++;
        if(stage==0){before=NativeSetupBench.stock;game=before.game();base=before.base();count=NativeProfiles.read().profiles().size();mc.setScreen(new GeneralsSetupScreen(null));NativeSetupBench.click(MenuScreens.active(mc.screen),"Игра: "+before.name());if(!(MenuScreens.active(mc.screen) instanceof NativeProfilesScreen))throw new IllegalStateException("Game picker lacks management");NativeSetupBench.bounds(MenuScreens.active(mc.screen));stage=10;
        }else if(stage==10&&age>20){Screenshot.grab(mc.gameDirectory,"96-profile-management.png",mc.getMainRenderTarget(),c->{});NativeSetupBench.click(MenuScreens.active(mc.screen),"✎");var e=(GeneralsSetupScreen.Connect)MenuScreens.active(mc.screen);if(!e.game.getValue().equals(game)||!e.base.getValue().equals(base)||!e.name.getValue().equals(before.name()))throw new IllegalStateException("Edit did not prefill actual profile");e.game.setValue(NativeProfiles.root().resolve("missing-game").toString());e.connect();stage=1;
        }else if(stage==1&&MenuScreens.active(mc.screen) instanceof GeneralsSetupScreen.Connect e&&!e.busy){
            if(e.status.isBlank()||NativeProfiles.read().profiles().size()!=count||!NativeProfiles.active().id().equals(before.id()))throw new IllegalStateException("Failed edit replaced working profile");checks.add("Edit connection prefills existing paths; failed validation preserves the original working profile and active selection atomically");e.game.setValue(game);e.name.setValue("Edited fixture");e.connect();stage=2;
        }else if(stage==2&&MenuScreens.active(mc.screen) instanceof NativeProfilesScreen){
            var after=NativeProfiles.active();if(!after.name().equals("Edited fixture")||after.id().equals(before.id())||NativeProfiles.read().profiles().size()!=count||!java.nio.file.Files.isDirectory(NativeProfiles.lab(NativeProfiles.root(),before)))throw new IllegalStateException("Validated replacement duplicated or damaged profile");NativeSetupBench.stock=after;checks.add("Successful in-game edit revalidates original data, replaces one connection without duplication, retains previous diagnostics and preserves retail files");NativeSetupBench.click(MenuScreens.active(mc.screen),"×");MenuScreens.active(mc.screen).onClose();if(NativeProfiles.read().profiles().size()!=count)throw new IllegalStateException("Cancelled delete removed a connection");NativeSetupBench.click(MenuScreens.active(mc.screen),"×");var confirm=MenuScreens.active(mc.screen);((Button)confirm.children().getFirst()).onPress();if(NativeProfiles.read().profiles().size()!=count-1||NativeProfiles.active()==null||!java.nio.file.Files.isRegularFile(java.nio.file.Path.of(game,"INIZH.big")))throw new IllegalStateException("Removal harmed game or selection");checks.add("Remove connection requires confirmation; cancellation keeps it; confirmed removal selects another connection and leaves installed game files intact");
            // Use another already validated stock-compatible connection for the remaining launch fixture.
            NativeSetupBench.stock=NativeProfiles.active();mc.setScreen(new GeneralsSetupScreen(null));return true;
        }
        if(age>1200)throw new IllegalStateException("Profile CRUD timeout "+stage);return false;
    }
}
