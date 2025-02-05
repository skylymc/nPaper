package net.minecraft.server;

import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
// CraftBukkit start
import java.io.IOException;
import java.net.Proxy;
import java.security.KeyPair;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.World.Environment;
import org.bukkit.craftbukkit.SpigotTimings; // Spigot
import org.bukkit.craftbukkit.util.Waitable;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.world.WorldSaveEvent;

import jline.console.ConsoleReader;
import joptsimple.OptionSet;
import net.minecraft.util.com.mojang.authlib.GameProfile;
import net.minecraft.util.com.mojang.authlib.GameProfileRepository;
import net.minecraft.util.com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.util.com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.util.org.apache.commons.lang3.Validate;
// CraftBukkit end

public abstract class MinecraftServer implements ICommandListener, Runnable, IMojangStatistics {

    private static final Logger i = LogManager.getLogger(MinecraftServer.class);
    private static final File a = new File("usercache.json");
    private static MinecraftServer j;
    public Convertable convertable; // CraftBukkit - private final -> public
    private final MojangStatisticsGenerator l = new MojangStatisticsGenerator("server", this, ar());
    public File universe; // CraftBukkit - private final -> public
    private final List n = new ArrayList();
    private final ICommandHandler o;
    public final MethodProfiler methodProfiler = new MethodProfiler();
    private ServerConnection p; // Spigot
    private final ServerPing q = new ServerPing();
    private final Random r = new Random();
    private String serverIp;
    private int t = -1;
    public WorldServer[] worldServer;
    private PlayerList u;
    private boolean isRunning = true;
    private boolean isStopped;
    private int ticks;
    protected final Proxy d;
    public String e;
    public int f;
    private boolean onlineMode;
    private boolean spawnAnimals;
    private boolean spawnNPCs;
    private boolean pvpMode;
    private boolean allowFlight;
    private String motd;
    private int E;
    private int F = 0;
    public final long[] g = new long[100];
    public long[][] h;
    private KeyPair G;
    private String H;
    private String I;
    private boolean demoMode;
    private boolean L;
    private boolean M;
    private String N = "";
    private boolean O;
    private long P;
    private String Q;
    private boolean R;
    private boolean S;
    private final YggdrasilAuthenticationService T;
    private final MinecraftSessionService U;
    private long V = 0L;
    private final GameProfileRepository W;
    private final UserCache X;

    // CraftBukkit start - add fields
    public List<WorldServer> worlds = new ArrayList<WorldServer>();
    public org.bukkit.craftbukkit.CraftServer server;
    public OptionSet options;
    public org.bukkit.command.ConsoleCommandSender console;
    public org.bukkit.command.RemoteConsoleCommandSender remoteConsole;
    public ConsoleReader reader;
    public static int currentTick = 0; // PaperSpigot - Further improve tick loop
    public final Thread primaryThread;
    public java.util.Queue<Runnable> processQueue = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
    public int autosavePeriod;
    // CraftBukkit end

    public MinecraftServer(OptionSet options, Proxy proxy) { // CraftBukkit - signature file -> OptionSet
        net.minecraft.util.io.netty.util.ResourceLeakDetector.setEnabled( false ); // Spigot - disable
        this.X = new UserCache(this, a);
        j = this;
        this.d = proxy;
        // this.universe = file1; // CraftBukkit
        // this.p = new ServerConnection(this); // Spigot
        this.o = new CommandDispatcher();
        // this.convertable = new WorldLoaderServer(file1); // CraftBukkit - moved to DedicatedServer.init
        this.T = new YggdrasilAuthenticationService(proxy, UUID.randomUUID().toString());
        this.U = this.T.createMinecraftSessionService();
        this.W = this.T.createProfileRepository();
        // CraftBukkit start
        this.options = options;
        // Try to see if we're actually running in a terminal, disable jline if not
        if (System.console() == null) {
            System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
            org.bukkit.craftbukkit.Main.useJline = false;
        }

        try {
            this.reader = new ConsoleReader(System.in, System.out);
            this.reader.setExpandEvents(false); // Avoid parsing exceptions for uncommonly used event designators
        } catch (Throwable e) {
            try {
                // Try again with jline disabled for Windows users without C++ 2008 Redistributable
                System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
                System.setProperty("user.language", "en");
                org.bukkit.craftbukkit.Main.useJline = false;
                this.reader = new ConsoleReader(System.in, System.out);
                this.reader.setExpandEvents(false);
            } catch (IOException ex) {
                i.warn((String) null, ex);
            }
        }
        Runtime.getRuntime().addShutdownHook(new org.bukkit.craftbukkit.util.ServerShutdownThread(this));

        primaryThread = new ThreadServerApplication(this, "Server thread"); // Moved from main
    }

    public abstract PropertyManager getPropertyManager();
    // CraftBukkit end

    protected abstract boolean init() throws java.net.UnknownHostException; // CraftBukkit - throws UnknownHostException

    protected void a(String s) {
        if (this.getConvertable().isConvertable(s)) {
            i.info("Converting map!");
            this.b("menu.convertingLevel");
            this.getConvertable().convert(s, new ConvertProgressUpdater(this));
        }
    }

    protected synchronized void b(String s) {
        this.Q = s;
    }

