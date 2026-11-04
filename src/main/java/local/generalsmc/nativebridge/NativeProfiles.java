package local.generalsmc.nativebridge;

import com.google.gson.Gson;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Private, immutable game mounts. Never writes into an owned game installation. */
public final class NativeProfiles {
    private NativeProfiles() {}
    private static final Gson JSON = new Gson();
    private static final Set<String> STOCK = Set.of("audio","audioenglish","english","ini","maps","music","patch","shaders","speech","speechenglish","terrain","textures","w3d","w3denglish","window","gensec");
    public record Profile(String id,String name,String game,String base,String mod,boolean allArchives,
                          List<String> factions,List<NativeSession.TeamColor> colors,String checkedAt,String engineHash,Map<String,String> factionNames,boolean modified,Map<String,Integer> startRadii) {
        public Profile(String id,String name,String game,String base,String mod,boolean all,List<String> factions,List<NativeSession.TeamColor> colors,String checked,String hash,Map<String,String> labels,boolean modified){this(id,name,game,base,mod,all,factions,colors,checked,hash,labels,modified,Map.of());}
        public Profile(String id,String name,String game,String base,String mod,boolean all,List<String> factions,List<NativeSession.TeamColor> colors,String checked,String hash){this(id,name,game,base,mod,all,factions,colors,checked,hash,Map.of(),false);}
    }
    public record Registry(int schema,String active,List<Profile> profiles) {}
    public record Mount(String source,String destination,long bytes,long modified) {}
    public static boolean needsRefresh(Profile p){return p!=null&&(p.factionNames()==null||p.factionNames().isEmpty()||p.factions()==null||p.factions().isEmpty()||p.colors()==null||p.colors().isEmpty());}
    public static boolean available(Profile p){try{return p!=null&&Files.isRegularFile(Path.of(p.game(),"INIZH.big"))&&Files.isRegularFile(Path.of(p.game(),"W3DZH.big"))&&Files.isRegularFile(Path.of(p.base(),"INI.big"))&&Files.isRegularFile(Path.of(p.base(),"W3D.big"))&&(p.mod()==null||p.mod().isBlank()||Files.isDirectory(Path.of(p.mod())));}catch(Exception ignored){return false;}}
    public static Path root(){String local=System.getenv("LOCALAPPDATA");Path defaultRoot=local==null||local.isBlank()?Path.of(System.getProperty("user.home"),".local/share/GeneralsMinecraft"):Path.of(local,"GeneralsMinecraft");return Path.of(System.getProperty("generalsmc.nativeRoot",defaultRoot.toString()));}
    public static Path engine(Path root){try{return NativeRuntime.prepare(root);}catch(IOException error){throw new java.io.UncheckedIOException(error);}}
    public static synchronized Registry read() throws IOException {
        Path file=root().resolve("profiles.json");
        if(!Files.exists(file))return new Registry(1,"",List.of());
        Registry value=JSON.fromJson(Files.readString(file),Registry.class);
        if(value==null||value.schema()!=1||value.profiles()==null)throw new IOException(local.generalsmc.LocalText.token("Не удалось прочитать список игр: ")+file);
        for(Profile p:value.profiles())UUID.fromString(p.id());
        return value;
    }
    public static synchronized Profile active() throws IOException {Registry r=read();return r.profiles().stream().filter(p->p.id().equals(r.active())).findFirst().orElse(null);}
    public static Path lab(Path root,Profile p){UUID.fromString(p.id());return root.resolve("profiles").resolve(p.id()).resolve("lab");}
    public static synchronized void select(String id) throws IOException {Registry r=read();if(r.profiles().stream().noneMatch(p->p.id().equals(id)))throw new IOException(local.generalsmc.LocalText.token("Профиль не найден"));write(new Registry(1,id,r.profiles()));}
    /** Unregister only; owned installations and retained private diagnostics stay intact. */
    public static synchronized void remove(String id)throws IOException{
        Registry r=read();var next=r.profiles().stream().filter(p->!p.id().equals(id)).toList();
        if(next.size()==r.profiles().size())throw new IOException(local.generalsmc.LocalText.token("Профиль не найден"));
        write(new Registry(1,r.active().equals(id)?next.isEmpty()?"":next.getFirst().id():r.active(),next));
    }
    private static void write(Registry value) throws IOException {Files.createDirectories(root());Path temp=Files.createTempFile(root(),"profiles-",".tmp");try{Files.writeString(temp,JSON.toJson(value));Files.move(temp,root().resolve("profiles.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
    public record GamePaths(String zeroHour,String generals) {}
    private static boolean installation(Path p,boolean zh){return Files.isRegularFile(p.resolve(zh?"INIZH.big":"INI.big"))&&Files.isRegularFile(p.resolve(zh?"W3DZH.big":"W3D.big"));}
    /** Nearby folders only; never recursively scans a drive or runs an installed launcher. */
    public static GamePaths locate(String input){
        if(input==null||input.isBlank())return new GamePaths("","");
        try{
            Path selected=Path.of(input.strip()).toAbsolutePath().normalize();if(!Files.isDirectory(selected))return new GamePaths("","");
            var candidates=new LinkedHashSet<Path>();candidates.add(selected);Path parent=selected.getParent();
            for(Path root:parent==null?List.of(selected):List.of(selected,parent)){
                try(var dirs=Files.list(root)){dirs.filter(Files::isDirectory).sorted().limit(64).forEach(candidates::add);}catch(IOException ignored){}
            }
            var directZh=candidates.stream().filter(p->installation(p,true)).findFirst();var directBase=candidates.stream().filter(p->installation(p,false)).findFirst();
            if(directZh.isPresent()&&directBase.isPresent())return new GamePaths(directZh.get().toString(),directBase.get().toString());
            // Common collection/Steam layouts have one extra directory layer.
            for(Path root:List.copyOf(candidates)){
                if(candidates.size()>=160)break;
                if(installation(root,true)||installation(root,false))continue;
                try(var dirs=Files.list(root)){dirs.filter(Files::isDirectory).sorted().limit(Math.min(8,160-candidates.size())).forEach(candidates::add);}catch(IOException ignored){}
            }
            String zh=candidates.stream().filter(p->installation(p,true)).map(Path::toString).findFirst().orElse("");
            String base=candidates.stream().filter(p->installation(p,false)).map(Path::toString).findFirst().orElse("");
            return new GamePaths(zh,base);
        }catch(RuntimeException ignored){return new GamePaths("","");}
    }
    public static String browseStart(String input){
        try{if(input!=null&&!input.isBlank()){Path p=Path.of(input.strip()).toAbsolutePath();while(p!=null){if(Files.isDirectory(p))return p.toString();p=p.getParent();}}}catch(RuntimeException ignored){}
        return Path.of(System.getProperty("user.home")).toAbsolutePath().toString();
    }
    public static List<String> discover(){var found=new LinkedHashSet<String>();for(String root:List.of("C:/Games/Generals ZH","C:/Games/Generals","C:/Program Files (x86)/EA Games/Command & Conquer Generals Zero Hour","C:/Program Files/EA Games/Command and Conquer Generals Zero Hour")){if(!Files.isDirectory(Path.of(root)))continue;var pair=locate(root);if(!pair.zeroHour().isBlank())found.add(pair.zeroHour());}return List.copyOf(found);}
    public static String suggestBase(String game){return locate(game).generals();}
    public static String suggestName(String game){try{Path source=Path.of(game);try(var files=Files.list(source)){for(var file:files.filter(f->f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("launchersettings.ini")).toList()){if(Files.size(file)>65536)continue;var matcher=java.util.regex.Pattern.compile("(?im)^Title\\s*=\\s*([^\\r\\n]+)").matcher(Files.readString(file,StandardCharsets.ISO_8859_1));if(matcher.find()){String name=matcher.group(1).trim().replaceFirst("(?i)\\s+Launcher$","");if(!name.isBlank()&&name.length()<=60)return name;}}}if(!launcherArchives(source).isEmpty())return source.getFileName().toString();}catch(Exception ignored){}return "Zero Hour";}
    private static Path source(String input,String label) throws IOException {if(input==null||input.isBlank())throw new IOException(local.generalsmc.LocalText.token("Выберите папку ")+label);Path p=Path.of(input).toRealPath();if(!Files.isDirectory(p))throw new IOException(local.generalsmc.LocalText.token("Нет папки ")+label+": "+p);return p;}
    private static void require(Path p,String file) throws IOException {if(!Files.isRegularFile(p.resolve(file)))throw new IOException(local.generalsmc.LocalText.token("В папке ")+p+local.generalsmc.LocalText.token(" не найден ")+file+local.generalsmc.LocalText.token(". Нужны файлы установленной игры, не её ярлык."));}
    private static boolean stock(Path p,boolean zh){String n=p.getFileName().toString().toLowerCase(Locale.ROOT);n=n.substring(0,n.length()-4);if(zh&&n.endsWith("zh"))n=n.substring(0,n.length()-2);return STOCK.contains(n);}
    private static void copy(Path from,Path to,List<Mount> mounts) throws IOException {
        if(Thread.currentThread().isInterrupted())throw new IOException(local.generalsmc.LocalText.token("Подключение отменено"));
        if(Files.isSymbolicLink(from))throw new IOException(local.generalsmc.LocalText.token("Символическая ссылка внутри данных: ")+from);
        Files.createDirectories(to.getParent());
        // Engines read archives only. A link avoids duplicating GB; other volumes use a copy.
        try{Files.createLink(to,from);}catch(IOException|UnsupportedOperationException e){Files.copy(from,to);}
        mounts.add(new Mount(from.toString(),to.toString(),Files.size(from),Files.getLastModifiedTime(from).toMillis()));
    }
    private static void archives(Path source,Path target,boolean zh,boolean all,String prefix,List<Mount> mounts) throws IOException {
        try(var files=Files.list(source)){for(Path p:files.filter(Files::isRegularFile).filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".big")).sorted(Comparator.comparing(p->p.getFileName().toString().toLowerCase(Locale.ROOT))).toList()){
            if(!all&&!stock(p,zh))continue;
            validateBig(p);
            copy(p,target.resolve(prefix+p.getFileName()),mounts);
        }}
        // Some installed mod launchers temporarily rename their BIG packages.
        // Read only their declared file map; activate copies/links in our private
        // mount, never rename the owner's installed files or run its launcher.
        if(zh)for(var entry:launcherArchives(source).entrySet()){validateBig(entry.getKey());copy(entry.getKey(),target.resolve(prefix+entry.getValue()),mounts);}
    }
    private static Map<Path,String> launcherArchives(Path source)throws IOException{
        var result=new LinkedHashMap<Path,String>();
        try(var files=Files.list(source)){for(var mapping:files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("_lnchr.dat")).toList()){
            if(Files.size(mapping)>1024*1024)continue;
            var pattern=java.util.regex.Pattern.compile("(?im)^\\s*([^<>\\r\\n]+\\.gib)\\s*>\\s*([^<>\\r\\n]+\\.big)\\s*$");
            var matcher=pattern.matcher(Files.readString(mapping,StandardCharsets.ISO_8859_1));
            while(matcher.find()){String from=matcher.group(1).trim(),to=matcher.group(2).trim();if(from.indexOf('/')>=0||from.indexOf('\\')>=0||to.indexOf('/')>=0||to.indexOf('\\')>=0)continue;var file=source.resolve(from);if(Files.isRegularFile(file))result.put(file,to);}
        }}return result;
    }
    /** Check native fixed-size filename/table limits before asking its legacy parser to read a mod. */
    public static void validateBig(Path file) throws IOException {
        try(RandomAccessFile in=new RandomAccessFile(file.toFile(),"r")){
            if(in.length()<16||in.length()>Integer.MAX_VALUE)throw new IOException(local.generalsmc.LocalText.token("Повреждён BIG или превышен предел32-bit движка: ")+file.getFileName());
            byte[] magic=new byte[4];in.readFully(magic);
            if(!Arrays.equals(magic,"BIGF".getBytes(StandardCharsets.US_ASCII)))throw new IOException(local.generalsmc.LocalText.token("Движок поддерживает BIGF; неподдерживаемый архив: ")+file.getFileName());
            in.readInt();long count=Integer.toUnsignedLong(in.readInt()),end=Integer.toUnsignedLong(in.readInt());
            if(count>1_000_000||end<16||end>in.length())throw new IOException(local.generalsmc.LocalText.token("Повреждена таблица BIG: ")+file.getFileName());
            for(long i=0;i<count;i++){
                long offset=Integer.toUnsignedLong(in.readInt()),size=Integer.toUnsignedLong(in.readInt());
                int n=0,b;while((b=in.readUnsignedByte())!=0){if(++n>240)throw new IOException(local.generalsmc.LocalText.token("Слишком длинное имя в BIG: ")+file.getFileName());}
                if(in.getFilePointer()>end||(size>0&&(offset<end||offset+size>in.length())))throw new IOException(local.generalsmc.LocalText.token("Некорректные границы BIG: ")+file.getFileName());
            }
        }
    }
    private static Set<Path> launcherDisabledLoose(Path root)throws IOException{
        var disabled=new HashSet<Path>();try(var files=Files.list(root)){for(var mapping:files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("_lnchr.dat")).toList()){
            if(Files.size(mapping)>1024*1024)continue;var matcher=java.util.regex.Pattern.compile("(?im)^\\s*([^<>\\r\\n]+\\.(?:scb|ini))\\s*>\\s*([^<>\\r\\n]+\\.nope)\\s*$").matcher(Files.readString(mapping,StandardCharsets.ISO_8859_1));
            while(matcher.find()){Path file=root.resolve(matcher.group(1).trim().replace('\\','/')).normalize();if(file.startsWith(root))disabled.add(file);}
        }}return disabled;
    }
    private static void loose(Path source,Path target,List<Mount> mounts,boolean replace,Set<Path> disabled) throws IOException {
        if(!Files.isDirectory(source))return;
        try(var files=Files.walk(source)){for(Path p:files.filter(Files::isRegularFile).toList()){
            if(disabled.contains(p))continue;
            String name=p.getFileName().toString().toLowerCase(Locale.ROOT);
            if(name.endsWith(".exe")||name.endsWith(".dll")||name.endsWith(".big"))continue;
            Path relative=source.relativize(p),to=target.resolve(relative);
            if(Files.exists(to)){if(!replace)continue;Files.delete(to);}
            copy(p,to,mounts);
        }}
    }
    public static Profile connect(String name,String gameInput,String baseInput,String modInput,boolean all,Consumer<String> progress) throws Exception {
        return prepare(null,name,gameInput,baseInput,modInput,all,progress);
    }
    public static Profile update(String id,String name,String gameInput,String baseInput,String modInput,boolean all,Consumer<String> progress)throws Exception{
        UUID.fromString(id);return prepare(id,name,gameInput,baseInput,modInput,all,progress);
    }
    private static Profile prepare(String replacing,String name,String gameInput,String baseInput,String modInput,boolean all,Consumer<String> progress)throws Exception{
        if(name==null||name.isBlank()||name.length()>60)throw new IOException(local.generalsmc.LocalText.token("Введите название профиля (до60 символов)"));
        Path game=source(gameInput,"Zero Hour"),base=source(baseInput,"Generals");
        require(game,"INIZH.big");require(game,"W3DZH.big");require(base,"INI.big");require(base,"W3D.big");
        require(game,"mss32.dll");require(game,"BINKW32.DLL");
        Path mod=modInput==null||modInput.isBlank()?null:source(modInput,local.generalsmc.LocalText.token("мода"));
        // Old setup allowed choosing the installed total conversion twice. That
        // gave stock archives a second high-priority prefix over the mod INIs.
        if(mod!=null&&Files.isSameFile(mod,game)){mod=null;if(!launcherArchives(game).isEmpty())all=false;}
        if(!Files.isRegularFile(engine(root())))throw new IOException(local.generalsmc.LocalText.token("В моде отсутствует движок Generals. Скачайте полный JAR мода и положите его в папку mods."));
        String id=UUID.randomUUID().toString();
        Profile candidate=new Profile(id,name.trim(),game.toString(),base.toString(),mod==null?"":mod.toString(),all,List.of(),List.of(),"","");
        Path lab=lab(root(),candidate);
        for(Path original:mod==null?List.of(game,base):List.of(game,base,mod))if(lab.toAbsolutePath().startsWith(original))throw new IOException(local.generalsmc.LocalText.token("Профиль должен находиться вне папки оригинальной игры"));
        Files.createDirectories(lab.resolve("zh"));Files.createDirectories(lab.resolve("base"));Files.createDirectories(lab.resolve("userdata"));
        List<Mount> mounts=new ArrayList<>();progress.accept(local.generalsmc.LocalText.token("Проверка и подключение архивов…"));
        archives(game,lab.resolve("zh"),true,all,"",mounts);archives(base,lab.resolve("base"),false,false,"",mounts);
        if(mod!=null){int before=mounts.size();archives(mod,lab.resolve("zh"),true,true,"!!zhmc-mod-",mounts);var disabled=launcherDisabledLoose(mod);for(String folder:List.of("Data","Art","Maps"))loose(mod.resolve(folder),lab.resolve("zh").resolve(folder),mounts,true,disabled);if(mounts.size()==before)throw new IOException(local.generalsmc.LocalText.token("В папке мода нет BIG или данных Data/Art/Maps. EXE-патчи пока не поддерживаются."));}
        var disabled=launcherDisabledLoose(game);for(String folder:List.of("Data","Art","Maps"))loose(game.resolve(folder),lab.resolve("zh").resolve(folder),mounts,false,disabled);
        // Copy providers, never injection wrappers/game executables from a mod.
        if(Files.isDirectory(game.resolve("MSS")))try(var stream=Files.list(game.resolve("MSS"))){for(Path p:stream.filter(Files::isRegularFile).toList())copy(p,lab.resolve("zh/MSS").resolve(p.getFileName()),mounts);}
        for(String f:List.of("mss32.dll","BINKW32.DLL","langdata.dat"))if(Files.isRegularFile(game.resolve(f)))copy(game.resolve(f),lab.resolve("zh").resolve(f),mounts);
        Files.writeString(lab.resolve("mounts.json"),JSON.toJson(mounts));
        progress.accept(local.generalsmc.LocalText.token("Проверка запуска игры…"));
        NativeSession.State state=null;
        try(NativeSession probe=new NativeSession(root(),false,800,600,lab)){
            long deadline=System.currentTimeMillis()+60000;
            while(System.currentTimeMillis()<deadline){state=probe.read();if(state!=null){if(state.error()!=null&&!state.error().isEmpty())throw new IOException(state.error());if(state.playableFactions()!=null&&!state.playableFactions().isEmpty()&&state.maps()!=null&&!state.maps().isEmpty())break;}Thread.sleep(100);}
            if(state==null||state.playableFactions()==null||state.playableFactions().isEmpty()||state.maps()==null||state.maps().isEmpty())throw new IOException(local.generalsmc.LocalText.token("Игра не прошла проверку запуска. Подробности: ")+lab.resolve("sessions"));
            probe.close();if(!probe.awaitExit(8000))throw new IOException(local.generalsmc.LocalText.token("Предыдущая проверка ещё завершается; повторите подключение через несколько секунд"));
        }
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(engine(root()))));
        if(Thread.currentThread().isInterrupted())throw new IOException(local.generalsmc.LocalText.token("Подключение отменено"));
        Profile ready=new Profile(id,name.trim(),game.toString(),base.toString(),mod==null?"":mod.toString(),all,List.copyOf(state.playableFactions()),List.copyOf(state.colors()),java.time.Instant.now().toString(),hash,state.factionNames()==null?Map.of():Map.copyOf(state.factionNames()),mod!=null||all||!launcherArchives(game).isEmpty(),state.startRadii()==null?Map.of():Map.copyOf(state.startRadii()));
        synchronized(NativeProfiles.class){Registry r=read();List<Profile> next=new ArrayList<>(r.profiles());
            int at=-1;if(replacing!=null){for(int i=0;i<next.size();i++)if(next.get(i).id().equals(replacing))at=i;if(at<0)throw new IOException(local.generalsmc.LocalText.token("Профиль не найден"));next.set(at,ready);}else next.add(ready);
            write(new Registry(1,replacing==null||r.active().equals(replacing)?id:r.active(),List.copyOf(next)));}
        progress.accept(local.generalsmc.LocalText.token("Подключено: ")+ready.name());return ready;
    }
}
