package local.generalsmc.client;
import local.generalsmc.LocalText;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import java.util.regex.*;
final class L {
    private static final Pattern TOKEN=Pattern.compile("\\{#(generalsmc[.][a-zA-Z0-9_.]+)#}");
    static String s(String source){return I18n.get(LocalText.key(source));}
    static String s(String source,Object... args){return I18n.get(LocalText.key(source),args);}
    static String decode(String value){if(value==null)return "";var m=TOKEN.matcher(value);var out=new StringBuffer();while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(I18n.get(m.group(1))));m.appendTail(out);return out.toString();}
    static Component component(String value){return Component.literal(decode(value));}
}