    protected void a(String s, String s1, long i, WorldType worldtype, String s2) {
        this.a(s);
        this.b("menu.loadingLevel");
        this.worldServer = new WorldServer[3];
        // this.h = new long[this.worldServer.length][100]; // CraftBukkit - Removed ticktime arrays
        // IDataManager idatamanager = this.convertable.a(s, true);
        // WorldData worlddata = idatamanager.getWorldData();
        /* CraftBukkit start - Removed worldsettings
        WorldSettings worldsettings;

        if (worlddata == null) {
            worldsettings = new WorldSettings(i, this.getGamemode(), this.getGenerateStructures(), this.isHardcore(), worldtype);
            worldsettings.a(s2);
        } else {
            worldsettings = new WorldSettings(worlddata);
        }

        if (this.L) {
            worldsettings.a();
        }
        // */
        int worldCount = 3;

        for (int j = 0; j < worldCount; ++j) {
            WorldServer world;
            int dimension = 0;

            if (j == 1) {
                if (this.getAllowNether()) {
                    dimension = -1;
                } else {
                    continue;
                }
            }

            if (j == 2) {
                if (this.server.getAllowEnd()) {
                    dimension = 1;
                } else {
                    continue;
                }
            }

            String worldType = Environment.getEnvironment(dimension).toString().toLowerCase();
            String name = (dimension == 0) ? s : s + "_" + worldType;

            org.bukkit.generator.ChunkGenerator gen = this.server.getGenerator(name);
            WorldSettings worldsettings = new WorldSettings(i, this.getGamemode(), this.getGenerateStructures(), this.isHardcore(), worldtype);
            worldsettings.a(s2);

            if (j == 0) {
                IDataManager idatamanager = new ServerNBTManager(server.getWorldContainer(), s1, true);
                if (this.R()) {
                    world = new DemoWorldServer(this, idatamanager, s1, dimension, this.methodProfiler);
                } else {
                    // world =, b0 to dimension, added Environment and gen
                    world = new WorldServer(this, idatamanager, s1, dimension, worldsettings, this.methodProfiler, Environment.getEnvironment(dimension), gen);
                }
                this.server.scoreboardManager = new org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager(this, world.getScoreboard());
            } else {
                String dim = "DIM" + dimension;

                File newWorld = new File(new File(name), dim);
                File oldWorld = new File(new File(s), dim);

                if ((!newWorld.isDirectory()) && (oldWorld.isDirectory())) {
                    MinecraftServer.i.info("---- Migration of old " + worldType + " folder required ----");
                    MinecraftServer.i.info("Unfortunately due to the way that Minecraft implemented multiworld support in 1.6, Bukkit requires that you move your " + worldType + " folder to a new location in order to operate correctly.");
                    MinecraftServer.i.info("We will move this folder for you, but it will mean that you need to move it back should you wish to stop using Bukkit in the future.");
                    MinecraftServer.i.info("Attempting to move " + oldWorld + " to " + newWorld + "...");

                    if (newWorld.exists()) {
                        MinecraftServer.i.warn("A file or folder already exists at " + newWorld + "!");
                        MinecraftServer.i.info("---- Migration of old " + worldType + " folder failed ----");
                    } else if (newWorld.getParentFile().mkdirs()) {
                        if (oldWorld.renameTo(newWorld)) {
                            MinecraftServer.i.info("Success! To restore " + worldType + " in the future, simply move " + newWorld + " to " + oldWorld);
                            // Migrate world data too.
                            try {
                                com.google.common.io.Files.copy(new File(new File(s), "level.dat"), new File(new File(name), "level.dat"));
                            } catch (IOException exception) {
                                MinecraftServer.i.warn("Unable to migrate world data.");
                            }
                            MinecraftServer.i.info("---- Migration of old " + worldType + " folder complete ----");
                        } else {
                            MinecraftServer.i.warn("Could not move folder " + oldWorld + " to " + newWorld + "!");
                            MinecraftServer.i.info("---- Migration of old " + worldType + " folder failed ----");
                        }
                    } else {
                        MinecraftServer.i.warn("Could not create path for " + newWorld + "!");
                        MinecraftServer.i.info("---- Migration of old " + worldType + " folder failed ----");
                    }
                }

                IDataManager idatamanager = new ServerNBTManager(server.getWorldContainer(), name, true);
                // world =, b0 to dimension, s1 to name, added Environment and gen
                world = new SecondaryWorldServer(this, idatamanager, name, dimension, worldsettings, this.worlds.get(0), this.methodProfiler, Environment.getEnvironment(dimension), gen);
            }

            if (gen != null) {
                world.getWorld().getPopulators().addAll(gen.getDefaultPopulators(world.getWorld()));
            }

            this.server.getPluginManager().callEvent(new org.bukkit.event.world.WorldInitEvent(world.getWorld()));

            world.addIWorldAccess(new WorldManager(this, world));
            if (!this.N()) {
                world.getWorldData().setGameType(this.getGamemode());
            }

            this.worlds.add(world);
            this.u.setPlayerFileData(this.worlds.toArray(new WorldServer[this.worlds.size()]));
            // CraftBukkit end
        }

        this.a(this.getDifficulty());
        this.g();
    }

    protected void g() {
        /*boolean flag = true;
        boolean flag1 = true;
        boolean flag2 = true;
        boolean flag3 = true;*/
        int i = 0;

        this.b("menu.generatingTerrain");
        //byte b0 = 0;

        // CraftBukkit start - fire WorldLoadEvent and handle whether or not to keep the spawn in memory
        for (int m = 0; m < this.worlds.size(); ++m) {
            WorldServer worldserver = this.worlds.get(m);
            MinecraftServer.i.info("Preparing start region for level " + m + " (Seed: " + worldserver.getSeed() + ")");
            if (!worldserver.getWorld().getKeepSpawnInMemory()) {
                continue;
            }

            ChunkCoordinates chunkcoordinates = worldserver.getSpawn();
            long j = ar();
            i = 0;

            for (int k = -192; k <= 192 && this.isRunning(); k += 16) {
                for (int l = -192; l <= 192 && this.isRunning(); l += 16) {
                    long i1 = ar();

                    if (i1 - j > 1000L) {
                        this.a_("Preparing spawn area", i * 100 / 625);
                        j = i1;
                    }

                    ++i;
                    worldserver.chunkProviderServer.getChunkAt(chunkcoordinates.x + k >> 4, chunkcoordinates.z + l >> 4);
                }
            }
        }

        for (WorldServer world : this.worlds) {
            this.server.getPluginManager().callEvent(new org.bukkit.event.world.WorldLoadEvent(world.getWorld()));
        }
        // CraftBukkit end
        this.n();
    }

