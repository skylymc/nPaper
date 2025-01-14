package org.bukkit.craftbukkit;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.lang.Validate;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.Warning.WarningState;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.conversations.Conversable;
import org.bukkit.craftbukkit.command.VanillaCommandWrapper;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.help.SimpleHelpMap;
import org.bukkit.craftbukkit.inventory.CraftFurnaceRecipe;
import org.bukkit.craftbukkit.inventory.CraftInventoryCustom;
import org.bukkit.craftbukkit.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftShapedRecipe;
import org.bukkit.craftbukkit.inventory.CraftShapelessRecipe;
import org.bukkit.craftbukkit.inventory.RecipeIterator;
import org.bukkit.craftbukkit.map.CraftMapView;
import org.bukkit.craftbukkit.metadata.EntityMetadataStore;
import org.bukkit.craftbukkit.metadata.PlayerMetadataStore;
import org.bukkit.craftbukkit.metadata.WorldMetadataStore;
import org.bukkit.craftbukkit.potion.CraftPotionBrewer;
import org.bukkit.craftbukkit.scheduler.CraftScheduler;
import org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager;
import org.bukkit.craftbukkit.updater.AutoUpdater;
import org.bukkit.craftbukkit.updater.BukkitDLUpdaterService;
import org.bukkit.craftbukkit.util.CraftIconCache;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.util.DatFileFilter;
import org.bukkit.craftbukkit.util.Versioning;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChatTabCompleteEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.help.HelpMap;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLoadOrder;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.SimpleServicesManager;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.StandardMessenger;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitWorker;
import org.bukkit.util.StringUtil;
import org.bukkit.util.permissions.DefaultPermissions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import com.avaje.ebean.config.DataSourceConfig;
import com.avaje.ebean.config.ServerConfig;
import com.avaje.ebean.config.dbplatform.SQLitePlatform;
import com.avaje.ebeaninternal.server.lib.sql.TransactionIsolation;
import com.google.common.collect.ImmutableList;
import com.google.common.cache.CacheBuilder;

import jline.console.ConsoleReader;
import net.minecraft.server.ChunkCoordinates;
import net.minecraft.server.ChunkProviderServer;
import net.minecraft.server.ChunkRegionLoader;
import net.minecraft.server.CommandAchievement;
import net.minecraft.server.CommandBan;
import net.minecraft.server.CommandBanIp;
import net.minecraft.server.CommandBanList;
import net.minecraft.server.CommandClear;
import net.minecraft.server.CommandDeop;
import net.minecraft.server.CommandDifficulty;
import net.minecraft.server.CommandEffect;
import net.minecraft.server.CommandEnchant;
import net.minecraft.server.CommandGamemode;
import net.minecraft.server.CommandGamemodeDefault;
import net.minecraft.server.CommandGamerule;
import net.minecraft.server.CommandGive;
import net.minecraft.server.CommandHelp;
import net.minecraft.server.CommandIdleTimeout;
import net.minecraft.server.CommandKick;
import net.minecraft.server.CommandKill;
import net.minecraft.server.CommandList;
import net.minecraft.server.CommandMe;
import net.minecraft.server.CommandNetstat;
import net.minecraft.server.CommandOp;
import net.minecraft.server.CommandPardon;
import net.minecraft.server.CommandPardonIP;
import net.minecraft.server.CommandPlaySound;
import net.minecraft.server.CommandSay;
import net.minecraft.server.CommandScoreboard;
import net.minecraft.server.CommandSeed;
import net.minecraft.server.CommandSetBlock;
import net.minecraft.server.CommandSetWorldSpawn;
import net.minecraft.server.CommandSpawnpoint;
import net.minecraft.server.CommandSpreadPlayers;
import net.minecraft.server.CommandSummon;
import net.minecraft.server.CommandTell;
import net.minecraft.server.CommandTellRaw;
import net.minecraft.server.CommandTestFor;
import net.minecraft.server.CommandTestForBlock;
import net.minecraft.server.CommandTime;
import net.minecraft.server.CommandToggleDownfall;
import net.minecraft.server.CommandTp;
import net.minecraft.server.CommandWeather;
import net.minecraft.server.CommandWhitelist;
import net.minecraft.server.CommandXp;
import net.minecraft.server.ConvertProgressUpdater;
import net.minecraft.server.Convertable;
import net.minecraft.server.CraftingManager;
import net.minecraft.server.DedicatedPlayerList;
import net.minecraft.server.DedicatedServer;
import net.minecraft.server.Enchantment;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.EntityTracker;
import net.minecraft.server.EnumDifficulty;
import net.minecraft.server.EnumGamemode;
import net.minecraft.server.ExceptionWorldConflict;
import net.minecraft.server.FileIOThread;
import net.minecraft.server.Items;
import net.minecraft.server.JsonListEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.MobEffectList;
import net.minecraft.server.PersistentCollection;
import net.minecraft.server.PlayerList;
import net.minecraft.server.PropertyManager;
import net.minecraft.server.RecipesFurnace;
import net.minecraft.server.RegionFile;
import net.minecraft.server.RegionFileCache;
import net.minecraft.server.ServerCommand;
import net.minecraft.server.ServerNBTManager;
import net.minecraft.server.WorldLoaderServer;
import net.minecraft.server.WorldManager;
import net.minecraft.server.WorldMap;
import net.minecraft.server.WorldNBTStorage;
import net.minecraft.server.WorldServer;
import net.minecraft.server.WorldSettings;
import net.minecraft.server.WorldType;
import net.minecraft.util.com.google.common.base.Charsets;
import net.minecraft.util.com.mojang.authlib.GameProfile;

public final class CraftServer implements Server {
    private static final Player[] EMPTY_PLAYER_ARRAY = new Player[0];
    private final String serverName = "nPaper";
    private final String serverVersion;
    private final String bukkitVersion = Versioning.getBukkitVersion();
    private final Logger logger = Logger.getLogger("Minecraft");
    private final ServicesManager servicesManager = new SimpleServicesManager();
    private final CraftScheduler scheduler = new CraftScheduler();
    private final SimpleCommandMap commandMap = new SimpleCommandMap(this);
    private final SimpleHelpMap helpMap = new SimpleHelpMap(this);
    private final StandardMessenger messenger = new StandardMessenger();
    private final PluginManager pluginManager = new SimplePluginManager(this, commandMap);
    protected final MinecraftServer console;
    protected final DedicatedPlayerList playerList;
    private final Map<String, World> worlds = new LinkedHashMap<String, World>();
    private final Map<UUID, World> worldIdentifier = new LinkedHashMap<>();
    private YamlConfiguration configuration;
    private YamlConfiguration commandsConfiguration;
    private final Yaml yaml = new Yaml(new SafeConstructor());
    private final Map<UUID, OfflinePlayer> offlinePlayers = CacheBuilder.newBuilder().softValues().<UUID, OfflinePlayer>build().asMap();
    private final AutoUpdater updater;
    private final EntityMetadataStore entityMetadata = new EntityMetadataStore();
    private final PlayerMetadataStore playerMetadata = new PlayerMetadataStore();
    private final WorldMetadataStore worldMetadata = new WorldMetadataStore();
    private int monsterSpawn = -1;
    private int animalSpawn = -1;
    private int waterAnimalSpawn = -1;
    private int ambientSpawn = -1;
    public int chunkGCPeriod = -1;
    public int chunkGCLoadThresh = 0;
    private File container;
    private WarningState warningState = WarningState.DEFAULT;
    private final BooleanWrapper online = new BooleanWrapper();
    public CraftScoreboardManager scoreboardManager;
    public boolean playerCommandState;
    private boolean printSaveWarning;
    private CraftIconCache icon;
    private boolean overrideAllCommandBlockCommands = false;
    private final Pattern validUserPattern = Pattern.compile("^[a-zA-Z0-9_]{2,16}$");
    private final UUID invalidUserUUID = UUID.nameUUIDFromBytes("InvalidUsername".getBytes(Charsets.UTF_8));
    private final List<CraftPlayer> playerView;

