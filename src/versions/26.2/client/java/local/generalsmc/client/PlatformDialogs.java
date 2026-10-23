package local.generalsmc.client;
import java.awt.Desktop;
import java.net.URI;
import javax.swing.*;
import net.minecraft.client.Minecraft;
/** Desktop folder selection does not depend on Minecraft's window backend. */
final class PlatformDialogs {
 static void folder(String title,String initial,java.util.function.Consumer<String> selected){folder(title,initial,selected,error->GeneralsClient.notify(error));}
 static void folder(String title,String initial,java.util.function.Consumer<String> selected,java.util.function.Consumer<String> failed){SwingUtilities.invokeLater(()->{try{var chooser=new JFileChooser(initial.isBlank()?null:initial);chooser.setDialogTitle(title);chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION){String path=chooser.getSelectedFile().getAbsolutePath();Minecraft.getInstance().execute(()->selected.accept(path));}}catch(Exception error){Minecraft.getInstance().execute(()->failed.accept(error.getMessage()==null?error.toString():error.getMessage()));}});}
 static void open(URI uri){java.util.concurrent.CompletableFuture.runAsync(()->{try{if("file".equals(uri.getScheme()))Desktop.getDesktop().open(java.nio.file.Path.of(uri).toFile());else Desktop.getDesktop().browse(uri);}catch(Exception error){Minecraft.getInstance().execute(()->GeneralsClient.notify(error.getMessage()==null?error.toString():error.getMessage()));}});}
}