    public abstract boolean getGenerateStructures();

    public abstract EnumGamemode getGamemode();

    public abstract EnumDifficulty getDifficulty();

    public abstract boolean isHardcore();

    public abstract int l();

    public abstract boolean m();

    protected void a_(String s, int i) {
        this.e = s;
        this.f = i;
        // CraftBukkit - Use FQN to work around decompiler issue
        MinecraftServer.i.info(s + ": " + i + "%");
    }

    protected void n() {
        this.e = null;
        this.f = 0;

        this.server.enablePlugins(org.bukkit.plugin.PluginLoadOrder.POSTWORLD); // CraftBukkit
    }

    protected void saveChunks(boolean flag) throws ExceptionWorldConflict { // CraftBukkit - added throws
        if (!this.M) {
            // CraftBukkit start - fire WorldSaveEvent
            // WorldServer[] aworldserver = this.worldServer;
            //int i = this.worlds.size();

            for (WorldServer worldserver : this.worlds) {

                if (worldserver != null) {
                    if (!flag) {
                        MinecraftServer.i.info("Saving chunks for level \'" + worldserver.getWorldData().getName() + "\'/" + worldserver.worldProvider.getName());
                    }

                    worldserver.save(true, (IProgressUpdate) null);
                    worldserver.saveLevel();

                    WorldSaveEvent event = new WorldSaveEvent(worldserver.getWorld());
                    this.server.getPluginManager().callEvent(event);
                    // CraftBukkit end
                }
            }
        }
    }

    public void stop() throws ExceptionWorldConflict { // CraftBukkit - added throws
        if (!this.M) {
            i.info("Stopping server");
            // CraftBukkit start
            if (this.server != null) {
                this.server.disablePlugins();
            }
            // CraftBukkit end

            if (this.ai() != null) {
                this.ai().b();
            }

            if (this.u != null) {
                i.info("Saving players");
                this.u.savePlayers();
                this.u.u();
            }

            if (this.worldServer != null) {
                i.info("Saving worlds");
                this.saveChunks(false);
            
                /* CraftBukkit start - Handled in saveChunks
                for (int i = 0; i < this.worldServer.length; ++i) {
                    WorldServer worldserver = this.worldServer[i];

                    worldserver.saveLevel();
                }
                // CraftBukkit end */
            }

            if (this.l.d()) {
                this.l.e();
            }
            // Spigot start
            if( org.spigotmc.SpigotConfig.saveUserCacheOnStopOnly )
            {
                i.info("Saving usercache.json");
                this.X.c();
            }
            //Spigot end
        }
    }

    public String getServerIp() {
        return this.serverIp;
    }

