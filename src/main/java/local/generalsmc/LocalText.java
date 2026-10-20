package local.generalsmc;
/** Stable language tokens cross the server connection without choosing the server's language. */
public final class LocalText {
    private LocalText(){}
    public static String key(String source){return "generalsmc.text."+Integer.toUnsignedString(source.hashCode(),16);}
    public static String token(String source){return "{#"+key(source)+"#}";}
}