    private final class BooleanWrapper {
        private boolean value = true;
    }

    static {
        ConfigurationSerialization.registerClass(CraftOfflinePlayer.class);
        CraftItemFactory.instance();
    }

    public CraftServer(MinecraftServer console, PlayerList playerList) {
        this.console = console;
        this.playerList = (DedicatedPlayerList) playerList;
        this.playerView = Collections.unmodifiableList(net.minecraft.util.com.google.common.collect.Lists.transform(playerList.players, new net.minecraft.util.com.google.common.base.Function<EntityPlayer, CraftPlayer>() {
            @Override
            public CraftPlayer apply(EntityPlayer player) {
                return player.getBukkitEntity();
            }
        }));
        this.serverVersion = CraftServer.class.getPackage().getImplementationVersion();
        online.value = console.getPropertyManager().getBoolean("online-mode", true);

        Bukkit.setServer(this);

        // Register all the Enchantments and PotionTypes now so we can stop new registration immediately after
        Enchantment.DAMAGE_ALL.getClass();
        org.bukkit.enchantments.Enchantment.stopAcceptingRegistrations();

        Potion.setPotionBrewer(new CraftPotionBrewer());
        MobEffectList.BLINDNESS.getClass();
        PotionEffectType.stopAcceptingRegistrations();
        // Ugly hack :(

        if (!Main.useConsole) {
            getLogger().info("Console input is disabled due to --noconsole command argument");
        }

        configuration = YamlConfiguration.loadConfiguration(getConfigFile());
        configuration.options().copyDefaults(true);
        configuration.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("configurations/bukkit.yml"), Charsets.UTF_8)));
        ConfigurationSection legacyAlias = null;
        if (!configuration.isString("aliases")) {
            legacyAlias = configuration.getConfigurationSection("aliases");
            configuration.set("aliases", "now-in-commands.yml");
        }
        saveConfig();
        if (getCommandsConfigFile().isFile()) {
            legacyAlias = null;
        }
        commandsConfiguration = YamlConfiguration.loadConfiguration(getCommandsConfigFile());
        commandsConfiguration.options().copyDefaults(true);
        commandsConfiguration.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("configurations/commands.yml"), Charsets.UTF_8)));
        saveCommandsConfig();

        // Migrate aliases from old file and add previously implicit $1- to pass all arguments
        if (legacyAlias != null) {
            ConfigurationSection aliases = commandsConfiguration.createSection("aliases");
            for (String key : legacyAlias.getKeys(false)) {
                ArrayList<String> commands = new ArrayList<String>();

                if (legacyAlias.isList(key)) {
                    for (String command : legacyAlias.getStringList(key)) {
                        commands.add(command + " $1-");
                    }
                } else {
                    commands.add(legacyAlias.getString(key) + " $1-");
                }

                aliases.set(key, commands);
            }
        }

        saveCommandsConfig();
        overrideAllCommandBlockCommands = commandsConfiguration.getStringList("command-block-overrides").contains("*");
        ((SimplePluginManager) pluginManager).useTimings(configuration.getBoolean("settings.plugin-profiling"));
        monsterSpawn = configuration.getInt("spawn-limits.monsters");
        animalSpawn = configuration.getInt("spawn-limits.animals");
        waterAnimalSpawn = configuration.getInt("spawn-limits.water-animals");
        ambientSpawn = configuration.getInt("spawn-limits.ambient");
        console.autosavePeriod = configuration.getInt("ticks-per.autosave");
        warningState = WarningState.value(configuration.getString("settings.deprecated-verbose"));
        chunkGCPeriod = configuration.getInt("chunk-gc.period-in-ticks");
        chunkGCLoadThresh = configuration.getInt("chunk-gc.load-threshold");
        loadIcon();

        updater = new AutoUpdater(new BukkitDLUpdaterService(configuration.getString("auto-updater.host")), getLogger(), configuration.getString("auto-updater.preferred-channel"));
        updater.setEnabled(false); // Spigot
        updater.setSuggestChannels(configuration.getBoolean("auto-updater.suggest-channels"));
        updater.getOnBroken().addAll(configuration.getStringList("auto-updater.on-broken"));
        updater.getOnUpdate().addAll(configuration.getStringList("auto-updater.on-update"));
        updater.check(serverVersion);

        // Spigot Start - Moved to old location of new DedicatedPlayerList in DedicatedServer
        // loadPlugins();
        // enablePlugins(PluginLoadOrder.STARTUP);
        // Spigot End
    }

    public boolean getCommandBlockOverride(String command) {
        return overrideAllCommandBlockCommands || commandsConfiguration.getStringList("command-block-overrides").contains(command);
    }

    private File getConfigFile() {
        return (File) console.options.valueOf("bukkit-settings");
    }

    private File getCommandsConfigFile() {
        return (File) console.options.valueOf("commands-settings");
    }

    private void saveConfig() {
        try {
            configuration.save(getConfigFile());
        } catch (IOException ex) {
            Logger.getLogger(CraftServer.class.getName()).log(Level.SEVERE, "Could not save " + getConfigFile(), ex);
        }
    }

    private void saveCommandsConfig() {
        try {
            commandsConfiguration.save(getCommandsConfigFile());
        } catch (IOException ex) {
            Logger.getLogger(CraftServer.class.getName()).log(Level.SEVERE, "Could not save " + getCommandsConfigFile(), ex);
        }
    }

    public void loadPlugins() {
        pluginManager.registerInterface(JavaPluginLoader.class);

        File pluginFolder = (File) console.options.valueOf("plugins");

        if (pluginFolder.exists()) {
            Plugin[] plugins = pluginManager.loadPlugins(pluginFolder);
            for (Plugin plugin : plugins) {
                try {
                    String message = String.format("Loading %s", plugin.getDescription().getFullName());
                    plugin.getLogger().info(message);
                    plugin.onLoad();
                } catch (Throwable ex) {
                    Logger.getLogger(CraftServer.class.getName()).log(Level.SEVERE, ex.getMessage() + " initializing " + plugin.getDescription().getFullName() + " (Is it up to date?)", ex);
                }
            }
        } else {
            pluginFolder.mkdir();
        }
    }

    public void enablePlugins(PluginLoadOrder type) {
        if (type == PluginLoadOrder.STARTUP) {
            helpMap.clear();
            helpMap.initializeGeneralTopics();
        }

        Plugin[] plugins = pluginManager.getPlugins();

        for (Plugin plugin : plugins) {
            if ((!plugin.isEnabled()) && (plugin.getDescription().getLoad() == type)) {
                loadPlugin(plugin);
            }
        }

        if (type == PluginLoadOrder.POSTWORLD) {
            // Spigot start - Allow vanilla commands to be forced to be the main command
            setVanillaCommands(true);
            commandMap.setFallbackCommands();
            setVanillaCommands(false);
            // Spigot end
            commandMap.registerServerAliases();
            loadCustomPermissions();
            DefaultPermissions.registerCorePermissions();
            helpMap.initializeCommands();
        }
    }

    public void disablePlugins() {
        pluginManager.disablePlugins();
    }

    // Spigot start
    private void tryRegister(VanillaCommandWrapper commandWrapper, boolean first) {
        if (org.spigotmc.SpigotConfig.replaceCommands.contains( commandWrapper.getName() ) ) {
            if (first) {
                commandMap.register( "minecraft", commandWrapper );
            }
        } else if (!first) {
            commandMap.register( "minecraft", commandWrapper );
        }
    }

    private void setVanillaCommands(boolean first)
    {
        tryRegister( new VanillaCommandWrapper( new CommandAchievement(), "/achievement give <stat_name> [player]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandBan(), "/ban <playername> [reason]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandBanIp(), "/ban-ip <ip-address|playername>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandBanList(), "/banlist [ips]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandClear(), "/clear <playername> [item] [metadata]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandGamemodeDefault(), "/defaultgamemode <mode>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandDeop(), "/deop <playername>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandDifficulty(), "/difficulty <new difficulty>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandEffect(), "/effect <player> <effect|clear> [seconds] [amplifier]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandEnchant(), "/enchant <playername> <enchantment ID> [enchantment level]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandGamemode(), "/gamemode <mode> [player]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandGamerule(), "/gamerule <rulename> [true|false]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandGive(), "/give <playername> <item> [amount] [metadata] [dataTag]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandHelp(), "/help [page|commandname]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandIdleTimeout(), "/setidletimeout <Minutes until kick>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandKick(), "/kick <playername> [reason]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandKill(), "/kill [playername]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandList(), "/list" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandMe(), "/me <actiontext>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandOp(), "/op <playername>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandPardon(), "/pardon <playername>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandPardonIP(), "/pardon-ip <ip-address>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandPlaySound(), "/playsound <sound> <playername> [x] [y] [z] [volume] [pitch] [minimumVolume]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandSay(), "/say <message>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandScoreboard(), "/scoreboard" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandSeed(), "/seed" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandSetBlock(), "/setblock <x> <y> <z> <tilename> [datavalue] [oldblockHandling] [dataTag]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandSetWorldSpawn(), "/setworldspawn [x] [y] [z]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandSpawnpoint(), "/spawnpoint <playername> [x] [y] [z]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandSpreadPlayers(), "/spreadplayers <x> <z> [spreadDistance] [maxRange] [respectTeams] <playernames>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandSummon(), "/summon <EntityName> [x] [y] [z] [dataTag]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandTp(), "/tp [player] <target>\n/tp [player] <x> <y> <z>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandTell(), "/tell <playername> <message>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandTellRaw(), "/tellraw <playername> <raw message>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandTestFor(), "/testfor <playername | selector> [dataTag]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandTestForBlock(), "/testforblock <x> <y> <z> <tilename> [datavalue] [dataTag]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandTime(), "/time set <value>\n/time add <value>" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandToggleDownfall(), "/toggledownfall" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandWeather(), "/weather <clear/rain/thunder> [duration in seconds]" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandWhitelist(), "/whitelist (add|remove) <player>\n/whitelist (on|off|list|reload)" ), first );
        tryRegister( new VanillaCommandWrapper( new CommandXp(), "/xp <amount> [player]\n/xp <amount>L [player]" ), first );
        // This is what is in the lang file, I swear.
        tryRegister( new VanillaCommandWrapper(new CommandNetstat(), "/list"), first );
    }
    // Spigot end

    private void loadPlugin(Plugin plugin) {
        try {
            pluginManager.enablePlugin(plugin);

            List<Permission> perms = plugin.getDescription().getPermissions();

            for (Permission perm : perms) {
                try {
                    pluginManager.addPermission(perm);
                } catch (IllegalArgumentException ex) {
                    getLogger().log(Level.WARNING, "Plugin " + plugin.getDescription().getFullName() + " tried to register permission '" + perm.getName() + "' but it's already registered", ex);
                }
            }
        } catch (Throwable ex) {
            Logger.getLogger(CraftServer.class.getName()).log(Level.SEVERE, ex.getMessage() + " loading " + plugin.getDescription().getFullName() + " (Is it up to date?)", ex);
        }
    }

    @Override
    public String getName() {
        return serverName;
    }

    @Override
    public String getVersion() {
        return serverVersion + " (MC: " + console.getVersion() + ")";
    }

    @Override
    public String getBukkitVersion() {
        return bukkitVersion;
    }

    @Override
    @Deprecated
    @SuppressWarnings("unchecked")
    public Player[] _INVALID_getOnlinePlayers() {
        return getOnlinePlayers().toArray(EMPTY_PLAYER_ARRAY);
    }

    @Override
    public List<CraftPlayer> getOnlinePlayers() {
        return this.playerView;
    }

    @Override
    @Deprecated
    public Player getPlayer(final String name) {
        Validate.notNull(name, "Name cannot be null");

        // PaperSpigot start - Improved player lookup changes
        Player found = getPlayerExact(name);
        if (found != null) {
            return found;
        }
        // PaperSpigot end
        String lowerName = name.toLowerCase();
        int delta = Integer.MAX_VALUE;
        for (Player player : getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(lowerName)) {
                int curDelta = player.getName().length() - lowerName.length();
                if (curDelta < delta) {
                    found = player;
                    delta = curDelta;
                }
                if (curDelta == 0) break;
            }
        }
        return found;
    }

    @Override
    @Deprecated
    public Player getPlayerExact(String name) {
        // PaperSpigot start - Improved player lookup, replace whole method
        EntityPlayer player = playerList.playerMap.get(name);
        return player != null ? player.getBukkitEntity() : null;
        // PaperSpigot end
    }

    @Override
    public Player getPlayer(UUID id) {
        EntityPlayer entityPlayer = playerList.getEntityPlayer(id);
        if (entityPlayer != null) return entityPlayer.getBukkitEntity();
        return null;
    }

    @Override
    public int broadcastMessage(String message) {
        return broadcast(message, BROADCAST_CHANNEL_USERS);
    }

    public Player getPlayer(final EntityPlayer entity) {
        return entity.playerConnection.getPlayer();
    }

    @Override
    @Deprecated
    public List<Player> matchPlayer(String partialName) {
        Validate.notNull(partialName, "PartialName cannot be null");

        List<Player> matchedPlayers = new ArrayList<Player>();

        for (Player iterPlayer : this.getOnlinePlayers()) {
            String iterPlayerName = iterPlayer.getName();

            if (partialName.equalsIgnoreCase(iterPlayerName)) {
                // Exact match
                matchedPlayers.clear();
                matchedPlayers.add(iterPlayer);
                break;
            }
            if (iterPlayerName.toLowerCase().contains(partialName.toLowerCase())) {
                // Partial match
                matchedPlayers.add(iterPlayer);
            }
        }

        return matchedPlayers;
    }

    @Override
    public int getMaxPlayers() {
        return playerList.getMaxPlayers();
    }

    // NOTE: These are dependent on the corrisponding call in MinecraftServer
    // so if that changes this will need to as well
    @Override
    public int getPort() {
        return this.getConfigInt("server-port", 25565);
    }

    @Override
    public int getViewDistance() {
        return this.getConfigInt("view-distance", 10);
    }

    @Override
    public String getIp() {
        return this.getConfigString("server-ip", "");
    }

    @Override
    public String getServerName() {
        return this.getConfigString("server-name", "Unknown Server");
    }

    @Override
    public String getServerId() {
        return this.getConfigString("server-id", "unnamed");
    }

    @Override
    public String getWorldType() {
        return this.getConfigString("level-type", "DEFAULT");
    }

    @Override
    public boolean getGenerateStructures() {
        return this.getConfigBoolean("generate-structures", true);
    }

    @Override
    public boolean getAllowEnd() {
        return this.configuration.getBoolean("settings.allow-end");
    }

    @Override
    public boolean getAllowNether() {
        return this.getConfigBoolean("allow-nether", true);
    }

    public boolean getWarnOnOverload() {
        return this.configuration.getBoolean("settings.warn-on-overload");
    }

    public boolean getQueryPlugins() {
        return this.configuration.getBoolean("settings.query-plugins");
    }

    @Override
    public boolean hasWhitelist() {
        return this.getConfigBoolean("white-list", false);
    }

    // NOTE: Temporary calls through to server.properies until its replaced
    private String getConfigString(String variable, String defaultValue) {
        return this.console.getPropertyManager().getString(variable, defaultValue);
    }

    private int getConfigInt(String variable, int defaultValue) {
        return this.console.getPropertyManager().getInt(variable, defaultValue);
    }

    private boolean getConfigBoolean(String variable, boolean defaultValue) {
        return this.console.getPropertyManager().getBoolean(variable, defaultValue);
    }

    // End Temporary calls

    @Override
    public String getUpdateFolder() {
        return this.configuration.getString("settings.update-folder", "update");
    }

    @Override
    public File getUpdateFolderFile() {
        return new File((File) console.options.valueOf("plugins"), this.configuration.getString("settings.update-folder", "update"));
    }

    public int getPingPacketLimit() {
        return this.configuration.getInt("settings.ping-packet-limit", 100);
    }

    @Override
    public long getConnectionThrottle() {
        // Spigot Start - Automatically set connection throttle for bungee configurations
        if (org.spigotmc.SpigotConfig.bungee) {
            return -1;
        }
        return this.configuration.getInt("settings.connection-throttle");
        // Spigot End
    }

    @Override
    public int getTicksPerAnimalSpawns() {
        return this.configuration.getInt("ticks-per.animal-spawns");
    }

    @Override
    public int getTicksPerMonsterSpawns() {
        return this.configuration.getInt("ticks-per.monster-spawns");
    }

    @Override
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    @Override
    public CraftScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public ServicesManager getServicesManager() {
        return servicesManager;
    }

    @Override
    public List<World> getWorlds() {
        return new ArrayList<World>(worlds.values());
    }

    public DedicatedPlayerList getHandle() {
        return playerList;
    }

    // NOTE: Should only be called from DedicatedServer.ah()
    public boolean dispatchServerCommand(CommandSender sender, ServerCommand serverCommand) {
        if (sender instanceof Conversable) {
            Conversable conversable = (Conversable)sender;

            if (conversable.isConversing()) {
                conversable.acceptConversationInput(serverCommand.command);
                return true;
            }
        }
        try {
            this.playerCommandState = true;
            return dispatchCommand(sender, serverCommand.command);
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Unexpected exception while parsing console command \"" + serverCommand.command + '"', ex);
            return false;
        } finally {
            this.playerCommandState = false;
        }
    }

    @Override
    public boolean dispatchCommand(CommandSender sender, String commandLine) {
        Validate.notNull(sender, "Sender cannot be null");
        Validate.notNull(commandLine, "CommandLine cannot be null");

        if (commandMap.dispatch(sender, commandLine)) {
            return true;
        }

        sender.sendMessage(org.spigotmc.SpigotConfig.unknownCommandMessage);

        return false;
    }

    @Override
    public void reload() {
        configuration = YamlConfiguration.loadConfiguration(getConfigFile());
        commandsConfiguration = YamlConfiguration.loadConfiguration(getCommandsConfigFile());
        PropertyManager config = new PropertyManager(console.options);

        ((DedicatedServer) console).propertyManager = config;

        boolean animals = config.getBoolean("spawn-animals", console.getSpawnAnimals());
        boolean monsters = config.getBoolean("spawn-monsters", console.worlds.get(0).difficulty != EnumDifficulty.PEACEFUL);
        EnumDifficulty difficulty = EnumDifficulty.getById(config.getInt("difficulty", console.worlds.get(0).difficulty.ordinal()));

        online.value = config.getBoolean("online-mode", console.getOnlineMode());
        console.setSpawnAnimals(config.getBoolean("spawn-animals", console.getSpawnAnimals()));
        console.setPvP(config.getBoolean("pvp", console.getPvP()));
        console.setAllowFlight(config.getBoolean("allow-flight", console.getAllowFlight()));
        console.setMotd(config.getString("motd", console.getMotd()));
        monsterSpawn = configuration.getInt("spawn-limits.monsters");
        animalSpawn = configuration.getInt("spawn-limits.animals");
        waterAnimalSpawn = configuration.getInt("spawn-limits.water-animals");
        ambientSpawn = configuration.getInt("spawn-limits.ambient");
        warningState = WarningState.value(configuration.getString("settings.deprecated-verbose"));
        printSaveWarning = false;
        console.autosavePeriod = configuration.getInt("ticks-per.autosave");
        chunkGCPeriod = configuration.getInt("chunk-gc.period-in-ticks");
        chunkGCLoadThresh = configuration.getInt("chunk-gc.load-threshold");
        loadIcon();

        try {
            playerList.getIPBans().load();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to load banned-ips.json, " + ex.getMessage());
        }
        try {
            playerList.getProfileBans().load();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to load banned-players.json, " + ex.getMessage());
        }

        org.spigotmc.SpigotConfig.init(); // Spigot
        org.github.paperspigot.PaperSpigotConfig.init(); // PaperSpigot
        for (WorldServer world : console.worlds) {
            world.difficulty = difficulty;
            world.setSpawnFlags(monsters, animals);
            if (this.getTicksPerAnimalSpawns() < 0) {
                world.ticksPerAnimalSpawns = 400;
            } else {
                world.ticksPerAnimalSpawns = this.getTicksPerAnimalSpawns();
            }

            if (this.getTicksPerMonsterSpawns() < 0) {
                world.ticksPerMonsterSpawns = 1;
            } else {
                world.ticksPerMonsterSpawns = this.getTicksPerMonsterSpawns();
            }
            world.spigotConfig.init(); // Spigot
            world.paperSpigotConfig.init(); // PaperSpigot
        }

        pluginManager.clearPlugins();
        commandMap.clearCommands();
        resetRecipes();
        org.spigotmc.SpigotConfig.registerCommands(); // Spigot
        org.github.paperspigot.PaperSpigotConfig.registerCommands(); // PaperSpigot

        overrideAllCommandBlockCommands = commandsConfiguration.getStringList("command-block-overrides").contains("*");

        int pollCount = 0;

        // Wait for at most 2.5 seconds for plugins to close their threads
        while (pollCount < 50 && getScheduler().getActiveWorkers().size() > 0) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {}
            pollCount++;
        }

        List<BukkitWorker> overdueWorkers = getScheduler().getActiveWorkers();
        for (BukkitWorker worker : overdueWorkers) {
            Plugin plugin = worker.getOwner();
            String author = "<NoAuthorGiven>";
            if (plugin.getDescription().getAuthors().size() > 0) {
                author = plugin.getDescription().getAuthors().get(0);
            }
            getLogger().log(Level.SEVERE, String.format(
                "Nag author: '%s' of '%s' about the following: %s",
                author,
                plugin.getDescription().getName(),
                "This plugin is not properly shutting down its async tasks when it is being reloaded.  This may cause conflicts with the newly loaded version of the plugin"
            ));
        }
        loadPlugins();
        enablePlugins(PluginLoadOrder.STARTUP);
        enablePlugins(PluginLoadOrder.POSTWORLD);
    }

    private void loadIcon() {
        icon = new CraftIconCache(null);
        try {
            final File file = new File(new File("."), "server-icon.png");
            if (file.isFile()) {
                icon = loadServerIcon0(file);
            }
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Couldn't load server icon", ex);
        }
    }

    @SuppressWarnings({ "unchecked", "finally" })
    private void loadCustomPermissions() {
        File file = new File(configuration.getString("settings.permissions-file"));
        FileInputStream stream;

        try {
            stream = new FileInputStream(file);
        } catch (FileNotFoundException ex) {
            try {
                file.createNewFile();
            } finally {
                return;
            }
        }

        Map<String, Map<String, Object>> perms;

        try {
            perms = (Map<String, Map<String, Object>>) yaml.load(stream);
        } catch (MarkedYAMLException ex) {
            getLogger().log(Level.WARNING, "Server permissions file " + file + " is not valid YAML: " + ex.toString());
            return;
        } catch (Throwable ex) {
            getLogger().log(Level.WARNING, "Server permissions file " + file + " is not valid YAML.", ex);
            return;
        } finally {
            try {
                stream.close();
            } catch (IOException ex) {}
        }

        if (perms == null) {
            getLogger().log(Level.INFO, "Server permissions file " + file + " is empty, ignoring it");
            return;
        }

        List<Permission> permsList = Permission.loadPermissions(perms, "Permission node '%s' in " + file + " is invalid", Permission.DEFAULT_PERMISSION);

        for (Permission perm : permsList) {
            try {
                pluginManager.addPermission(perm);
            } catch (IllegalArgumentException ex) {
                getLogger().log(Level.SEVERE, "Permission in " + file + " was already defined", ex);
            }
        }
    }

    @Override
    public String toString() {
        return "CraftServer{" + "serverName=" + serverName + ",serverVersion=" + serverVersion + ",minecraftVersion=" + console.getVersion() + '}';
    }

    public World createWorld(String name, World.Environment environment) {
        return WorldCreator.name(name).environment(environment).createWorld();
    }

    public World createWorld(String name, World.Environment environment, long seed) {
        return WorldCreator.name(name).environment(environment).seed(seed).createWorld();
    }

    public World createWorld(String name, Environment environment, ChunkGenerator generator) {
        return WorldCreator.name(name).environment(environment).generator(generator).createWorld();
    }

    public World createWorld(String name, Environment environment, long seed, ChunkGenerator generator) {
        return WorldCreator.name(name).environment(environment).seed(seed).generator(generator).createWorld();
    }

    @Override
    public World createWorld(WorldCreator creator) {
        Validate.notNull(creator, "Creator may not be null");

        String name = creator.name();
        World world = getWorld(name);
        if (world != null) {
            return world;
        }
        File folder = new File(getWorldContainer(), name);
        if ((folder.exists()) && (!folder.isDirectory())) {
            throw new IllegalArgumentException("File exists with the name '" + name + "' and isn't a folder");
        }
        ChunkGenerator generator = creator.generator();
        if (generator == null) {
            generator = getGenerator(name);
        }
        WorldType type = WorldType.getType(creator.type().getName());
        boolean generateStructures = creator.generateStructures();

        Convertable converter = new WorldLoaderServer(getWorldContainer());
        if (converter.isConvertable(name)) {
            getLogger().info("Converting world '" + name + "'");
            converter.convert(name, new ConvertProgressUpdater(console));
        }

        int dimension = CraftWorld.CUSTOM_DIMENSION_OFFSET + console.worlds.size();
        boolean used = false;
        do {
            for (WorldServer server : console.worlds) {
                used = server.dimension == dimension;
                if (used) {
                    dimension++;
                    break;
                }
            }
        } while(used);
        boolean hardcore = false;

        WorldServer internal = new WorldServer(console, new ServerNBTManager(getWorldContainer(), name, true), name, dimension, new WorldSettings(creator.seed(), EnumGamemode.getById(getDefaultGameMode().getValue()), generateStructures, hardcore, type), console.methodProfiler, creator.environment(), generator);

        if (!(worlds.containsKey(name.toLowerCase()))) {
            return null;
        }

        internal.scoreboard = getScoreboardManager().getMainScoreboard().getHandle();

        internal.tracker = new EntityTracker(internal);
        internal.addIWorldAccess(new WorldManager(console, internal));
        internal.difficulty = EnumDifficulty.EASY;
        internal.setSpawnFlags(true, true);
        console.worlds.add(internal);

        if (generator != null) {
            internal.getWorld().getPopulators().addAll(generator.getDefaultPopulators(internal.getWorld()));
        }

        pluginManager.callEvent(new WorldInitEvent(internal.getWorld()));
        System.out.print("Preparing start region for level " + (console.worlds.size() - 1) + " (Seed: " + internal.getSeed() + ")");

        if (internal.getWorld().getKeepSpawnInMemory()) {
            short short1 = 196;
            long i = System.currentTimeMillis();
            for (int j = -short1; j <= short1; j += 16) {
                for (int k = -short1; k <= short1; k += 16) {
                    long l = System.currentTimeMillis();

                    if (l < i) {
                        i = l;
                    }

                    if (l > i + 1000L) {
                        int i1 = (short1 * 2 + 1) * (short1 * 2 + 1);
                        int j1 = (j + short1) * (short1 * 2 + 1) + k + 1;

                        System.out.println("Preparing spawn area for " + name + ", " + (j1 * 100 / i1) + "%");
                        i = l;
                    }

                    ChunkCoordinates chunkcoordinates = internal.getSpawn();
                    internal.chunkProviderServer.getChunkAt(chunkcoordinates.x + j >> 4, chunkcoordinates.z + k >> 4);
                }
            }
        }
        pluginManager.callEvent(new WorldLoadEvent(internal.getWorld()));
        return internal.getWorld();
    }

    @Override
    public boolean unloadWorld(String name, boolean save) {
        return unloadWorld(getWorld(name), save);
    }

    @Override
    public boolean unloadWorld(World world, boolean save) {
        if (world == null) {
            return false;
        }

        WorldServer handle = ((CraftWorld) world).getHandle();

        if (!(console.worlds.contains(handle))) {
            return false;
        }

        if (!(handle.dimension > 1)) {
            return false;
        }

        if (handle.players.size() > 0) {
            return false;
        }

        WorldUnloadEvent e = new WorldUnloadEvent(handle.getWorld());
        pluginManager.callEvent(e);

        if (e.isCancelled()) {
            return false;
        }
        
        worlds.remove(world.getName().toLowerCase());
        worldIdentifier.remove(world.getUID());
        console.worlds.remove(console.worlds.indexOf(handle));

        if (save) {
            try {
                handle.save(true, null);
                handle.saveLevel();
                WorldSaveEvent event = new WorldSaveEvent(handle.getWorld());
                getPluginManager().callEvent(event);
            } catch (ExceptionWorldConflict ex) {
                getLogger().log(Level.SEVERE, null, ex);
            }
        } else { // FlamePaper - Fix chunk memory leak
        	ChunkProviderServer chunkProviderServer = handle.chunkProviderServer;
        	ChunkRegionLoader regionLoader = (ChunkRegionLoader) chunkProviderServer.f;
        	
        	regionLoader.b.clear();
        	regionLoader.c.clear();
        	
        	FileIOThread.a.a();
        	chunkProviderServer.unloadChunks(true);            
        	chunkProviderServer.f = null;
        	chunkProviderServer.chunkProvider = null;
        	chunkProviderServer.chunks.clear();
        }

        File parentFolder = world.getWorldFolder().getAbsoluteFile();

        // Synchronized because access to RegionFileCache.a is guarded by this lock.
        synchronized (RegionFileCache.class) {
            // RegionFileCache.a should be RegionFileCache.cache
            Iterator<Map.Entry<File, RegionFile>> i = RegionFileCache.a.entrySet().iterator();
            while(i.hasNext()) {
                Map.Entry<File, RegionFile> entry = i.next();
                File child = entry.getKey().getAbsoluteFile();
                while (child != null) {
                    if (child.equals(parentFolder)) {
                        i.remove();
                        try {
                            entry.getValue().c(); // Should be RegionFile.close();
                        } catch (IOException ex) {
                            getLogger().log(Level.SEVERE, null, ex);
                        }
                        break;
                    }
                    child = child.getParentFile();
                }
            }
        }

        return true;
    }

    public MinecraftServer getServer() {
        return console;
    }

    @Override
    public World getWorld(String name) {
        Validate.notNull(name, "Name cannot be null");

        return worlds.get(name.toLowerCase());
    }

    @Override
    public World getWorld(UUID uid) {
        return worldIdentifier.get(uid);
    }

    public void addWorld(World world) {
        // Check if a World already exists with the UID.
        if (getWorld(world.getUID()) != null) {
            System.out.println("World " + world.getName() + " is a duplicate of another world and has been prevented from loading. Please delete the uid.dat file from " + world.getName() + "'s world directory if you want to be able to load the duplicate world.");
            return;
        }
        worlds.put(world.getName().toLowerCase(), world);
        worldIdentifier.put(world.getUID(), world);
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    public ConsoleReader getReader() {
        return console.reader;
    }

    @Override
    public PluginCommand getPluginCommand(String name) {
        Command command = commandMap.getCommand(name);

        if (command instanceof PluginCommand) {
            return (PluginCommand) command;
        } else {
            return null;
        }
    }

    @Override
    public void savePlayers() {
        checkSaveState();
        playerList.savePlayers();
    }

    @Override
    public void configureDbConfig(ServerConfig config) {
        Validate.notNull(config, "Config cannot be null");

        DataSourceConfig ds = new DataSourceConfig();
        ds.setDriver(configuration.getString("database.driver"));
        ds.setUrl(configuration.getString("database.url"));
        ds.setUsername(configuration.getString("database.username"));
        ds.setPassword(configuration.getString("database.password"));
        ds.setIsolationLevel(TransactionIsolation.getLevel(configuration.getString("database.isolation")));

        if (ds.getDriver().contains("sqlite")) {
            config.setDatabasePlatform(new SQLitePlatform());
            config.getDatabasePlatform().getDbDdlSyntax().setIdentity("");
        }

        config.setDataSourceConfig(ds);
    }

    @Override
    public boolean addRecipe(Recipe recipe) {
        CraftRecipe toAdd;
        if (recipe instanceof CraftRecipe) {
            toAdd = (CraftRecipe) recipe;
        } else {
            if (recipe instanceof ShapedRecipe) {
                toAdd = CraftShapedRecipe.fromBukkitRecipe((ShapedRecipe) recipe);
            } else if (recipe instanceof ShapelessRecipe) {
                toAdd = CraftShapelessRecipe.fromBukkitRecipe((ShapelessRecipe) recipe);
            } else if (recipe instanceof FurnaceRecipe) {
                toAdd = CraftFurnaceRecipe.fromBukkitRecipe((FurnaceRecipe) recipe);
            } else {
                return false;
            }
        }
        toAdd.addToCraftingManager();
        CraftingManager.getInstance().sort();
        return true;
    }

    @Override
    public List<Recipe> getRecipesFor(ItemStack result) {
        Validate.notNull(result, "Result cannot be null");

        List<Recipe> results = new ArrayList<Recipe>();
        Iterator<Recipe> iter = recipeIterator();
        while (iter.hasNext()) {
            Recipe recipe = iter.next();
            ItemStack stack = recipe.getResult();
            if (stack.getType() != result.getType()) {
                continue;
            }
            if (result.getDurability() == -1 || result.getDurability() == stack.getDurability()) {
                results.add(recipe);
            }
        }
        return results;
    }

    @Override
    public Iterator<Recipe> recipeIterator() {
        return new RecipeIterator();
    }

    @Override
    public void clearRecipes() {
        CraftingManager.getInstance().recipes.clear();
        RecipesFurnace.getInstance().recipes.clear();
        RecipesFurnace.getInstance().customRecipes.clear();
    }

    @Override
    public void resetRecipes() {
        CraftingManager.getInstance().recipes = new CraftingManager().recipes;
        RecipesFurnace.getInstance().recipes = new RecipesFurnace().recipes;
        RecipesFurnace.getInstance().customRecipes.clear();
    }

    @Override
    public Map<String, String[]> getCommandAliases() {
        ConfigurationSection section = commandsConfiguration.getConfigurationSection("aliases");
        Map<String, String[]> result = new LinkedHashMap<String, String[]>();

        if (section != null) {
            for (String key : section.getKeys(false)) {
                List<String> commands;

                if (section.isList(key)) {
                    commands = section.getStringList(key);
                } else {
                    commands = ImmutableList.of(section.getString(key));
                }

                result.put(key, commands.toArray(new String[commands.size()]));
            }
        }

        return result;
    }

    public void removeBukkitSpawnRadius() {
        configuration.set("settings.spawn-radius", null);
        saveConfig();
    }

    public int getBukkitSpawnRadius() {
        return configuration.getInt("settings.spawn-radius", -1);
    }

    @Override
    public String getShutdownMessage() {
        return configuration.getString("settings.shutdown-message");
    }

    @Override
    public int getSpawnRadius() {
        return ((DedicatedServer) console).propertyManager.getInt("spawn-protection", 16);
    }

    @Override
    public void setSpawnRadius(int value) {
        configuration.set("settings.spawn-radius", value);
        saveConfig();
    }

    @Override
    public boolean getOnlineMode() {
        return online.value;
    }

    @Override
    public boolean getAllowFlight() {
        return console.getAllowFlight();
    }

    @Override
    public boolean isHardcore() {
        return console.isHardcore();
    }

    @Override
    public boolean useExactLoginLocation() {
        return configuration.getBoolean("settings.use-exact-login-location");
    }

    public ChunkGenerator getGenerator(String world) {
        ConfigurationSection section = configuration.getConfigurationSection("worlds");
        ChunkGenerator result = null;

        if (section != null) {
            section = section.getConfigurationSection(world);

            if (section != null) {
                String name = section.getString("generator");

                if ((name != null) && (!name.equals(""))) {
                    String[] split = name.split(":", 2);
                    String id = (split.length > 1) ? split[1] : null;
                    Plugin plugin = pluginManager.getPlugin(split[0]);

                    if (plugin == null) {
                        getLogger().severe("Could not set generator for default world '" + world + "': Plugin '" + split[0] + "' does not exist");
                    } else if (!plugin.isEnabled()) {
                        getLogger().severe("Could not set generator for default world '" + world + "': Plugin '" + plugin.getDescription().getFullName() + "' is not enabled yet (is it load:STARTUP?)");
                    } else {
                        try {
                            result = plugin.getDefaultWorldGenerator(world, id);
                            if (result == null) {
                                getLogger().severe("Could not set generator for default world '" + world + "': Plugin '" + plugin.getDescription().getFullName() + "' lacks a default world generator");
                            }
                        } catch (Throwable t) {
                            plugin.getLogger().log(Level.SEVERE, "Could not set generator for default world '" + world + "': Plugin '" + plugin.getDescription().getFullName(), t);
                        }
                    }
                }
            }
        }

        return result;
    }

    @Override
    @Deprecated
    public CraftMapView getMap(short id) {
        PersistentCollection collection = console.worlds.get(0).worldMaps;
        WorldMap worldmap = (WorldMap) collection.get(WorldMap.class, "map_" + id);
        if (worldmap == null) {
            return null;
        }
        return worldmap.mapView;
    }

    @Override
    public CraftMapView createMap(World world) {
        Validate.notNull(world, "World cannot be null");

        net.minecraft.server.ItemStack stack = new net.minecraft.server.ItemStack(Items.MAP, 1, -1);
        WorldMap worldmap = Items.MAP.getSavedMap(stack, ((CraftWorld) world).getHandle());
        return worldmap.mapView;
    }

    @Override
    public void shutdown() {
        console.safeShutdown();
    }

    @Override
    public int broadcast(String message, String permission) {
        int count = 0;
        Set<Permissible> permissibles = getPluginManager().getPermissionSubscriptions(permission);

        for (Permissible permissible : permissibles) {
            if (permissible instanceof CommandSender && permissible.hasPermission(permission)) {
                CommandSender user = (CommandSender) permissible;
                user.sendMessage(message);
                count++;
            }
        }

        return count;
    }

    @Override
    @Deprecated
    public OfflinePlayer getOfflinePlayer(String name) {
        Validate.notNull(name, "Name cannot be null");
        com.google.common.base.Preconditions.checkArgument( !org.apache.commons.lang.StringUtils.isBlank( name ), "Name cannot be blank" ); // Spigot

        OfflinePlayer result = getPlayerExact(name);
        if (result == null) {
            // Spigot Start
            GameProfile profile = null;
            // Only fetch an online UUID in online mode
            if ( MinecraftServer.getServer().getOnlineMode() || org.spigotmc.SpigotConfig.bungee )
            {
                profile = MinecraftServer.getServer().getUserCache().getProfile( name );
            }
            // Spigot end
            if (profile == null) {
                // Make an OfflinePlayer using an offline mode UUID since the name has no profile
                result = getOfflinePlayer(new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(Charsets.UTF_8)), name));
            } else {
                // Use the GameProfile even when we get a UUID so we ensure we still have a name
                result = getOfflinePlayer(profile);
            }
        } else {
            offlinePlayers.remove(result.getUniqueId());
        }

        return result;
    }

    @Override
    public OfflinePlayer getOfflinePlayer(UUID id) {
        Validate.notNull(id, "UUID cannot be null");

        OfflinePlayer result = getPlayer(id);
        if (result == null) {
            result = offlinePlayers.get(id);
            if (result == null) {
                result = new CraftOfflinePlayer(this, new GameProfile(id, null));
                offlinePlayers.put(id, result);
            }
        } else {
            offlinePlayers.remove(id);
        }

        return result;
    }

    public OfflinePlayer getOfflinePlayer(GameProfile profile) {
        OfflinePlayer player = new CraftOfflinePlayer(this, profile);
        offlinePlayers.put(profile.getId(), player);
        return player;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getIPBans() {
        return new HashSet<String>(Arrays.asList(playerList.getIPBans().getEntries()));
    }

    @Override
    public void banIP(String address) {
        Validate.notNull(address, "Address cannot be null.");

        this.getBanList(org.bukkit.BanList.Type.IP).addBan(address, null, null, null);
    }

    @Override
    public void unbanIP(String address) {
        Validate.notNull(address, "Address cannot be null.");

        this.getBanList(org.bukkit.BanList.Type.IP).pardon(address);
    }

    @Override
    public Set<OfflinePlayer> getBannedPlayers() {
        Set<OfflinePlayer> result = new HashSet<OfflinePlayer>();

        for (JsonListEntry entry : playerList.getProfileBans().getValues()) {
            result.add(getOfflinePlayer((GameProfile) entry.getKey()));
        }

        return result;
    }

    @Override
    public BanList getBanList(BanList.Type type) {
        Validate.notNull(type, "Type cannot be null");

        switch(type){
        case IP:
            return new CraftIpBanList(playerList.getIPBans());
        case NAME:
        default:
            return new CraftProfileBanList(playerList.getProfileBans());
        }
    }

    @Override
    public void setWhitelist(boolean value) {
        playerList.setHasWhitelist(value);
        console.getPropertyManager().setProperty("white-list", value);
    }

    @Override
    public Set<OfflinePlayer> getWhitelistedPlayers() {
        Set<OfflinePlayer> result = new LinkedHashSet<OfflinePlayer>();

        for (JsonListEntry entry : playerList.getWhitelist().getValues()) {
            result.add(getOfflinePlayer((GameProfile) entry.getKey()));
        }

        return result;
    }

    @Override
    public Set<OfflinePlayer> getOperators() {
        Set<OfflinePlayer> result = new HashSet<OfflinePlayer>();

        for (JsonListEntry entry : playerList.getOPs().getValues()) {
            result.add(getOfflinePlayer((GameProfile) entry.getKey()));
        }

        return result;
    }

    @Override
    public void reloadWhitelist() {
        playerList.reloadWhitelist();
    }

    @Override
    public GameMode getDefaultGameMode() {
        return GameMode.getByValue(console.worlds.get(0).getWorldData().getGameType().getId());
    }

    @Override
    public void setDefaultGameMode(GameMode mode) {
        Validate.notNull(mode, "Mode cannot be null");

        for (World world : getWorlds()) {
            ((CraftWorld) world).getHandle().worldData.setGameType(EnumGamemode.getById(mode.getValue()));
        }
    }

    @Override
    public ConsoleCommandSender getConsoleSender() {
        return console.console;
    }

    public EntityMetadataStore getEntityMetadata() {
        return entityMetadata;
    }

    public PlayerMetadataStore getPlayerMetadata() {
        return playerMetadata;
    }

    public WorldMetadataStore getWorldMetadata() {
        return worldMetadata;
    }

    public void detectListNameConflict(EntityPlayer entityPlayer) {
        // Collisions will make for invisible people
        for (int i = 0; i < getHandle().players.size(); ++i) {
            EntityPlayer testEntityPlayer = (EntityPlayer) getHandle().players.get(i);

            // We have a problem!
            if (testEntityPlayer != entityPlayer && testEntityPlayer.listName.equals(entityPlayer.listName)) {
                String oldName = entityPlayer.listName;
                int spaceLeft = 16 - oldName.length();

                if (spaceLeft <= 1) { // We also hit the list name length limit!
                    entityPlayer.listName = oldName.subSequence(0, oldName.length() - 2 - spaceLeft) + String.valueOf(System.currentTimeMillis() % 99);
                } else {
                    entityPlayer.listName = oldName + String.valueOf(System.currentTimeMillis() % 99);
                }

                return;
            }
        }
    }

    @Override
    public File getWorldContainer() {
        if (this.getServer().universe != null) {
            return this.getServer().universe;
        }

        if (container == null) {
            container = new File(configuration.getString("settings.world-container", "."));
        }

        return container;
    }

    @Override
    public OfflinePlayer[] getOfflinePlayers() {
        WorldNBTStorage storage = (WorldNBTStorage) console.worlds.get(0).getDataManager();
        String[] files = storage.getPlayerDir().list(new DatFileFilter());
        Set<OfflinePlayer> players = new HashSet<OfflinePlayer>();

        for (String file : files) {
            try {
                players.add(getOfflinePlayer(UUID.fromString(file.substring(0, file.length() - 4))));
            } catch (IllegalArgumentException ex) {
                // Who knows what is in this directory, just ignore invalid files
            }
        }

        players.addAll(getOnlinePlayers());

        return players.toArray(new OfflinePlayer[players.size()]);
    }

    @Override
    public Messenger getMessenger() {
        return messenger;
    }

    @Override
    public void sendPluginMessage(Plugin source, String channel, byte[] message) {
        StandardMessenger.validatePluginMessage(getMessenger(), source, channel, message);

        for (Player player : getOnlinePlayers()) {
            player.sendPluginMessage(source, channel, message);
        }
    }

    @Override
    public Set<String> getListeningPluginChannels() {
        Set<String> result = new HashSet<String>();

        for (Player player : getOnlinePlayers()) {
            result.addAll(player.getListeningPluginChannels());
        }

        return result;
    }

    public void onPlayerJoin(Player player) {
        if ((updater.isEnabled()) && (updater.getCurrent() != null) && (player.hasPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE))) {
            if ((updater.getCurrent().isBroken()) && (updater.getOnBroken().contains(AutoUpdater.WARN_OPERATORS))) {
                player.sendMessage(ChatColor.DARK_RED + "The version of CraftBukkit that this server is running is known to be broken. Please consider updating to the latest version at dl.bukkit.org.");
            } else if ((updater.isUpdateAvailable()) && (updater.getOnUpdate().contains(AutoUpdater.WARN_OPERATORS))) {
                player.sendMessage(ChatColor.DARK_PURPLE + "The version of CraftBukkit that this server is running is out of date. Please consider updating to the latest version at dl.bukkit.org.");
            }
        }
    }

    @Override
    public Inventory createInventory(InventoryHolder owner, InventoryType type) {
        // TODO: Create the appropriate type, rather than Custom?
        return new CraftInventoryCustom(owner, type);
    }

    @Override
    public Inventory createInventory(InventoryHolder owner, InventoryType type, String title) {
        return new CraftInventoryCustom(owner, type, title);
    }

    @Override
    public Inventory createInventory(InventoryHolder owner, int size) throws IllegalArgumentException {
        Validate.isTrue(size % 9 == 0, "Chests must have a size that is a multiple of 9!");
        return new CraftInventoryCustom(owner, size);
    }

    @Override
    public Inventory createInventory(InventoryHolder owner, int size, String title) throws IllegalArgumentException {
        Validate.isTrue(size % 9 == 0, "Chests must have a size that is a multiple of 9!");
        return new CraftInventoryCustom(owner, size, title);
    }

    @Override
    public HelpMap getHelpMap() {
        return helpMap;
    }

    public SimpleCommandMap getCommandMap() {
        return commandMap;
    }

    @Override
    public int getMonsterSpawnLimit() {
        return monsterSpawn;
    }

    @Override
    public int getAnimalSpawnLimit() {
        return animalSpawn;
    }

    @Override
    public int getWaterAnimalSpawnLimit() {
        return waterAnimalSpawn;
    }

    @Override
    public int getAmbientSpawnLimit() {
        return ambientSpawn;
    }

    @Override
    public boolean isPrimaryThread() {
        return Thread.currentThread().equals(console.primaryThread);
    }

    @Override
    public String getMotd() {
        return console.getMotd();
    }

    @Override
    public WarningState getWarningState() {
        return warningState;
    }

    public List<String> tabComplete(net.minecraft.server.ICommandListener sender, String message) {
        if (!(sender instanceof EntityPlayer)) {
            return ImmutableList.of();
        }

        Player player = ((EntityPlayer) sender).getBukkitEntity();
        if (message.startsWith("/")) {
            return tabCompleteCommand(player, message);
        } else {
            return tabCompleteChat(player, message);
        }
    }

    public List<String> tabCompleteCommand(Player player, String message) {
        // Spigot Start
		if ( (org.spigotmc.SpigotConfig.tabComplete < 0 || message.length() <= org.spigotmc.SpigotConfig.tabComplete) && !message.contains( " " ) )
        {
            return ImmutableList.of();
        }
        // Spigot End

        List<String> completions = null;
        try {
            completions = getCommandMap().tabComplete(player, message.substring(1));
        } catch (CommandException ex) {
            player.sendMessage(ChatColor.RED + "An internal error occurred while attempting to tab-complete this command");
            getLogger().log(Level.SEVERE, "Exception when " + player.getName() + " attempted to tab complete " + message, ex);
        }

        return completions == null ? ImmutableList.<String>of() : completions;
    }

    public List<String> tabCompleteChat(Player player, String message) {
        List<String> completions = new ArrayList<String>();
        PlayerChatTabCompleteEvent event = new PlayerChatTabCompleteEvent(player, message, completions);
        String token = event.getLastToken();
        for (Player p : getOnlinePlayers()) {
            if (player.canSee(p) && StringUtil.startsWithIgnoreCase(p.getName(), token)) {
                completions.add(p.getName());
            }
        }
        pluginManager.callEvent(event);

        Iterator<?> it = completions.iterator();
        while (it.hasNext()) {
            Object current = it.next();
            if (!(current instanceof String)) {
                // Sanity
                it.remove();
            }
        }
        Collections.sort(completions, String.CASE_INSENSITIVE_ORDER);
        return completions;
    }

    @Override
    public CraftItemFactory getItemFactory() {
        return CraftItemFactory.instance();
    }

    @Override
    public CraftScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public void checkSaveState() {
        if (this.playerCommandState || this.printSaveWarning || this.console.autosavePeriod <= 0) {
            return;
        }
        this.printSaveWarning = true;
        getLogger().log(Level.WARNING, "A manual (plugin-induced) save has been detected while server is configured to auto-save. This may affect performance.", warningState == WarningState.ON ? new Throwable() : null);
    }

    @Override
    public CraftIconCache getServerIcon() {
        return icon;
    }

    @Override
    public CraftIconCache loadServerIcon(File file) throws Exception {
        Validate.notNull(file, "File cannot be null");
        if (!file.isFile()) {
            throw new IllegalArgumentException(file + " is not a file");
        }
        return loadServerIcon0(file);
    }

    static CraftIconCache loadServerIcon0(File file) throws Exception {
        return loadServerIcon0(ImageIO.read(file));
    }

    @Override
    public CraftIconCache loadServerIcon(BufferedImage image) throws Exception {
        Validate.notNull(image, "Image cannot be null");
        return loadServerIcon0(image);
    }

    static CraftIconCache loadServerIcon0(BufferedImage image) throws Exception {
    	Validate.isTrue(image.getWidth() == image.getHeight(), "Width must be equals to the height");
        Validate.isTrue(image.getWidth() == 64, "Must be 64 pixels wide");
        Validate.isTrue(image.getHeight() == 64, "Must be 64 pixels high");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] imageInByte = baos.toByteArray();
        String imageDataString = Base64.getEncoder().encodeToString(imageInByte);

        return new CraftIconCache("data:image/png;base64," + imageDataString);
    }

    @Override
    public void setIdleTimeout(int threshold) {
        console.setIdleTimeout(threshold);
    }

    @Override
    public int getIdleTimeout() {
        return console.getIdleTimeout();
    }

    @Deprecated
    @Override
    public UnsafeValues getUnsafe() {
        return CraftMagicNumbers.INSTANCE;
    }

    private final Spigot spigot = new Spigot()
    {

        @Override
        public YamlConfiguration getConfig()
        {
            return org.spigotmc.SpigotConfig.config;
        }

        // PaperSpigot start - Add getTPS (Further improve tick loop)
        @Override
        public double[] getTPS() {
            return new double[] {
                    MinecraftServer.getServer().tps1.getAverage(),
                    MinecraftServer.getServer().tps5.getAverage(),
                    MinecraftServer.getServer().tps15.getAverage()
            };
        }
        // PaperSpigot end

        /**
         * Sends the component to the player
         *
         * @param component the components to send
         */
        @Override
        public void broadcast(net.md_5.bungee.api.chat.BaseComponent component)
        {
            for ( Player player : getOnlinePlayers() )
            {
                player.spigot().sendMessage( component );
            }
        }

        /**
         * Sends an array of components as a single message to the
         * player
         *
         * @param components the components to send
         */
        @Override
        public void broadcast(net.md_5.bungee.api.chat.BaseComponent ...components)
        {
            for ( Player player : getOnlinePlayers() )
            {
                player.spigot().sendMessage( components );
            }
        }
    };

    public Spigot spigot()
    {
        return spigot;
    }
}