    public void c(String s) {
        this.serverIp = s;
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    public void safeShutdown() {
        this.isRunning = false;
    }

    // PaperSpigot start - Further improve tick loop
    private static final int TPS = 20;
    private static final long SEC_IN_NANO = 1_000_000_000;
    private static final long TICK_TIME = SEC_IN_NANO / TPS;
    private static final long MAX_CATCHUP_BUFFER = TICK_TIME * TPS * 60L;
    private static final int SAMPLE_INTERVAL = 20;
    public final RollingAverage tps1 = new RollingAverage(60);
    public final RollingAverage tps5 = new RollingAverage(60*5);
    public final RollingAverage tps15 = new RollingAverage(60*15);
    public double[] recentTps = new double[ 3 ]; // PaperSpigot - Fine have your darn compat with bad plugins
    public static long START_TIME, LAST_TICK_TIME_NANO, LAST_TICK_TIME_MILLIS;

    public static class RollingAverage {
    	private final int size;
        private double total;
        private int index = 0;
        private final double[] samples;
        private final long[] times;

        RollingAverage(int size) {
            this.size = size;
            this.total = TPS * SEC_IN_NANO * size;
            this.samples = new double[size];
            this.times = new long[size];
            for (int i = 0; i < size; i++) {
                this.samples[i] = TPS;
                this.times[i] = SEC_IN_NANO;
            }
        }

        public void add(double x, long t) {
            index = (index + 1) % size;
            total -= samples[index] * times[index];
            samples[index] = x;
            times[index] = t;
            total += x * t;
        }

        public double getAverage() {
            double avg = total;
            for (int i = 0; i < size; i++) {
                avg += samples[i] * (SEC_IN_NANO - times[i]);
            }
            return avg / (SEC_IN_NANO * size);
        }
    }
    // PaperSpigot End
 
    public void run() {
        try {
            if (this.init()) {
                //long i = ar();
                //long j = 0L;

                this.q.setMOTD(new ChatComponentText(this.motd));
                this.q.setServerInfo(new ServerPingServerData("1.7.10", 5));
                this.a(this.q);

                // Spigot start
                // PaperSpigot start - Further improve tick loop
                Arrays.fill( recentTps, 20 );
                //long lastTick = System.nanoTime(), catchupTime = 0, curTime, wait, tickSection = lastTick;
                long start = System.nanoTime(), lastTick = start - TICK_TIME, catchupTime = 0, curTime, wait, tickSection = start;
                // PaperSpigot end
                START_TIME = System.currentTimeMillis();
                while (this.isRunning) {
                    curTime = System.nanoTime();
                    // PaperSpigot start - Further improve tick loop
                    wait = TICK_TIME - (curTime - lastTick);
                    if (wait > 0) {
                        if (catchupTime < 2E6) {
                            wait += Math.abs(catchupTime);
                        } else if (wait < catchupTime) {
                            catchupTime -= wait;
                            wait = 0;
                        } else {
                            wait -= catchupTime;
                            catchupTime = 0;
                        }
                    }
                    if (wait > 0) {
                        Thread.sleep(wait / 1000000);
                        curTime = System.nanoTime();
                        wait = TICK_TIME - (curTime - lastTick);
                    }

                    catchupTime = Math.min(MAX_CATCHUP_BUFFER, catchupTime - wait);
                    // Paperspigot end

                    if ( ++MinecraftServer.currentTick % SAMPLE_INTERVAL == 0 ) // PaperSpigot - Further improve tick loop
                    {
                        // PaperSpigot start - Further improve tick loop
                        final long diff = curTime - tickSection;
                        double currentTps = 1E9 / diff * SAMPLE_INTERVAL;
                        tps1.add(currentTps, diff);
                        tps5.add(currentTps, diff);
                        tps15.add(currentTps, diff);
                        // Backwards compat with bad plugins
                        recentTps[0] = tps1.getAverage();
                        recentTps[1] = tps5.getAverage();
                        recentTps[2] = tps15.getAverage();
                        tickSection = curTime;
                        // PaperSpigot end
                    }
                    lastTick = curTime;
                    LAST_TICK_TIME_NANO = System.nanoTime();
                    LAST_TICK_TIME_MILLIS = System.currentTimeMillis();

                    this.u();
                    this.O = true;
                }
                // Spigot end
            } else {
                this.a((CrashReport) null);
            }
        } catch (Throwable throwable) {
            i.error("Encountered an unexpected exception", throwable);
            // Spigot Start
            if ( throwable.getCause() != null )
            {
                i.error( "\tCause of unexpected exception was", throwable.getCause() );
            }
            // Spigot End
            CrashReport crashreport = null;

            if (throwable instanceof ReportedException) {
                crashreport = this.b(((ReportedException) throwable).a());
            } else {
                crashreport = this.b(new CrashReport("Exception in server tick loop", throwable));
            }

            File file1 = new File(new File(this.s(), "crash-reports"), "crash-" + (new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")).format(new Date()) + "-server.txt");

            if (crashreport.a(file1)) {
                i.error("This crash report has been saved to: " + file1.getAbsolutePath());
            } else {
                i.error("We were unable to save this crash report to disk.");
            }

            this.a(crashreport);
        } finally {
            try {
                org.spigotmc.WatchdogThread.doStop();
                this.stop();
                this.isStopped = true;
            } catch (Throwable throwable1) {
                i.error("Exception stopping the server", throwable1);
            } finally {
                // CraftBukkit start - Restore terminal to original settings
                try {
                    this.reader.getTerminal().restore();
                } catch (Exception e) {
                }
                // CraftBukkit end
                this.t();
            }
        }
    }

    private void a(ServerPing serverping) {
        File file1 = this.d("server-icon.png");

        if (file1.isFile()) {
            try {
            	final BufferedImage bufferedimage = ImageIO.read(file1);
                Validate.validState(bufferedimage.getWidth() == bufferedimage.getHeight(), "Width must be equals to the height");
                Validate.validState(bufferedimage.getWidth() == 64, "Must be 64 pixels wide");
                Validate.validState(bufferedimage.getHeight() == 64, "Must be 64 pixels high");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bufferedimage, "PNG", baos);
                byte[] imageInByte = baos.toByteArray();
                String imageDataString = Base64.getEncoder().encodeToString(imageInByte);
                serverping.setFavicon("data:image/png;base64," + imageDataString);
            } catch (Exception exception) {
                i.error("Couldn\'t load server icon", exception);
            }
        }
    }

    protected File s() {
        return new File(".");
    }

    protected void a(CrashReport crashreport) {}

    protected void t() {}

    protected void u() throws ExceptionWorldConflict { // CraftBukkit - added throws
        SpigotTimings.serverTickTimer.startTiming(); // Spigot
        long i = System.nanoTime();

        ++this.ticks;
        if (this.R) {
            this.R = false;
            this.methodProfiler.a = true;
            this.methodProfiler.a();
        }

        this.methodProfiler.a("root");
        this.v();
        if (i - this.V >= 5000000000L) {
            this.V = i;
            this.q.setPlayerSample(new ServerPingPlayerSample(this.D(), this.C()));
            GameProfile[] agameprofile = new GameProfile[Math.min(this.C(), 12)];
            int j = MathHelper.nextInt(this.r, 0, this.C() - agameprofile.length);

            for (int k = 0; k < agameprofile.length; ++k) {
                agameprofile[k] = ((EntityPlayer) this.u.players.get(j + k)).getProfile();
            }

            Collections.shuffle(Arrays.asList(agameprofile));
            this.q.b().a(agameprofile);
        }

        if ((this.autosavePeriod > 0) && ((this.ticks % this.autosavePeriod) == 0)) { // CraftBukkit
            SpigotTimings.worldSaveTimer.startTiming(); // Spigot
            this.methodProfiler.a("save");
            this.u.savePlayers();
            // Spigot Start
            // We replace this with saving each individual world as this.saveChunks(...) is broken,
            // and causes the main thread to sleep for random amounts of time depending on chunk activity
            // Also pass flag to only save modified chunks -- PaperSpigot
            server.playerCommandState = true;
            for (World world : worlds) {
                world.getWorld().save(true);
            }
            server.playerCommandState = false;
            // this.saveChunks(true);
            // Spigot End
            this.methodProfiler.b();
            SpigotTimings.worldSaveTimer.stopTiming(); // Spigot
        }

        this.methodProfiler.a("tallying");
        this.g[this.ticks % 100] = System.nanoTime() - i;
        this.methodProfiler.b();
        this.methodProfiler.a("snooper");
        if (getSnooperEnabled() && !this.l.d() && this.ticks > 100) { // Spigot
            this.l.a();
        }

        if (getSnooperEnabled() && this.ticks % 6000 == 0) { // Spigot
            this.l.b();
        }

        this.methodProfiler.b();
        //this.methodProfiler.b();
        org.spigotmc.WatchdogThread.tick(); // Spigot
        SpigotTimings.serverTickTimer.stopTiming(); // Spigot
        org.spigotmc.CustomTimingsHandler.tick(); // Spigot
    }

    public void v() {
        this.methodProfiler.a("levels");

        SpigotTimings.schedulerTimer.startTiming(); // Spigot
        // CraftBukkit start
        this.server.getScheduler().mainThreadHeartbeat(this.ticks);
        SpigotTimings.schedulerTimer.stopTiming(); // Spigot

        // Run tasks that are waiting on processing
        SpigotTimings.processQueueTimer.startTiming(); // Spigot
        while (!processQueue.isEmpty()) {
            processQueue.remove().run();
        }
        SpigotTimings.processQueueTimer.stopTiming(); // Spigot

        SpigotTimings.chunkIOTickTimer.startTiming(); // Spigot
        org.bukkit.craftbukkit.chunkio.ChunkIOExecutor.tick();
        SpigotTimings.chunkIOTickTimer.stopTiming(); // Spigot

        SpigotTimings.timeUpdateTimer.startTiming(); // Spigot
        // Send time updates to everyone, it will get the right time from the world the player is in.
        if ((this.ticks % 20) == 0) {
	        for (final WorldServer world : this.worlds) {
	            final boolean doDaylight = world.getGameRules().getBoolean("doDaylightCycle");
	            final long dayTime = world.getDayTime();
	            long worldTime = world.getTime();
	            final PacketPlayOutUpdateTime worldPacket = new PacketPlayOutUpdateTime(worldTime, dayTime, doDaylight);
	            for (EntityHuman entityhuman : (List<EntityHuman>) world.players) {
	            	if (!(entityhuman instanceof EntityPlayer)) {//|| (ticks + entityhuman.getId()) % 20 != 0
	                    continue;
	                }
	            	if (entityhuman.world != world) {
	            		continue;
	            	}
	                EntityPlayer entityplayer = (EntityPlayer) entityhuman;
	                long playerTime = entityplayer.getPlayerTime();
	                PacketPlayOutUpdateTime packet = (playerTime == dayTime) ? worldPacket : new PacketPlayOutUpdateTime(worldTime, playerTime, doDaylight);
	                entityplayer.playerConnection.sendPacket(packet); // Add support for per player time
	            }
	        }
        }
        SpigotTimings.timeUpdateTimer.stopTiming(); // Spigot

        int i;

        for (i = 0; i < this.worlds.size(); ++i) {
            long j = System.nanoTime();

            // if (i == 0 || this.getAllowNether()) {
                WorldServer worldserver = this.worlds.get(i);

                this.methodProfiler.a(worldserver.getWorldData().getName());
                this.methodProfiler.a("pools");
                this.methodProfiler.b();
                /* Drop global time updates
                if (this.ticks % 20 == 0) {
                    this.methodProfiler.a("timeSync");
                    this.t.a(new PacketPlayOutUpdateTime(worldserver.getTime(), worldserver.getDayTime(), worldserver.getGameRules().getBoolean("doDaylightCycle")), worldserver.worldProvider.dimension);
                    this.methodProfiler.b();
                }
                // CraftBukkit end */

                this.methodProfiler.a("tick");

                CrashReport crashreport;

                try {
                    worldserver.timings.doTick.startTiming(); // Spigot
                    worldserver.doTick();
                    worldserver.timings.doTick.stopTiming(); // Spigot
                } catch (Throwable throwable) {
                    // Spigot Start
                    try {
                    crashreport = CrashReport.a(throwable, "Exception ticking world");
                    } catch (Throwable t){
                        throw new RuntimeException("Error generating crash report", t);
                    }
                    // Spigot End
                    worldserver.a(crashreport);
                    throw new ReportedException(crashreport);
                }

                try {
                    worldserver.timings.tickEntities.startTiming(); // Spigot
                    worldserver.tickEntities();
                    worldserver.timings.tickEntities.stopTiming(); // Spigot
                } catch (Throwable throwable1) {
                    // Spigot Start
                    try {
                    crashreport = CrashReport.a(throwable1, "Exception ticking world entities");
                    } catch (Throwable t){
                        throw new RuntimeException("Error generating crash report", t);
                    }
                    // Spigot End
                    worldserver.a(crashreport);
                    throw new ReportedException(crashreport);
                }

                this.methodProfiler.b();
                this.methodProfiler.a("tracker");
                worldserver.timings.tracker.startTiming(); // Spigot
                if (u.players.size() > 0) worldserver.getTracker().updatePlayers();
                worldserver.timings.tracker.stopTiming(); // Spigot
                this.methodProfiler.b();
                //this.methodProfiler.b();
                worldserver.explosionDensityCache.clear(); // PaperSpigot - Optimize explosions
            // } // CraftBukkit

            // this.h[i][this.ticks % 100] = System.nanoTime() - j; // CraftBukkit
        }

        this.methodProfiler.c("connection");
        SpigotTimings.connectionTimer.startTiming(); // Spigot
        this.ai().c();
        SpigotTimings.connectionTimer.stopTiming(); // Spigot
        this.methodProfiler.c("players");
        SpigotTimings.playerListTimer.startTiming(); // Spigot
        this.u.tick();
        SpigotTimings.playerListTimer.stopTiming(); // Spigot
        this.methodProfiler.c("tickables");

        SpigotTimings.tickablesTimer.startTiming(); // Spigot
        for (i = 0; i < this.n.size(); ++i) {
            ((IUpdatePlayerListBox) this.n.get(i)).a();
        }
        SpigotTimings.tickablesTimer.stopTiming(); // Spigot

        this.methodProfiler.b();
    }

    public boolean getAllowNether() {
        return true;
    }

    public void a(IUpdatePlayerListBox iupdateplayerlistbox) {
        this.n.add(iupdateplayerlistbox);
    }

    public static void main(final OptionSet options) { // CraftBukkit - replaces main(String[] astring)
        DispenserRegistry.b();
        org.spigotmc.ProtocolInjector.inject();

        try {
            /* CraftBukkit start - Replace everything
            boolean flag = true;
            String s = null;
            String s1 = ".";
            String s2 = null;
            boolean flag1 = false;
            boolean flag2 = false;
            int i = -1;

            for (int j = 0; j < astring.length; ++j) {
                String s3 = astring[j];
                String s4 = j == astring.length - 1 ? null : astring[j + 1];
                boolean flag3 = false;

                if (!s3.equals("nogui") && !s3.equals("--nogui")) {
                    if (s3.equals("--port") && s4 != null) {
                        flag3 = true;

                        try {
                            i = Integer.parseInt(s4);
                        } catch (NumberFormatException numberformatexception) {
                            ;
                        }
                    } else if (s3.equals("--singleplayer") && s4 != null) {
                        flag3 = true;
                        s = s4;
                    } else if (s3.equals("--universe") && s4 != null) {
                        flag3 = true;
                        s1 = s4;
                    } else if (s3.equals("--world") && s4 != null) {
                        flag3 = true;
                        s2 = s4;
                    } else if (s3.equals("--demo")) {
                        flag1 = true;
                    } else if (s3.equals("--bonusChest")) {
                        flag2 = true;
                    }
                } else {
                    flag = false;
                }

                if (flag3) {
                    ++j;
                }
            }

            DedicatedServer dedicatedserver = new DedicatedServer(new File(s1));

            if (s != null) {
                dedicatedserver.j(s);
            }

            if (s2 != null) {
                dedicatedserver.k(s2);
            }

            if (i >= 0) {
                dedicatedserver.setPort(i);
            }

            if (flag1) {
                dedicatedserver.b(true);
            }

            if (flag2) {
                dedicatedserver.c(true);
            }

            if (flag) {
                dedicatedserver.aD();
            }
            // */

            DedicatedServer dedicatedserver = new DedicatedServer(options);

            if (options.has("port")) {
                int port = (Integer) options.valueOf("port");
                if (port > 0) {
                    dedicatedserver.setPort(port);
                }
            }

            if (options.has("universe")) {
                dedicatedserver.universe = (File) options.valueOf("universe");
            }

            if (options.has("world")) {
                dedicatedserver.k((String) options.valueOf("world"));
            }

            dedicatedserver.primaryThread.start();
            // Runtime.getRuntime().addShutdownHook(new ThreadShutdown("Server Shutdown Thread", dedicatedserver));
            // CraftBukkit end
        } catch (Exception exception) {
            i.fatal("Failed to start the minecraft server", exception);
        }
    }

    public void x() {
        // (new ThreadServerApplication(this, "Server thread")).start(); // CraftBukkit - prevent abuse
    }

    public File d(String s) {
        return new File(this.s(), s);
    }

    public void info(String s) {
        i.info(s);
    }

    public void warning(String s) {
        i.warn(s);
    }

    public WorldServer getWorldServer(int i) {
        // CraftBukkit start
        for (WorldServer world : this.worlds) {
            if (world.dimension == i) {
                return world;
            }
        }

        return this.worlds.get(0);
        // CraftBukkit end
    }

    public String y() {
        return this.serverIp;
    }

    public int z() {
        return this.t;
    }

    public String A() {
        return this.motd;
    }

    public String getVersion() {
        return "1.7.10";
    }

    public int C() {
        return this.u.getPlayerCount();
    }

    public int D() {
        return this.u.getMaxPlayers();
    }

    public String[] getPlayers() {
        return this.u.f();
    }

    public GameProfile[] F() {
        return this.u.g();
    }

    public String getPlugins() {
        // CraftBukkit start - Whole method
        StringBuilder result = new StringBuilder();
        org.bukkit.plugin.Plugin[] plugins = server.getPluginManager().getPlugins();

        result.append(server.getName());
        result.append(" on Bukkit ");
        result.append(server.getBukkitVersion());

        if (plugins.length > 0 && this.server.getQueryPlugins()) {
            result.append(": ");

            for (int i = 0; i < plugins.length; i++) {
                if (i > 0) {
                    result.append("; ");
                }

                result.append(plugins[i].getDescription().getName());
                result.append(" ");
                result.append(plugins[i].getDescription().getVersion().replaceAll(";", ","));
            }
        }

        return result.toString();
        // CraftBukkit end
    }

    // CraftBukkit start - fire RemoteServerCommandEvent
    public String g(final String s) { // final parameter
        Waitable<String> waitable = new Waitable<String>() {
            @Override
            protected String evaluate() {
                RemoteControlCommandListener.instance.e();
                // Event changes start
                RemoteServerCommandEvent event = new RemoteServerCommandEvent(MinecraftServer.this.remoteConsole, s);
                MinecraftServer.this.server.getPluginManager().callEvent(event);
                // Event changes end
                ServerCommand servercommand = new ServerCommand(event.getCommand(), RemoteControlCommandListener.instance);
                MinecraftServer.this.server.dispatchServerCommand(MinecraftServer.this.remoteConsole, servercommand); // CraftBukkit
                // this.o.a(RemoteControlCommandListener.instance, s);
                return RemoteControlCommandListener.instance.f();
            }};
        processQueue.add(waitable);
        try {
            return waitable.get();
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException("Exception processing rcon command " + s, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Maintain interrupted state
            throw new RuntimeException("Interrupted processing rcon command " + s, e);
        }
        // CraftBukkit end
    }

    public boolean isDebugging() {
        return this.getPropertyManager().getBoolean("debug", false); // CraftBukkit - don't hardcode
    }

    public void h(String s) {
        i.error(s);
    }

    public void i(String s) {
        if (this.isDebugging()) {
            i.info(s);
        }
    }

    public String getServerModName() {
        return "nPaper"; // PaperSpigot - PaperSpigot > // Spigot - Spigot > // CraftBukkit - cb > vanilla!
    }

    public CrashReport b(CrashReport crashreport) {
        crashreport.g().a("Profiler Position", new CrashReportProfilerPosition(this));
        if (this.worlds != null && this.worlds.size() > 0 && this.worlds.get(0) != null) { // CraftBukkit
            crashreport.g().a("Vec3 Pool Size", new CrashReportVec3DPoolSize(this));
        }

        if (this.u != null) {
            crashreport.g().a("Player Count", new CrashReportPlayerCount(this));
        }

        return crashreport;
    }

    public List a(ICommandListener icommandlistener, String s) {
        // CraftBukkit start - Allow tab-completion of Bukkit commands
        /*
        ArrayList arraylist = new ArrayList();

        if (s.startsWith("/")) {
            s = s.substring(1);
            boolean flag = !s.contains(" ");
            List list = this.o.b(icommandlistener, s);

            if (list != null) {
                Iterator iterator = list.iterator();

                while (iterator.hasNext()) {
                    String s1 = (String) iterator.next();

                    if (flag) {
                        arraylist.add("/" + s1);
                    } else {
                        arraylist.add(s1);
                    }
                }
            }

            return arraylist;
        } else {
            String[] astring = s.split(" ", -1);
            String s2 = astring[astring.length - 1];
            String[] astring1 = this.u.f();
            int i = astring1.length;

            for (int j = 0; j < i; ++j) {
                String s3 = astring1[j];

                if (CommandAbstract.a(s2, s3)) {
                    arraylist.add(s3);
                }
            }

            return arraylist;
        }
        */
        return this.server.tabComplete(icommandlistener, s);
        // CraftBukkit end
    }

    public static MinecraftServer getServer() {
        return j;
    }

    public String getName() {
        return "Server";
    }

    public void sendMessage(IChatBaseComponent ichatbasecomponent) {
        i.info(ichatbasecomponent.c());
    }

    public boolean a(int i, String s) {
        return true;
    }

    public ICommandHandler getCommandHandler() {
        return this.o;
    }

    public KeyPair K() {
        return this.G;
    }

    public int L() {
        return this.t;
    }

    public void setPort(int i) {
        this.t = i;
    }

    public String M() {
        return this.H;
    }

    public void j(String s) {
        this.H = s;
    }

    public boolean N() {
        return this.H != null;
    }

    public String O() {
        return this.I;
    }

    public void k(String s) {
        this.I = s;
    }

    public void a(KeyPair keypair) {
        this.G = keypair;
    }

    public void a(EnumDifficulty enumdifficulty) {
        // CraftBukkit start - Use worlds list for iteration
        for (WorldServer worldserver : this.worlds) {
            // CraftBukkit end

            if (worldserver != null) {
                if (worldserver.getWorldData().isHardcore()) {
                    worldserver.difficulty = EnumDifficulty.HARD;
                    worldserver.setSpawnFlags(true, true);
                } else if (this.N()) {
                    worldserver.difficulty = enumdifficulty;
                    worldserver.setSpawnFlags(worldserver.difficulty != EnumDifficulty.PEACEFUL, true);
                } else {
                    worldserver.difficulty = enumdifficulty;
                    worldserver.setSpawnFlags(this.getSpawnMonsters(), this.spawnAnimals);
                }
            }
        }
    }

    protected boolean getSpawnMonsters() {
        return true;
    }

    public boolean R() {
        return this.demoMode;
    }

    public void b(boolean flag) {
        this.demoMode = flag;
    }

    public void c(boolean flag) {
        this.L = flag;
    }

    public Convertable getConvertable() {
        return this.convertable;
    }

    public void U() {
        this.M = true;
        this.getConvertable().d();

        // CraftBukkit start
        for (WorldServer worldserver : this.worlds) {
            // CraftBukkit end

            if (worldserver != null) {
                worldserver.saveLevel();
            }
        }

        this.getConvertable().e(this.worlds.get(0).getDataManager().g()); // CraftBukkit
        this.safeShutdown();
    }

    public String getResourcePack() {
        return this.N;
    }

    public void setTexturePack(String s) {
        this.N = s;
    }

    public void a(MojangStatisticsGenerator mojangstatisticsgenerator) {
        mojangstatisticsgenerator.a("whitelist_enabled", Boolean.valueOf(false));
        mojangstatisticsgenerator.a("whitelist_count", Integer.valueOf(0));
        mojangstatisticsgenerator.a("players_current", Integer.valueOf(this.C()));
        mojangstatisticsgenerator.a("players_max", Integer.valueOf(this.D()));
        mojangstatisticsgenerator.a("players_seen", Integer.valueOf(this.u.getSeenPlayers().length));
        mojangstatisticsgenerator.a("uses_auth", Boolean.valueOf(this.onlineMode));
        mojangstatisticsgenerator.a("gui_state", this.ak() ? "enabled" : "disabled");
        mojangstatisticsgenerator.a("run_time", Long.valueOf((ar() - mojangstatisticsgenerator.g()) / 60L * 1000L));
        mojangstatisticsgenerator.a("avg_tick_ms", Integer.valueOf((int) (MathHelper.a(this.g) * 1.0E-6D)));
        int i = 0;

        // CraftBukkit start - use worlds list for iteration
        for (WorldServer worldserver : this.worlds) {
            if (worldServer != null) {
                // CraftBukkit end
                WorldData worlddata = worldserver.getWorldData();

                mojangstatisticsgenerator.a("world[" + i + "][dimension]", Integer.valueOf(worldserver.worldProvider.dimension));
                mojangstatisticsgenerator.a("world[" + i + "][mode]", worlddata.getGameType());
                mojangstatisticsgenerator.a("world[" + i + "][difficulty]", worldserver.difficulty);
                mojangstatisticsgenerator.a("world[" + i + "][hardcore]", Boolean.valueOf(worlddata.isHardcore()));
                mojangstatisticsgenerator.a("world[" + i + "][generator_name]", worlddata.getType().name());
                mojangstatisticsgenerator.a("world[" + i + "][generator_version]", Integer.valueOf(worlddata.getType().getVersion()));
                mojangstatisticsgenerator.a("world[" + i + "][height]", Integer.valueOf(this.E));
                mojangstatisticsgenerator.a("world[" + i + "][chunks_loaded]", Integer.valueOf(worldserver.L().getLoadedChunks()));
                ++i;
            }
        }

        mojangstatisticsgenerator.a("worlds", Integer.valueOf(i));
    }

    public void b(MojangStatisticsGenerator mojangstatisticsgenerator) {
        mojangstatisticsgenerator.b("singleplayer", Boolean.valueOf(this.N()));
        mojangstatisticsgenerator.b("server_brand", this.getServerModName());
        mojangstatisticsgenerator.b("gui_supported", GraphicsEnvironment.isHeadless() ? "headless" : "supported");
        mojangstatisticsgenerator.b("dedicated", Boolean.valueOf(this.X()));
    }

    public boolean getSnooperEnabled() {
        return true;
    }

    public abstract boolean X();

    public boolean getOnlineMode() {
        return this.server.getOnlineMode(); // CraftBukkit
    }

    public void setOnlineMode(boolean flag) {
        this.onlineMode = flag;
    }

    public boolean getSpawnAnimals() {
        return this.spawnAnimals;
    }

    public void setSpawnAnimals(boolean flag) {
        this.spawnAnimals = flag;
    }

    public boolean getSpawnNPCs() {
        return this.spawnNPCs;
    }

    public void setSpawnNPCs(boolean flag) {
        this.spawnNPCs = flag;
    }

    public boolean getPvP() {
        return this.pvpMode;
    }

    public void setPvP(boolean flag) {
        this.pvpMode = flag;
    }

    public boolean getAllowFlight() {
        return this.allowFlight;
    }

    public void setAllowFlight(boolean flag) {
        this.allowFlight = flag;
    }

    public abstract boolean getEnableCommandBlock();

    public String getMotd() {
        return this.motd;
    }

    public void setMotd(String s) {
        this.motd = s;
    }

    public int getMaxBuildHeight() {
        return this.E;
    }

    public void c(int i) {
        this.E = i;
    }

    public boolean isStopped() {
        return this.isStopped;
    }

    public PlayerList getPlayerList() {
        return this.u;
    }

    public void a(PlayerList playerlist) {
        this.u = playerlist;
    }

    public void a(EnumGamemode enumgamemode) {
        // CraftBukkit start - use worlds list for iteration
        for (World world : getServer().worlds) {
            world.getWorldData().setGameType(enumgamemode);
            // CraftBukkit end
        }
    }

    // Spigot Start
    public ServerConnection getServerConnection()
    {
        return this.p;
    }
    // Spigot End
    public ServerConnection ai() {
        return ( this.p ) == null ? this.p = new ServerConnection( this ) : this.p; // Spigot
    }

    public boolean ak() {
        return false;
    }

    public abstract String a(EnumGamemode enumgamemode, boolean flag);

    public int al() {
        return this.ticks;
    }

    public void am() {
        this.R = true;
    }

    public ChunkCoordinates getChunkCoordinates() {
        return new ChunkCoordinates(0, 0, 0);
    }

    public World getWorld() {
        return this.worlds.get(0); // CraftBukkit
    }

    public int getSpawnProtection() {
        return 16;
    }

    public boolean a(World world, int i, int j, int k, EntityHuman entityhuman) {
        return false;
    }

    public void setForceGamemode(boolean flag) {
        this.S = flag;
    }

    public boolean getForceGamemode() {
        return this.S;
    }

    public Proxy aq() {
        return this.d;
    }

    public static long ar() {
        return System.currentTimeMillis();
    }

    public int getIdleTimeout() {
        return this.F;
    }

    public void setIdleTimeout(int i) {
        this.F = i;
    }

    public IChatBaseComponent getScoreboardDisplayName() {
        return new ChatComponentText(this.getName());
    }

    public boolean at() {
        return true;
    }

    public MinecraftSessionService av() {
        return this.U;
    }

    public GameProfileRepository getGameProfileRepository() {
        return this.W;
    }

    public UserCache getUserCache() {
        return this.X;
    }

    public ServerPing ay() {
        return this.q;
    }

    public void az() {
        this.V = 0L;
    }

    public static Logger getLogger() {
        return i;
    }

    public static PlayerList a(MinecraftServer minecraftserver) {
        return minecraftserver.u;
    }
}
