package local.generalsmc.nativebridge;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Own process and transport only. All RTS simulation stays in the source-built EA engine. */
public final class NativeSession implements AutoCloseable {
    public final Path directory;
    private final Process process;
    private long sequence;
    private volatile State lastState;
    public enum Mode { LOCAL, PEER, AUTHORITY, SHARED_AUTHORITY }
    private final Mode mode;
    private boolean directAuthority;
    public synchronized void beginDirectAuthorityWrites(){if(mode!=Mode.SHARED_AUTHORITY)throw new IllegalStateException();directAuthority=true;}
    private long worldToken,acknowledgedWorld,outgoing;
    private volatile long prunedCommands,prunedFrames,prunedOrders;
    private Thread cleanup;
    private record WorldCommand(long token,String text) {}
    private record WorldAck(int frame,long token) {}
    private final java.util.ArrayDeque<WorldCommand> queuedWorld=new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<WorldAck> queuedAcks=new java.util.ArrayDeque<>();
    private java.nio.file.attribute.FileTime stateStamp;
    private final Thread shutdown;
    private boolean closed;
    public final int width,height;
    public record Actor(int id, String template, double x, double y, double z, int owner, double health, double maxHealth, double constructionPercent,double orientation,double weaponRange,String externalKey,double geometryMajor,double geometryMinor,double geometryHeight,double goalX,double goalY,double goalZ,boolean groundMovement,boolean cloaked,boolean structure,int moveRetries,int moveRetryFrame,boolean contained) {}
    public record Control(int id,int x,int y,int width,int height,String command,boolean enabled) {}
    public record ResourceTemplate(String name,float major,float minor,float height) {}
    public record Resource(String key,int id,int cash) {}
    public record CombatEvent(long sequence,String target,int source,double amount,double x,double y,double z,double radius,int damageType,boolean explosive) {}
    public record TeamColor(int id,String label,long color) {}
    public record HealthBar(int id,double x,double y,double z,double dx,double dy,double width,double height,double ratio,long color,long outline) {}
    public record SpecialPower(int object,String name,int readyFrame,boolean ready) {}
    public record ArmyStatus(int player,int rank,int experience,int buildings) {}
    public record State(String engine, long pid, int frame, boolean inGame, long consumed, String error,
                        long terrainRevision, int terrainColumns, List<String> factions, List<String> maps, List<Actor> objects, int localCash,
                        int localPlayerIndex, int terrainExtentX, int terrainExtentY,
                        List<Integer> selected, String pendingCommand, boolean pendingBuild, int stopMessages, String pointerCommand, String pendingBuildType,List<Control> controls,
                        long radarRevision,float radarMinX,float radarMinY,float radarSpan,long cameraSerial,double cameraTargetX,double cameraTargetY,double scrollX,double scrollY,boolean scrolling,boolean forceAttack,int attackMessages,ResourceTemplate resourceTemplate,List<Resource> resources,int exportedParticles,int exportedDecals,int repairedUV,double placementAngle,boolean placementAnchored,int guiClockCount,int guiClockPercent,int testFXParticles,long rotationSerial,float cameraAngle,double healthUnitsPerPoint,List<CombatEvent> combatEvents,boolean pagedWorld,int pathPages,int partitionPages,List<HealthBar> healthBars,List<String> playableFactions,List<TeamColor> colors,int teamColor,long localPlayerColor,Control portrait,boolean hostAudioConfigured,boolean musicPlaying,double musicVolume,double soundVolume,double speechVolume,boolean audioEnabled,int audio2DSamples,int audio3DSamples,int audio2DUsed,int audio3DUsed,List<SpecialPower> specialPowers,List<Resource> sites,boolean lockstep,long lockstepCrc,long lockstepOutgoing,String lockstepError,int exportedLasers,int generalRank,int generalExperience,List<ArmyStatus> armyStatus,int lockstepCrcFrame,Map<String,String> factionNames) {}
    private static final Gson JSON = new Gson();
    public NativeSession(Path root, boolean graphics) throws IOException {
        this(root,graphics,800,600);
    }
    public NativeSession(Path root, boolean graphics,int width,int height) throws IOException {
        this(root,graphics,width,height,defaultLab(root));
    }
    private static Path defaultLab(Path root) throws IOException {
        if(!Boolean.getBoolean("generalsmc.nativeManualBench")&&!Boolean.getBoolean("generalsmc.nativeBench")){
            var profile=NativeProfiles.active();if(profile!=null)return NativeProfiles.lab(root,profile);
        }
        return root.resolve("native-lab");
    }
    public NativeSession(Path root, boolean graphics,int width,int height,Path lab) throws IOException {
        this(root,graphics,width,height,lab,Mode.LOCAL,2);
    }
    public NativeSession(Path root,boolean graphics,int width,int height,Path lab,Mode mode,int player) throws IOException {
        this.mode=mode;
        this.width=Math.max(800,Math.min(1920,width));this.height=Math.max(600,Math.min(1080,height));
        directory = lab.resolve("sessions").resolve(UUID.randomUUID().toString());
        Files.createDirectories(directory.resolve("commands"));
        if(mode!=Mode.LOCAL)Files.createDirectories(directory.resolve("frames"));
        var exe = NativeProfiles.engine(root);
        if(!Files.isRegularFile(exe)||!Files.isDirectory(lab.resolve("zh")))throw new IOException(local.generalsmc.LocalText.token("Подключите игру через меню Generals → Добавить игру."));
        var command = new ArrayList<>(List.of(exe.toString()));
        command.addAll(graphics ? List.of("-win", "-xres", Integer.toString(this.width), "-yres", Integer.toString(this.height)) : List.of("-headless"));
        boolean test=Boolean.getBoolean("generalsmc.nativeManualBench")||Boolean.getBoolean("generalsmc.nativeBench");
        if(!Boolean.parseBoolean(System.getProperty("generalsmc.nativeAudio",graphics&&!test?"true":"false")))command.add("-noaudio");
        command.addAll(List.of("-nologo", "-noshellmap", "-setCwd", lab.resolve("zh").toString()));
        var builder = new ProcessBuilder(command).directory(lab.resolve("zh").toFile());
        if(mode!=Mode.LOCAL){
            builder.environment().put("ZHMC_LOCKSTEP","1");builder.environment().put("ZHMC_LOCAL_PLAYER",Integer.toString(player));
            if(mode==Mode.AUTHORITY||mode==Mode.SHARED_AUTHORITY)builder.environment().put("ZHMC_AUTHORITY","1");
            if(mode==Mode.SHARED_AUTHORITY)builder.environment().put("ZHMC_SHARED_AUTHORITY","1");
        }
        if(WorldScale.PAGED_WORLD)builder.environment().put("ZHMC_PAGED","1");
        builder.environment().put("ZHMC_WORLD_SCALE",Double.toString(WorldScale.UNITS_PER_BLOCK));
        Path userdata=lab.resolve("userdata");if(mode!=Mode.LOCAL){userdata=directory.resolve("userdata");Files.createDirectories(userdata);try(var ini=Files.newDirectoryStream(lab.resolve("userdata"),"*.ini")){for(var file:ini)Files.copy(file,userdata.resolve(file.getFileName()),StandardCopyOption.REPLACE_EXISTING);}}
        builder.environment().put("ZHMC_USER_DATA", userdata.toString());
        builder.environment().put("ZHMC_BASE_DATA", lab.resolve("base").toString());
        builder.environment().put("ZHMC_BRIDGE_DIR", directory.toString());
        builder.environment().put("ZHMC_PARENT_PID",Long.toString(ProcessHandle.current().pid()));
        if(Boolean.getBoolean("generalsmc.nativeTestSupport")||Boolean.getBoolean("generalsmc.nativeManualBench")||Boolean.getBoolean("generalsmc.nativeBench"))builder.environment().put("ZHMC_TEST","1");
        if(mode==Mode.LOCAL&&(Boolean.getBoolean("generalsmc.nativeTestSupport")||Boolean.getBoolean("generalsmc.nativeManualBench")||Boolean.getBoolean("generalsmc.nativeEffectsBench")||Boolean.getBoolean("generalsmc.nativeFogBench")))builder.environment().put("ZHMC_SANDBOX","1");
        if (graphics) builder.environment().put("ZHMC_CAPTURE", "1");
        builder.redirectErrorStream(true).redirectOutput(directory.resolve("process.log").toFile());
        process = builder.start();
        shutdown = new Thread(() -> { if (process.isAlive()) process.destroyForcibly(); }, "zhmc-owned-process-exit");
        Runtime.getRuntime().addShutdownHook(shutdown);
    }
    public synchronized long send(String command) throws IOException {
        if (!process.isAlive()) throw new IOException("Native engine exited " + process.exitValue());
        if (command.length() > 65536) throw new IOException("Native command exceeds transport limit");
        if((mode==Mode.AUTHORITY||mode==Mode.SHARED_AUTHORITY)&&!directAuthority&&worldCommand(command)){long token=++worldToken;queuedWorld.add(new WorldCommand(token,command));return 1000000000L+token;}
        long next = sequence + 1;
        Path temporary = directory.resolve("commands/" + next + ".tmp");
        Files.writeString(temporary, command, StandardOpenOption.CREATE_NEW);
        Files.move(temporary, directory.resolve("commands/" + next + ".cmd"), StandardCopyOption.ATOMIC_MOVE);
        sequence = next;
        return next;
    }
    private static boolean worldCommand(String command){String verb=command.split("\\s",2)[0];return Set.of("BEGIN_SHARED_AUTHORITY","INIT_WORLD","TERRAIN","TERRAIN2","TERRAIN3","HOST_BATCH","HOST_DAMAGE","MOB_SYNC","MOB_DROP","MOB_KILLED","COMBAT_ACK","RESOURCE","RESOURCE_REMOVE","SITE","SITE_REMOVE","REBASE_PLAYER","PLAYER_DEFEAT").contains(verb);}
    public synchronized String commitFrame(int frame,List<String> playerRecords)throws IOException{
        if(mode!=Mode.AUTHORITY&&mode!=Mode.SHARED_AUTHORITY)throw new IOException("Only the server can commit a native frame");
        var text=new StringBuilder("FRAME ").append(frame).append('\n');long token=0;
        while(!queuedWorld.isEmpty()){var c=queuedWorld.remove();for(String line:c.text.split("\\R"))if(!line.isEmpty())text.append("W ").append(line).append('\n');token=c.token;}
        for(String record:playerRecords){if(record.indexOf('\n')>=0||!record.startsWith("C "))throw new IOException("Invalid participant record");text.append(record).append('\n');}
        String value=text.toString();acceptFrame(frame,value);if(token>0)queuedAcks.add(new WorldAck(frame+1,token));return value;
    }
    public synchronized void acceptFrame(int frame,String value)throws IOException{
        if(mode==Mode.LOCAL||frame<0||!value.startsWith("FRAME "+frame+"\n")||value.length()>4*1024*1024)throw new IOException("Invalid native frame");
        if(frame<prunedFrames)return; // Already executed beyond the retained diagnostic window.
        var destination=directory.resolve("frames/"+frame+".frame");if(Files.exists(destination)){if(!Files.readString(destination).equals(value))throw new IOException("Conflicting native frame");return;}
        var temporary=destination.resolveSibling(frame+".tmp");Files.writeString(temporary,value);Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);
    }
    public synchronized List<String> drainOrders()throws IOException{
        if(mode!=Mode.PEER)return List.of();var lines=new ArrayList<String>();
        for(int budget=0;budget<64;budget++){var file=directory.resolve("outbox/"+(outgoing+1)+".msg");if(!Files.isRegularFile(file))break;String line=Files.readString(file).strip();if(line.length()>16000||line.indexOf('\n')>=0)throw new IOException("Invalid original command record");lines.add(line);outgoing++;}return List.copyOf(lines);
    }
    public synchronized boolean acknowledged(long ticket,State state){return (mode==Mode.AUTHORITY||mode==Mode.SHARED_AUTHORITY)&&ticket>=1000000000L?acknowledgedWorld>=ticket-1000000000L:state.consumed()>=ticket;}
    public java.util.OptionalLong crcAt(int frame)throws IOException{
        var file=directory.resolve("checks.bin");if(!Files.isRegularFile(file))return java.util.OptionalLong.empty();
        try(var channel=java.nio.channels.FileChannel.open(file,StandardOpenOption.READ)){var data=java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.LITTLE_ENDIAN);channel.position((frame%256)*16L);if(channel.read(data)!=16)return java.util.OptionalLong.empty();data.flip();int first=data.getInt();long crc=Integer.toUnsignedLong(data.getInt());int last=data.getInt();return first==frame&&last==frame?java.util.OptionalLong.of(crc):java.util.OptionalLong.empty();}
    }
    public synchronized State read() throws IOException {
        if (!process.isAlive()) throw new IOException("Native engine exited " + process.exitValue());
        var file = directory.resolve("state.json");
        // Windows may briefly report the name missing while MoveFileEx replaces a complete snapshot.
        // A single missed poll after startup must retain the last frame, not close the running match.
        if (!Files.isRegularFile(file)) return lastState;
        try {
            var stamp=Files.getLastModifiedTime(file);if(stamp.equals(stateStamp))return lastState;
            State value=JSON.fromJson(Files.readString(file), State.class);
            if(value!=null){lastState=value;stateStamp=stamp;synchronized(this){while(!queuedAcks.isEmpty()&&queuedAcks.peek().frame<=value.frame())acknowledgedWorld=Math.max(acknowledgedWorld,queuedAcks.remove().token);prune(value);}}
        } catch(NoSuchFileException | AccessDeniedException transientReplace) { }
        return lastState;
    }
    private void prune(State state){
        if(cleanup!=null&&cleanup.isAlive())return;
        long commands=Math.max(0,state.consumed()-512),frames=mode==Mode.LOCAL?0:Math.max(0,state.frame()-512),orders=Math.max(0,outgoing-256);
        if(commands<=prunedCommands&&frames<=prunedFrames&&orders<=prunedOrders)return;
        // Retain recent diagnostics, and prune only acknowledged files in this owned UUID
        // session. Disk/antivirus work never runs on the Minecraft client/server tick.
        cleanup=Thread.ofVirtual().name("zhmc-transport-cleanup").start(()->{
            prunedCommands=pruneFiles("commands",".cmd",prunedCommands,commands);
            prunedFrames=pruneFiles("frames",".frame",prunedFrames,frames);
            prunedOrders=pruneFiles("outbox",".msg",prunedOrders,orders);
        });
    }
    private long pruneFiles(String folder,String suffix,long from,long until){
        for(int budget=0;budget<128&&from<until;budget++){
            try{Files.deleteIfExists(directory.resolve(folder+"/"+from+suffix));from++;}
            catch(IOException transientBusy){break;}
        }
        return from;
    }
    public long start(String map, String faction, String enemy, int seed, int cash) throws IOException {
        return send("START " + JSON.toJson(map) + " " + JSON.toJson(faction) + " " + JSON.toJson(enemy) + " " + seed + " " + cash);
    }
    public long start(String map,String faction,String enemy,int seed,int cash,int color) throws IOException {return send("START "+JSON.toJson(map)+" "+JSON.toJson(faction)+" "+JSON.toJson(enemy)+" "+seed+" "+cash+" "+color);}
    public void select(int id) throws IOException { send("SELECT " + id); }
    public void move(double x, double y, double z) throws IOException { send("MOVE " + x + " " + y + " " + z); }
    public void mouse(int message, int x, int y, int buttons, int wheel) throws IOException { send("MOUSE " + message + " " + x + " " + y + " " + buttons + " " + wheel); }
    public void key(int scanCode, boolean down) throws IOException { send("KEY " + scanCode + " " + (down ? 1 : 0)); }
    public boolean alive() { return process.isAlive(); }
    public boolean awaitExit(long millis) throws InterruptedException {return process.waitFor(millis,java.util.concurrent.TimeUnit.MILLISECONDS);}
    @Override public synchronized void close() {
        if(closed)return;closed=true;
        if (!process.isAlive()) { Runtime.getRuntime().removeShutdownHook(shutdown); return; }
        try { send("QUIT"); } catch (IOException ignored) {}
        // Cleanup runs away from the Minecraft thread. No process name matching.
        Thread.ofVirtual().name("zhmc-native-shutdown").start(() -> {
            try {
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroy();
                if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly();
                Runtime.getRuntime().removeShutdownHook(shutdown);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
    }
}
