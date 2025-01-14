package net.minecraft.server;

import java.io.File;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

import net.minecraft.server.PacketPlayOutPlayerInfo.PlayerInfo;
import net.minecraft.util.com.google.common.base.Charsets;
import net.minecraft.util.com.google.common.collect.Lists;
import net.minecraft.util.com.google.common.collect.Maps;
import net.minecraft.util.com.mojang.authlib.GameProfile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.chunkio.ChunkIOExecutor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.TravelAgent;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.Vector;
import org.github.paperspigot.PaperSpigotConfig;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
// CraftBukkit end

public abstract class PlayerList {

    public static final File a = new File("banned-players.json");
    public static final File b = new File("banned-ips.json");
    public static final File c = new File("ops.json");
    public static final File d = new File("whitelist.json");
    private static final Logger g = LogManager.getLogger();
    private static final SimpleDateFormat h = new SimpleDateFormat("yyyy-MM-dd \'at\' HH:mm:ss z");
    private final MinecraftServer server;
    public final List<EntityPlayer> players = new java.util.concurrent.CopyOnWriteArrayList(); // CraftBukkit - ArrayList -> CopyOnWriteArrayList: Iterator safety
    // PaperSpigot start - Player lookup improvements
    public final Map<String, EntityPlayer> playerMap = new java.util.HashMap<String, EntityPlayer>() {
        @Override
        public EntityPlayer put(String key, EntityPlayer value) {
            return super.put(key.toLowerCase(), value);
        }

        @Override
        public EntityPlayer get(Object key) {
            // put the .playerConnection check done in other places here
            EntityPlayer player = super.get(key instanceof String ? ((String)key).toLowerCase() : key);
            return (player != null && player.playerConnection != null) ? player : null;
        }

        @Override
        public boolean containsKey(Object key) {
            return get(key) != null;
        }

        @Override
        public EntityPlayer remove(Object key) {
            return super.remove(key instanceof String ? ((String)key).toLowerCase() : key);
        }
    };
    public final Map<UUID, EntityPlayer> uuidMap = new java.util.HashMap<UUID, EntityPlayer>() {
        @Override
        public EntityPlayer get(Object key) {
            // put the .playerConnection check done in other places here
            EntityPlayer player = super.get(key instanceof String ? ((String)key).toLowerCase() : key);
            return (player != null && player.playerConnection != null) ? player : null;
        }
    };
    // PaperSpigot end
    private final GameProfileBanList j;
    private final IpBanList k;
    private final OpList operators;
    private final WhiteList whitelist;
    private final Map n;
    public IPlayerFileData playerFileData; // CraftBukkit - private -> public
    public boolean hasWhitelist; // CraftBukkit - private -> public
    protected int maxPlayers;
    private int q;
    private EnumGamemode r;
    private boolean s;
    private int t;

    // CraftBukkit start
    private CraftServer cserver;

    public PlayerList(MinecraftServer minecraftserver) {
        minecraftserver.server = new CraftServer(minecraftserver, this);
        minecraftserver.console = org.bukkit.craftbukkit.command.ColouredConsoleSender.getInstance();
        minecraftserver.reader.addCompleter(new org.bukkit.craftbukkit.command.ConsoleCommandCompleter(minecraftserver.server));
        this.cserver = minecraftserver.server;
        // CraftBukkit end

        this.j = new GameProfileBanList(a);
        this.k = new IpBanList(b);
        this.operators = new OpList(c);
        this.whitelist = new WhiteList(d);
        this.n = Maps.newHashMap();
        this.server = minecraftserver;
        this.j.a(false);
        this.k.a(false);
        this.maxPlayers = 8;
    }

    public void a(NetworkManager networkmanager, EntityPlayer entityplayer) {
        GameProfile gameprofile = entityplayer.getProfile();
        UserCache usercache = this.server.getUserCache();
        GameProfile gameprofile1 = usercache.a(gameprofile.getId());
        String s = gameprofile1 == null ? gameprofile.getName() : gameprofile1.getName();

        usercache.a(gameprofile);
        NBTTagCompound nbttagcompound = this.a(entityplayer);

        entityplayer.spawnIn(this.server.getWorldServer(entityplayer.dimension));
        entityplayer.playerInteractManager.a((WorldServer) entityplayer.world);
        String s1 = "local";

        if (networkmanager.getSocketAddress() != null) {
            s1 = networkmanager.getSocketAddress().toString();
        }

        // Spigot start - spawn location event
        Player bukkitPlayer = entityplayer.getBukkitEntity();
        PlayerSpawnLocationEvent ev = new PlayerSpawnLocationEvent(bukkitPlayer, bukkitPlayer.getLocation());
        Bukkit.getPluginManager().callEvent(ev);

        Location loc = ev.getSpawnLocation();
        WorldServer world = ((CraftWorld) loc.getWorld()).getHandle();

        entityplayer.spawnIn(world);
        entityplayer.setPosition(loc.getX(), loc.getY(), loc.getZ());
        entityplayer.b(loc.getYaw(), loc.getPitch()); // should be setYawAndPitch
        // Spigot end

        // CraftBukkit - Moved message to after join
        // g.info(entityplayer.getName() + "[" + s1 + "] logged in with entity id " + entityplayer.getId() + " at (" + entityplayer.locX + ", " + entityplayer.locY + ", " + entityplayer.locZ + ")");
        WorldServer worldserver = this.server.getWorldServer(entityplayer.dimension);
        ChunkCoordinates chunkcoordinates = worldserver.getSpawn();

        this.a(entityplayer, (EntityPlayer) null, worldserver);
        PlayerConnection playerconnection = new PlayerConnection(this.server, networkmanager, entityplayer);

        // CraftBukkit start - Don't send a higher than 60 MaxPlayer size, otherwise the PlayerInfo window won't render correctly.
        int maxPlayers = this.getMaxPlayers();
        if (maxPlayers > 60) {
            maxPlayers = 60;
        }
        playerconnection.sendPacket(new PacketPlayOutLogin(entityplayer.getId(), entityplayer.playerInteractManager.getGameMode(), worldserver.getWorldData().isHardcore(), worldserver.worldProvider.dimension, worldserver.difficulty, maxPlayers, worldserver.getWorldData().getType()));
        entityplayer.getBukkitEntity().sendSupportedChannels();
        // CraftBukkit end
        playerconnection.sendPacket(new PacketPlayOutCustomPayload("MC|Brand", this.getServer().getServerModName().getBytes(Charsets.UTF_8)));
        playerconnection.sendPacket(new PacketPlayOutSpawnPosition(chunkcoordinates.x, chunkcoordinates.y, chunkcoordinates.z));
        playerconnection.sendPacket(new PacketPlayOutAbilities(entityplayer.abilities));
        playerconnection.sendPacket(new PacketPlayOutHeldItemSlot(entityplayer.inventory.itemInHandIndex));
        entityplayer.getStatisticManager().d();
        entityplayer.getStatisticManager().updateStatistics(entityplayer);
        this.sendScoreboard((ScoreboardServer) worldserver.getScoreboard(), entityplayer);
        this.server.az();
        /* CraftBukkit start - login message is handled in the event
        ChatMessage chatmessage;

        if (!entityplayer.getName().equalsIgnoreCase(s)) {
            chatmessage = new ChatMessage("multiplayer.player.joined.renamed", new Object[] { entityplayer.getScoreboardDisplayName(), s});
        } else {
            chatmessage = new ChatMessage("multiplayer.player.joined", new Object[] { entityplayer.getScoreboardDisplayName()});
        }

        chatmessage.getChatModifier().setColor(EnumChatFormat.YELLOW);
        this.sendMessage(chatmessage);
        // CraftBukkit end */
        this.c(entityplayer);
        worldserver = this.server.getWorldServer(entityplayer.dimension); // CraftBukkit - Update in case join event changed it
        playerconnection.a(entityplayer.locX, entityplayer.locY, entityplayer.locZ, entityplayer.yaw, entityplayer.pitch);
        this.b(entityplayer, worldserver);
        if (this.server.getResourcePack().length() > 0) {
            entityplayer.setResourcePack(this.server.getResourcePack());
        }

        Iterator iterator = entityplayer.getEffects().iterator();

        while (iterator.hasNext()) {
            MobEffect mobeffect = (MobEffect) iterator.next();

            playerconnection.sendPacket(new PacketPlayOutEntityEffect(entityplayer.getId(), mobeffect));
        }

        entityplayer.syncInventory();
        if (nbttagcompound != null && nbttagcompound.hasKeyOfType("Riding", 10)) {
            Entity entity = EntityTypes.a(nbttagcompound.getCompound("Riding"), worldserver);

            if (entity != null) {
                entity.attachedToPlayer = true;
                worldserver.addEntity(entity);
                entityplayer.mount(entity);
                entity.attachedToPlayer = false;
            }
        }

        // CraftBukkit - Moved from above, added world
        g.info(entityplayer.getName() + (PaperSpigotConfig.logPlayerIp ? " [" + s1 + "]" : "") + " logged in with entity id (" + entityplayer.getId() + ") at ([" + entityplayer.world.worldData.getName() + "] " + entityplayer.locX + ", " + entityplayer.locY + ", " + entityplayer.locZ + ")");
    }

    public void sendScoreboard(ScoreboardServer scoreboardserver, EntityPlayer entityplayer) { // CraftBukkit - protected -> public
        HashSet hashset = new HashSet();
        Iterator iterator = scoreboardserver.getTeams().iterator();

        while (iterator.hasNext()) {
            ScoreboardTeam scoreboardteam = (ScoreboardTeam) iterator.next();

            entityplayer.playerConnection.sendPacket(new PacketPlayOutScoreboardTeam(scoreboardteam, 0));
        }

        for (int i = 0; i < 3; ++i) {
            ScoreboardObjective scoreboardobjective = scoreboardserver.getObjectiveForSlot(i);

            if (scoreboardobjective != null && !hashset.contains(scoreboardobjective)) {
                List list = scoreboardserver.getScoreboardScorePacketsForObjective(scoreboardobjective);
                Iterator iterator1 = list.iterator();

                while (iterator1.hasNext()) {
                    Packet packet = (Packet) iterator1.next();

                    entityplayer.playerConnection.sendPacket(packet);
                }

                hashset.add(scoreboardobjective);
            }
        }
    }

    public void setPlayerFileData(WorldServer[] aworldserver) {
        if (this.playerFileData != null) return; // CraftBukkit
        this.playerFileData = aworldserver[0].getDataManager().getPlayerFileData();
    }

    public void a(EntityPlayer entityplayer, WorldServer worldserver) {
        WorldServer worldserver1 = entityplayer.r();

        if (worldserver != null) {
            worldserver.getPlayerChunkMap().removePlayer(entityplayer);
        }

        worldserver1.getPlayerChunkMap().addPlayer(entityplayer);
        worldserver1.chunkProviderServer.getChunkAt((int) entityplayer.locX >> 4, (int) entityplayer.locZ >> 4);
    }

    public int d() {
        return PlayerChunkMap.getFurthestViewableBlock(this.s());
    }

    public NBTTagCompound a(EntityPlayer entityplayer) {
        // CraftBukkit - fix reference to worldserver array
        NBTTagCompound nbttagcompound = this.server.worlds.get(0).getWorldData().i();
        NBTTagCompound nbttagcompound1;

        if (entityplayer.getName().equals(this.server.M()) && nbttagcompound != null) {
            entityplayer.f(nbttagcompound);
            nbttagcompound1 = nbttagcompound;
            g.debug("loading single player");
        } else {
            nbttagcompound1 = this.playerFileData.load(entityplayer);
        }

        return nbttagcompound1;
    }

    protected void b(EntityPlayer entityplayer) {
        this.playerFileData.save(entityplayer);
        ServerStatisticManager serverstatisticmanager = (ServerStatisticManager) this.n.get(entityplayer.getUniqueID());

        if (serverstatisticmanager != null) {
            serverstatisticmanager.b();
        }
    }

    public void c(EntityPlayer entityplayer) {
        cserver.detectListNameConflict(entityplayer); // CraftBukkit
        // this.sendAll(new PacketPlayOutPlayerInfo(entityplayer.getName(), true, 1000)); // CraftBukkit - replaced with loop below
        this.players.add(entityplayer);
        this.playerMap.put(entityplayer.getName(), entityplayer); // PaperSpigot
        this.uuidMap.put(entityplayer.getUniqueID(), entityplayer); // PaperSpigot
        WorldServer worldserver = this.server.getWorldServer(entityplayer.dimension);

        // CraftBukkit start
        PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(this.cserver.getPlayer(entityplayer), "\u00A7e" + entityplayer.getName() + " joined the game.");
        this.cserver.getPluginManager().callEvent(playerJoinEvent);

        String joinMessage = playerJoinEvent.getJoinMessage();

        if ((joinMessage != null) && (joinMessage.length() > 0)) {
            for (IChatBaseComponent line : org.bukkit.craftbukkit.util.CraftChatMessage.fromString(joinMessage)) {
                this.server.getPlayerList().sendAll(new PacketPlayOutChat(line));
            }
        }
        this.cserver.onPlayerJoin(playerJoinEvent.getPlayer());

        ChunkIOExecutor.adjustPoolSize(this.getPlayerCount());
        // CraftBukkit end

        // CraftBukkit start - Only add if the player wasn't moved in the event
        if (entityplayer.world == worldserver && !worldserver.players.contains(entityplayer)) {
            worldserver.addEntity(entityplayer);
            this.a(entityplayer, (WorldServer) null);
        }
        // CraftBukkit end

     // Rinny start - do all in a single for loop
        final PacketPlayOutPlayerInfo packet = new PacketPlayOutPlayerInfo(entityplayer, PlayerInfo.ADD_PLAYER); // Spigot - protocol patch
        final PacketPlayOutPlayerInfo displayPacket = new PacketPlayOutPlayerInfo(entityplayer, PlayerInfo.UPDATE_DISPLAY_NAME); // Spigot - protocol patch
        for (EntityPlayer entityplayer1 : this.players) {
            // .name -> .listName
        	entityplayer1.playerConnection.sendPacket(packet);
            entityplayer.playerConnection.sendPacket(new PacketPlayOutPlayerInfo(entityplayer1, PlayerInfo.ADD_PLAYER)); // Spigot - protocol patch
            // Spigot start - protocol patch
            if (!entityplayer.getName().equals(entityplayer.listName)) {
            	if (entityplayer.playerConnection.networkManager.getVersion() > 28) {
            		entityplayer.playerConnection.sendPacket(new PacketPlayOutPlayerInfo(entityplayer1, PlayerInfo.UPDATE_DISPLAY_NAME));
            	}
            	if (entityplayer1.playerConnection.networkManager.getVersion() > 28) {
            		entityplayer1.playerConnection.sendPacket(displayPacket);
            	}
            }
            // Spigot end
            // CraftBukkit end
        }
        // Rinny stop 
    }

    public void d(EntityPlayer entityplayer) {
        entityplayer.r().getPlayerChunkMap().movePlayer(entityplayer);
    }

    public String disconnect(EntityPlayer entityplayer) { // CraftBukkit - return string
        entityplayer.a(StatisticList.f);

        // CraftBukkit start - Quitting must be before we do final save of data, in case plugins need to modify it
        org.bukkit.craftbukkit.event.CraftEventFactory.handleInventoryCloseEvent(entityplayer);

        PlayerQuitEvent playerQuitEvent = new PlayerQuitEvent(this.cserver.getPlayer(entityplayer), "\u00A7e" + entityplayer.getName() + " left the game.");
        this.cserver.getPluginManager().callEvent(playerQuitEvent);
        entityplayer.getBukkitEntity().disconnect(playerQuitEvent.getQuitMessage());
        // CraftBukkit end

        this.b(entityplayer);
        WorldServer worldserver = entityplayer.r();

        if (entityplayer.vehicle != null && !(entityplayer.vehicle instanceof EntityPlayer)) { // CraftBukkit - Don't remove players
            worldserver.removeEntity(entityplayer.vehicle);
            g.debug("removing player mount");
        }

        worldserver.kill(entityplayer);
        worldserver.getPlayerChunkMap().removePlayer(entityplayer);
        this.players.remove(entityplayer);
        this.uuidMap.remove(entityplayer.getUniqueID()); // PaperSpigot
        this.playerMap.remove(entityplayer.getName()); // PaperSpigot
        this.n.remove(entityplayer.getUniqueID());
        ChunkIOExecutor.adjustPoolSize(this.getPlayerCount()); // CraftBukkit

        // CraftBukkit start - .name -> .listName, replace sendAll with loop
        // this.sendAll(new PacketPlayOutPlayerInfo(entityplayer.getName(), false, 9999));
        PacketPlayOutPlayerInfo packet = new PacketPlayOutPlayerInfo(entityplayer, PacketPlayOutPlayerInfo.PlayerInfo.REMOVE_PLAYER); // Spigot - protocol patch
        for (int i = 0; i < this.players.size(); ++i) {
            EntityPlayer entityplayer1 = (EntityPlayer) this.players.get(i);

            if (entityplayer1.getBukkitEntity().canSee(entityplayer.getBukkitEntity())) {
                entityplayer1.playerConnection.sendPacket(packet);
            } else {
                entityplayer1.getBukkitEntity().removeDisconnectingPlayer(entityplayer.getBukkitEntity());
            }
        }
        // This removes the scoreboard (and player reference) for the specific player in the manager
        this.cserver.getScoreboardManager().removePlayer(entityplayer.getBukkitEntity());

        entityplayer.r().getTracker().untrackPlayer(entityplayer);// Rinny - untrack player
        return playerQuitEvent.getQuitMessage();
        // CraftBukkit end
    }

    // CraftBukkit start - Whole method, SocketAddress to LoginListener, added hostname to signature, return EntityPlayer
    public EntityPlayer attemptLogin(LoginListener loginlistener, GameProfile gameprofile, String hostname) {
        // Instead of kicking then returning, we need to store the kick reason
        // in the event, check with plugins to see if it's ok, and THEN kick
        // depending on the outcome.
        SocketAddress socketaddress = loginlistener.networkManager.getSocketAddress();

        EntityPlayer entity = new EntityPlayer(this.server, this.server.getWorldServer(0), gameprofile, new PlayerInteractManager(this.server.getWorldServer(0)));
        Player player = entity.getBukkitEntity();
        PlayerLoginEvent event = new PlayerLoginEvent(player, hostname, ((java.net.InetSocketAddress) socketaddress).getAddress(), ((java.net.InetSocketAddress) loginlistener.networkManager.getRawAddress()).getAddress());
        String s;

        if (this.j.isBanned(gameprofile) && !this.j.get(gameprofile).hasExpired()) {
            GameProfileBanEntry gameprofilebanentry = (GameProfileBanEntry) this.j.get(gameprofile);

            s = "You are banned from this server!\nReason: " + gameprofilebanentry.getReason();
            if (gameprofilebanentry.getExpires() != null) {
                s = s + "\nYour ban will be removed on " + h.format(gameprofilebanentry.getExpires());
            }

            // return s;
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, s); // Spigot
        } else if (!this.isWhitelisted(gameprofile)) {
            // return "You are not white-listed on this server!";
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, org.spigotmc.SpigotConfig.whitelistMessage); // Spigot
        } else if (this.k.isBanned(socketaddress) && !this.k.get(socketaddress).hasExpired()) { // Spigot
            IpBanEntry ipbanentry = this.k.get(socketaddress);

            s = "Your IP address is banned from this server!\nReason: " + ipbanentry.getReason();
            if (ipbanentry.getExpires() != null) {
                s = s + "\nYour ban will be removed on " + h.format(ipbanentry.getExpires());
            }

            // return s;
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, s);
        } else {
            // return this.players.size() >= this.maxPlayers ? "The server is full!" : null;
            if (this.players.size() >= this.maxPlayers && (!player.isOp() && !player.hasPermission("bukkit.whitelist.bypass"))) { // Rinny allow to join server even if its full
                event.disallow(PlayerLoginEvent.Result.KICK_FULL, org.spigotmc.SpigotConfig.serverFullMessage); // Spigot
            }
        }

        this.cserver.getPluginManager().callEvent(event);
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            loginlistener.disconnect(event.getKickMessage());
            return null;
        }

        return entity;
        // CraftBukkit end
    }

    public EntityPlayer processLogin(GameProfile gameprofile, EntityPlayer player) { // CraftBukkit - added EntityPlayer
        UUID uuid = EntityHuman.a(gameprofile);
        ArrayList arraylist = Lists.newArrayList();

        EntityPlayer entityplayer;

        /* // PaperSpigot start - Use exact lookup below
        for (int i = 0; i < this.players.size(); ++i) {
            entityplayer = (EntityPlayer) this.players.get(i);
            if (entityplayer.getUniqueID().equals(uuid)) {
                arraylist.add(entityplayer);
            }
        }

        Iterator iterator = arraylist.iterator();

        while (iterator.hasNext()) {
            entityplayer = (EntityPlayer) iterator.next();
        */
        if ((entityplayer = uuidMap.get(uuid)) != null) {
            // PaperSpigot end
            entityplayer.playerConnection.disconnect("You logged in from another location");
        }

        /* CraftBukkit start
        Object object;

        if (this.server.R()) {
            object = new DemoPlayerInteractManager(this.server.getWorldServer(0));
        } else {
            object = new PlayerInteractManager(this.server.getWorldServer(0));
        }

        return new EntityPlayer(this.server, this.server.getWorldServer(0), gameprofile, (PlayerInteractManager) object);
        // */
        return player;
        // CraftBukkit end
    }

    // CraftBukkit start
    public EntityPlayer moveToWorld(EntityPlayer entityplayer, int i, boolean flag) {
        return this.moveToWorld(entityplayer, i, flag, null, true);
    }

    public EntityPlayer moveToWorld(EntityPlayer entityplayer, int i, boolean flag, Location location, boolean avoidSuffocation) {
        // CraftBukkit end
        entityplayer.r().getTracker().untrackPlayer(entityplayer);
        // entityplayer.r().getTracker().untrackEntity(entityplayer); // CraftBukkit
        entityplayer.r().getPlayerChunkMap().removePlayer(entityplayer);
        this.players.remove(entityplayer);
        this.server.getWorldServer(entityplayer.dimension).removeEntity(entityplayer);
        ChunkCoordinates chunkcoordinates = entityplayer.getBed();
        boolean flag1 = entityplayer.isRespawnForced();

        /* CraftBukkit start
        entityplayer.dimension = i;
        Object object;

        if (this.server.R()) {
            object = new DemoPlayerInteractManager(this.server.getWorldServer(entityplayer.dimension));
        } else {
            object = new PlayerInteractManager(this.server.getWorldServer(entityplayer.dimension));
        }

        EntityPlayer entityplayer1 = new EntityPlayer(this.server, this.server.getWorldServer(entityplayer.dimension), entityplayer.getProfile(), (PlayerInteractManager) object);
        // */
        EntityPlayer entityplayer1 = entityplayer;
        org.bukkit.World fromWorld = entityplayer1.getBukkitEntity().getWorld();
        entityplayer1.viewingCredits = false;
        // CraftBukkit end

        entityplayer1.playerConnection = entityplayer.playerConnection;
        entityplayer1.copyTo(entityplayer, flag);
        entityplayer1.d(entityplayer.getId());
        // WorldServer worldserver = this.server.getWorldServer(entityplayer.dimension); // CraftBukkit - handled later

        // this.a(entityplayer1, entityplayer, worldserver); // CraftBukkit - removed
        ChunkCoordinates chunkcoordinates1;

        // CraftBukkit start - fire PlayerRespawnEvent
        if (location == null) {
            boolean isBedSpawn = false;
            CraftWorld cworld = (CraftWorld) this.server.server.getWorld(entityplayer.spawnWorld);
            if (cworld != null && chunkcoordinates != null) {
                chunkcoordinates1 = EntityHuman.getBed(cworld.getHandle(), chunkcoordinates, flag1);
                if (chunkcoordinates1 != null) {
                    isBedSpawn = true;
                    location = new Location(cworld, chunkcoordinates1.x + 0.5, chunkcoordinates1.y, chunkcoordinates1.z + 0.5);
                } else {
                    entityplayer1.setRespawnPosition(null, true);
                    entityplayer1.playerConnection.sendPacket(new PacketPlayOutGameStateChange(0, 0));
                }
            }

            if (location == null) {
                cworld = (CraftWorld) this.server.server.getWorlds().get(0);
                chunkcoordinates = cworld.getHandle().getSpawn();
                location = new Location(cworld, chunkcoordinates.x + 0.5, chunkcoordinates.y, chunkcoordinates.z + 0.5);
            }

            Player respawnPlayer = this.cserver.getPlayer(entityplayer1);
            PlayerRespawnEvent respawnEvent = new PlayerRespawnEvent(respawnPlayer, location, isBedSpawn);
            this.cserver.getPluginManager().callEvent(respawnEvent);
            // Spigot Start
            if (entityplayer.playerConnection.isDisconnected()) {
                return entityplayer;
            }
            // Spigot End

            location = respawnEvent.getRespawnLocation();
            entityplayer.reset();
        } else {
            location.setWorld(this.server.getWorldServer(i).getWorld());
        }
        WorldServer worldserver = ((CraftWorld) location.getWorld()).getHandle();
        entityplayer1.setLocation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        // CraftBukkit end

        worldserver.chunkProviderServer.getChunkAt((int) entityplayer1.locX >> 4, (int) entityplayer1.locZ >> 4);

        while (avoidSuffocation && !worldserver.getCubes(entityplayer1, entityplayer1.boundingBox).isEmpty()) { // CraftBukkit
            entityplayer1.setPosition(entityplayer1.locX, entityplayer1.locY + 1.0D, entityplayer1.locZ);
        }

        // CraftBukkit start
        byte actualDimension = (byte) (worldserver.getWorld().getEnvironment().getId());
        // Force the client to refresh their chunk cache.
        entityplayer1.playerConnection.sendPacket(new PacketPlayOutRespawn((byte) (actualDimension >= 0 ? -1 : 0), worldserver.difficulty, worldserver.getWorldData().getType(), entityplayer.playerInteractManager.getGameMode()));
        entityplayer1.playerConnection.sendPacket(new PacketPlayOutRespawn(actualDimension, worldserver.difficulty, worldserver.getWorldData().getType(), entityplayer1.playerInteractManager.getGameMode()));
        entityplayer1.spawnIn(worldserver);
        entityplayer1.dead = false;
        entityplayer1.playerConnection.teleport(new Location(worldserver.getWorld(), entityplayer1.locX, entityplayer1.locY, entityplayer1.locZ, entityplayer1.yaw, entityplayer1.pitch));
        entityplayer1.setSneaking(false);
        chunkcoordinates1 = worldserver.getSpawn();
        // entityplayer1.playerConnection.a(entityplayer1.locX, entityplayer1.locY, entityplayer1.locZ, entityplayer1.yaw, entityplayer1.pitch);
        // CraftBukkit end
        entityplayer1.playerConnection.sendPacket(new PacketPlayOutSpawnPosition(chunkcoordinates1.x, chunkcoordinates1.y, chunkcoordinates1.z));
        entityplayer1.playerConnection.sendPacket(new PacketPlayOutExperience(entityplayer1.exp, entityplayer1.expTotal, entityplayer1.expLevel));
        this.b(entityplayer1, worldserver);
        // CraftBukkit start
        // Don't re-add player to player list if disconnected
        if (!entityplayer.playerConnection.isDisconnected()) {
            worldserver.getPlayerChunkMap().addPlayer(entityplayer1);
            worldserver.addEntity(entityplayer1);
            this.players.add(entityplayer1);
        }
        // Added from changeDimension
        this.updateClient(entityplayer1); // Update health, etc...
        entityplayer1.updateAbilities();
        Iterator iterator = entityplayer1.getEffects().iterator();

        while (iterator.hasNext()) {
            MobEffect mobeffect = (MobEffect) iterator.next();

            entityplayer1.playerConnection.sendPacket(new PacketPlayOutEntityEffect(entityplayer1.getId(), mobeffect));
        }
        // entityplayer1.syncInventory();
        // CraftBukkit end
        entityplayer1.setHealth(entityplayer1.getHealth());

        // CraftBukkit start
        // Don't fire on respawn
        if (fromWorld != location.getWorld()) {
            PlayerChangedWorldEvent event = new PlayerChangedWorldEvent((Player) entityplayer1.getBukkitEntity(), fromWorld);
            Bukkit.getServer().getPluginManager().callEvent(event);
        }

        // Save player file again if they were disconnected
        if (entityplayer.playerConnection.isDisconnected()) {
            this.b(entityplayer1);
        }
        // CraftBukkit end

        return entityplayer1;
    }

    // CraftBukkit start - Replaced the standard handling of portals with a more customised method.
    public void changeDimension(EntityPlayer entityplayer, int i, TeleportCause cause) {
        WorldServer exitWorld = null;
        if (entityplayer.dimension < CraftWorld.CUSTOM_DIMENSION_OFFSET) { // plugins must specify exit from custom Bukkit worlds
            // only target existing worlds (compensate for allow-nether/allow-end as false)
            for (WorldServer world : this.server.worlds) {
                if (world.dimension == i) {
                    exitWorld = world;
                }
            }
        }

        Location enter = entityplayer.getBukkitEntity().getLocation();
        Location exit = null;
        boolean useTravelAgent = false; // don't use agent for custom worlds or return from THE_END
        if (exitWorld != null) {
            if ((cause == TeleportCause.END_PORTAL) && (i == 0)) {
                // THE_END -> NORMAL; use bed if available, otherwise default spawn
                exit = ((org.bukkit.craftbukkit.entity.CraftPlayer) entityplayer.getBukkitEntity()).getBedSpawnLocation();
                if (exit == null || ((CraftWorld) exit.getWorld()).getHandle().dimension != 0) {
                    exit = exitWorld.getWorld().getSpawnLocation();
                }
            } else {
                // NORMAL <-> NETHER or NORMAL -> THE_END
                exit = this.calculateTarget(enter, exitWorld);
                useTravelAgent = true;
            }
        }

        TravelAgent agent = exit != null ? (TravelAgent) ((CraftWorld) exit.getWorld()).getHandle().getTravelAgent() : org.bukkit.craftbukkit.CraftTravelAgent.DEFAULT; // return arbitrary TA to compensate for implementation dependent plugins
        agent.setCanCreatePortal(cause != TeleportCause.END_PORTAL); // PaperSpigot - Configurable end credits, don't allow End Portals to create portals

        PlayerPortalEvent event = new PlayerPortalEvent(entityplayer.getBukkitEntity(), enter, exit, agent, cause);
        event.useTravelAgent(useTravelAgent);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getTo() == null) {
            return;
        }

        // PaperSpigot - Configurable end credits, if a plugin sets to use a travel agent even if the cause is an end portal, ignore it
        exit = cause != TeleportCause.END_PORTAL && event.useTravelAgent() ? event.getPortalTravelAgent().findOrCreate(event.getTo()) : event.getTo();
        if (exit == null) {
            return;
        }
        exitWorld = ((CraftWorld) exit.getWorld()).getHandle();

        Vector velocity = entityplayer.getBukkitEntity().getVelocity();
        boolean before = exitWorld.chunkProviderServer.forceChunkLoad;
        exitWorld.chunkProviderServer.forceChunkLoad = true;
        exitWorld.getTravelAgent().adjustExit(entityplayer, exit, velocity);
        exitWorld.chunkProviderServer.forceChunkLoad = before;

        this.moveToWorld(entityplayer, exitWorld.dimension, true, exit, false); // Vanilla doesn't check for suffocation when handling portals, so neither should we
        if (entityplayer.motX != velocity.getX() || entityplayer.motY != velocity.getY() || entityplayer.motZ != velocity.getZ()) {
            entityplayer.getBukkitEntity().setVelocity(velocity);
        }
        // CraftBukkit end
    }

    public void a(Entity entity, int i, WorldServer worldserver, WorldServer worldserver1) {
        // CraftBukkit start - Split into modular functions
        Location exit = this.calculateTarget(entity.getBukkitEntity().getLocation(), worldserver1);
        this.repositionEntity(entity, exit, true);
    }

    // Copy of original a(Entity, int, WorldServer, WorldServer) method with only location calculation logic
    public Location calculateTarget(Location enter, World target) {
        WorldServer worldserver = ((CraftWorld) enter.getWorld()).getHandle();
        WorldServer worldserver1 = ((CraftWorld) target.getWorld()).getHandle();
        int i = worldserver.dimension;

        double y = enter.getY();
        float yaw = enter.getYaw();
        float pitch = enter.getPitch();
        double d0 = enter.getX();
        double d1 = enter.getZ();
        double d2 = 8.0D;
        /*
        double d3 = entity.locX;
        double d4 = entity.locY;
        double d5 = entity.locZ;
        float f = entity.yaw;

        worldserver.methodProfiler.a("moving");
        */
        if (worldserver1.dimension == -1) {
            d0 /= d2;
            d1 /= d2;
            /*
            entity.setPositionRotation(d0, entity.locY, d1, entity.yaw, entity.pitch);
            if (entity.isAlive()) {
                worldserver.entityJoinedWorld(entity, false);
            }
            */
        } else if (worldserver1.dimension == 0) {
            d0 *= d2;
            d1 *= d2;
            /*
            entity.setPositionRotation(d0, entity.locY, d1, entity.yaw, entity.pitch);
            if (entity.isAlive()) {
                worldserver.entityJoinedWorld(entity, false);
            }
            */
        } else {
            ChunkCoordinates chunkcoordinates;

            if (i == 1) {
                // use default NORMAL world spawn instead of target
                worldserver1 = this.server.worlds.get(0);
                chunkcoordinates = worldserver1.getSpawn();
            } else {
                chunkcoordinates = worldserver1.getDimensionSpawn();
            }

            d0 = (double) chunkcoordinates.x;
            y = (double) chunkcoordinates.y;
            d1 = (double) chunkcoordinates.z;
            yaw = 90.0F;
            pitch = 0.0F;
            /*
            entity.setPositionRotation(d0, entity.locY, d1, 90.0F, 0.0F);
            if (entity.isAlive()) {
                worldserver.entityJoinedWorld(entity, false);
            }
            */
        }

        // worldserver.methodProfiler.b();
        if (i != 1) {
            // worldserver.methodProfiler.a("placing");
            d0 = (double) MathHelper.a((int) d0, -29999872, 29999872);
            d1 = (double) MathHelper.a((int) d1, -29999872, 29999872);
            /*
            if (entity.isAlive()) {
                worldserver1.addEntity(entity);
                entity.setPositionRotation(d0, entity.locY, d1, entity.yaw, entity.pitch);
                worldserver1.entityJoinedWorld(entity, false);
                worldserver1.getTravelAgent().a(entity, d3, d4, d5, f);
            }

            worldserver.methodProfiler.b();
            */
        }

        // entity.spawnIn(worldserver1);
        return new Location(worldserver1.getWorld(), d0, y, d1, yaw, pitch);
    }

    // copy of original a(Entity, int, WorldServer, WorldServer) method with only entity repositioning logic
    public void repositionEntity(Entity entity, Location exit, boolean portal) {
        int i = entity.dimension;
        WorldServer worldserver = (WorldServer) entity.world;
        WorldServer worldserver1 = ((CraftWorld) exit.getWorld()).getHandle();
        /*
        double d0 = entity.locX;
        double d1 = entity.locZ;
        double d2 = 8.0D;
        double d3 = entity.locX;
        double d4 = entity.locY;
        double d5 = entity.locZ;
        float f = entity.yaw;
        */

        worldserver.methodProfiler.a("moving");
        entity.setPositionRotation(exit.getX(), exit.getY(), exit.getZ(), exit.getYaw(), exit.getPitch());
        if (entity.isAlive()) {
            worldserver.entityJoinedWorld(entity, false);
        }
        /*
        if (entity.dimension == -1) {
            d0 /= d2;
            d1 /= d2;
            entity.setPositionRotation(d0, entity.locY, d1, entity.yaw, entity.pitch);
            if (entity.isAlive()) {
                worldserver.entityJoinedWorld(entity, false);
            }
        } else if (entity.dimension == 0) {
            d0 *= d2;
            d1 *= d2;
            entity.setPositionRotation(d0, entity.locY, d1, entity.yaw, entity.pitch);
            if (entity.isAlive()) {
                worldserver.entityJoinedWorld(entity, false);
            }
        } else {
            ChunkCoordinates chunkcoordinates;

            if (i == 1) {
                chunkcoordinates = worldserver1.getSpawn();
            } else {
                chunkcoordinates = worldserver1.getDimensionSpawn();
            }

            d0 = (double) chunkcoordinates.x;
            entity.locY = (double) chunkcoordinates.y;
            d1 = (double) chunkcoordinates.z;
            entity.setPositionRotation(d0, entity.locY, d1, 90.0F, 0.0F);
            if (entity.isAlive()) {
                worldserver.entityJoinedWorld(entity, false);
            }
        }
        */

        worldserver.methodProfiler.b();
        if (i != 1) {
            worldserver.methodProfiler.a("placing");
            /*
            d0 = (double) MathHelper.a((int) d0, -29999872, 29999872);
            d1 = (double) MathHelper.a((int) d1, -29999872, 29999872);
            */
            if (entity.isAlive()) {
                // entity.setPositionRotation(d0, entity.locY, d1, entity.yaw, entity.pitch)
                // worldserver1.getTravelAgent().a(entity, d3, d4, d5, f);
                if (portal) {
                    Vector velocity = entity.getBukkitEntity().getVelocity();
                    worldserver1.getTravelAgent().adjustExit(entity, exit, velocity);
                    entity.setPositionRotation(exit.getX(), exit.getY(), exit.getZ(), exit.getYaw(), exit.getPitch());
                    if (entity.motX != velocity.getX() || entity.motY != velocity.getY() || entity.motZ != velocity.getZ()) {
                        entity.getBukkitEntity().setVelocity(velocity);
                    }
                }
                worldserver1.addEntity(entity);
                worldserver1.entityJoinedWorld(entity, false);
            }

            worldserver.methodProfiler.b();
        }

        entity.spawnIn(worldserver1);
        // CraftBukkit end
    }

    private int currentPing = 0;

    public void tick() {
        if (++this.t > 600) {
            this.t = 0;
        }

        /* CraftBukkit start - Remove updating of lag to players -- it spams way to much on big servers.
        if (this.t < this.players.size()) {
            EntityPlayer entityplayer = (EntityPlayer) this.players.get(this.p);

            this.sendAll(new PacketPlayOutPlayerInfo(entityplayer.getName(), true, entityplayer.ping));
        }
        // CraftBukkit end */
        // Spigot start
        try
        {
            if ( !players.isEmpty() )
            {
                currentPing = ( currentPing + 1 ) % this.players.size();
                EntityPlayer player = (EntityPlayer) this.players.get( currentPing );
                if (player.lastPing == -1 || pingToBar(player.lastPing) != pingToBar(player.ping))
                {
                    Packet packet = new PacketPlayOutPlayerInfo(player, PacketPlayOutPlayerInfo.PlayerInfo.UPDATE_LATENCY); // Spigot - protocol patch
                    for ( EntityPlayer splayer : (List<EntityPlayer>) this.players )
                    {
                        if ( splayer.getBukkitEntity().canSee( player.getBukkitEntity() ) )
                        {
                            splayer.playerConnection.sendPacket(packet);
                        }
                    }
                    player.lastPing = player.ping;
                }
            }
        } catch (Exception e) {
            // Better safe than sorry :)
        }
        // Spigot end
    }

    private int pingToBar(int ping) {
        if (ping < 0) return 5;
        else if (ping < 150) return 0;
        else if (ping < 300) return 1;
        else if (ping < 600) return 2;
        else if (ping < 1000) return 3;
        else return 4;
    }

    public void sendAll(Packet packet) {
        for (int i = 0; i < this.players.size(); ++i) {
            ((EntityPlayer) this.players.get(i)).playerConnection.sendPacket(packet);
        }
    }

    public void a(Packet packet, int i) {
        for (int j = 0; j < this.players.size(); ++j) {
            EntityPlayer entityplayer = (EntityPlayer) this.players.get(j);

            if (entityplayer.dimension == i) {
                entityplayer.playerConnection.sendPacket(packet);
            }
        }
    }

    public String b(boolean flag) {
        String s = "";
        ArrayList arraylist = Lists.newArrayList(this.players);

        for (int i = 0; i < arraylist.size(); ++i) {
            if (i > 0) {
                s = s + ", ";
            }

            s = s + ((EntityPlayer) arraylist.get(i)).getName();
            if (flag) {
                s = s + " (" + ((EntityPlayer) arraylist.get(i)).getUniqueID().toString() + ")";
            }
        }

        return s;
    }

    public String[] f() {
        String[] astring = new String[this.players.size()];

        for (int i = 0; i < this.players.size(); ++i) {
            astring[i] = ((EntityPlayer) this.players.get(i)).getName();
        }

        return astring;
    }

    public GameProfile[] g() {
        GameProfile[] agameprofile = new GameProfile[this.players.size()];

        for (int i = 0; i < this.players.size(); ++i) {
            agameprofile[i] = ((EntityPlayer) this.players.get(i)).getProfile();
        }

        return agameprofile;
    }

    public GameProfileBanList getProfileBans() {
        return this.j;
    }

    public IpBanList getIPBans() {
        return this.k;
    }

    public void addOp(GameProfile gameprofile) {
        this.operators.add(new OpListEntry(gameprofile, this.server.l()));

        // CraftBukkit start
        Player player = server.server.getPlayer(gameprofile.getId());
        if (player != null) {
            player.recalculatePermissions();
        }
        // CraftBukkit end
    }

    public void removeOp(GameProfile gameprofile) {
        this.operators.remove(gameprofile);

        // CraftBukkit start
        Player player = server.server.getPlayer(gameprofile.getId());
        if (player != null) {
            player.recalculatePermissions();
        }
        // CraftBukkit end
    }

    public boolean isWhitelisted(GameProfile gameprofile) {
        return !this.hasWhitelist || this.operators.d(gameprofile) || this.whitelist.d(gameprofile);
    }

    public boolean isOp(GameProfile gameprofile) {
        // CraftBukkit - fix reference to worldserver array
        return this.operators.d(gameprofile) || this.server.N() && this.server.worlds.get(0).getWorldData().allowCommands() && this.server.M().equalsIgnoreCase(gameprofile.getName()) || this.s;
    }

    public EntityPlayer getPlayer(String s) {
        if (true) { return playerMap.get(s); } // PaperSpigot
        Iterator iterator = this.players.iterator();

        EntityPlayer entityplayer;

        do {
            if (!iterator.hasNext()) {
                return null;
            }

            entityplayer = (EntityPlayer) iterator.next();
        } while (!entityplayer.getName().equalsIgnoreCase(s));

        return entityplayer;
    }

    public List a(ChunkCoordinates chunkcoordinates, int i, int j, int k, int l, int i1, int j1, Map map, String s, String s1, World world) {
        if (this.players.isEmpty()) {
            return Collections.emptyList();
        } else {
            Object object = new ArrayList();
            boolean flag = k < 0;
            boolean flag1 = s != null && s.startsWith("!");
            boolean flag2 = s1 != null && s1.startsWith("!");
            int k1 = i * i;
            int l1 = j * j;

            k = MathHelper.a(k);
            if (flag1) {
                s = s.substring(1);
            }

            if (flag2) {
                s1 = s1.substring(1);
            }

            for (int i2 = 0; i2 < this.players.size(); ++i2) {
                EntityPlayer entityplayer = (EntityPlayer) this.players.get(i2);

                if ((world == null || entityplayer.world == world) && (s == null || flag1 != s.equalsIgnoreCase(entityplayer.getName()))) {
                    if (s1 != null) {
                        ScoreboardTeamBase scoreboardteambase = entityplayer.getScoreboardTeam();
                        String s2 = scoreboardteambase == null ? "" : scoreboardteambase.getName();

                        if (flag2 == s1.equalsIgnoreCase(s2)) {
                            continue;
                        }
                    }

                    if (chunkcoordinates != null && (i > 0 || j > 0)) {
                        float f = chunkcoordinates.e(entityplayer.getChunkCoordinates());

                        if (i > 0 && f < (float) k1 || j > 0 && f > (float) l1) {
                            continue;
                        }
                    }

                    if (this.a((EntityHuman) entityplayer, map) && (l == EnumGamemode.NONE.getId() || l == entityplayer.playerInteractManager.getGameMode().getId()) && (i1 <= 0 || entityplayer.expLevel >= i1) && entityplayer.expLevel <= j1) {
                        ((List) object).add(entityplayer);
                    }
                }
            }

            if (chunkcoordinates != null) {
                Collections.sort((List) object, new PlayerDistanceComparator(chunkcoordinates));
            }

            if (flag) {
                Collections.reverse((List) object);
            }

            if (k > 0) {
                object = ((List) object).subList(0, Math.min(k, ((List) object).size()));
            }

            return (List) object;
        }
    }

    private boolean a(EntityHuman entityhuman, Map map) {
        if (map != null && map.size() != 0) {
            Iterator iterator = map.entrySet().iterator();

            Entry entry;
            boolean flag;
            int i;

            do {
                if (!iterator.hasNext()) {
                    return true;
                }

                entry = (Entry) iterator.next();
                String s = (String) entry.getKey();

                flag = false;
                if (s.endsWith("_min") && s.length() > 4) {
                    flag = true;
                    s = s.substring(0, s.length() - 4);
                }

                Scoreboard scoreboard = entityhuman.getScoreboard();
                ScoreboardObjective scoreboardobjective = scoreboard.getObjective(s);

                if (scoreboardobjective == null) {
                    return false;
                }

                ScoreboardScore scoreboardscore = entityhuman.getScoreboard().getPlayerScoreForObjective(entityhuman.getName(), scoreboardobjective);

                i = scoreboardscore.getScore();
                if (i < ((Integer) entry.getValue()).intValue() && flag) {
                    return false;
                }
            } while (i <= ((Integer) entry.getValue()).intValue() || flag);

            return false;
        } else {
            return true;
        }
    }

    public void sendPacketNearby(double d0, double d1, double d2, double d3, int i, Packet packet) {
        this.sendPacketNearby((EntityHuman) null, d0, d1, d2, d3, i, packet, false);
    }

    public void sendPacketNearby(final EntityHuman entityhuman, double d0, double d1, double d2, double d3, int i, Packet packet, boolean includingSelf) {    final boolean entityHumanNull = entityhuman == null;
        final Collection<EntityPlayer> playersList = (entityHumanNull ? this.players : entityhuman.world.players);
        for (EntityPlayer entityplayer : playersList) {

            // CraftBukkit start - Test if player receiving packet can see the source of the packet
            if (!entityHumanNull && entityhuman instanceof EntityPlayer && !entityplayer.getBukkitEntity().canSee(((EntityPlayer) entityhuman).getBukkitEntity())) continue;
            // CraftBukkit end

            if ((includingSelf || entityplayer != entityhuman) && entityplayer.dimension == i) {
                double d4 = d0 - entityplayer.locX;
                double d5 = d1 - entityplayer.locY;
                double d6 = d2 - entityplayer.locZ;

                if (d4 * d4 + d5 * d5 + d6 * d6 < d3 * d3) {
                    entityplayer.playerConnection.sendPacket(packet);
                }
            }
        }
    }

    public void savePlayers() {
        for (int i = 0; i < this.players.size(); ++i) {
            this.b((EntityPlayer) this.players.get(i));
        }
    }

    public void addWhitelist(GameProfile gameprofile) {
        this.whitelist.add(new WhiteListEntry(gameprofile));
    }

    public void removeWhitelist(GameProfile gameprofile) {
        this.whitelist.remove(gameprofile);
    }

    public WhiteList getWhitelist() {
        return this.whitelist;
    }

    public String[] getWhitelisted() {
        return this.whitelist.getEntries();
    }

    public OpList getOPs() {
        return this.operators;
    }

    public String[] n() {
        return this.operators.getEntries();
    }

    public void reloadWhitelist() {}

    public void b(EntityPlayer entityplayer, WorldServer worldserver) {
        entityplayer.playerConnection.sendPacket(new PacketPlayOutUpdateTime(worldserver.getTime(), worldserver.getDayTime(), worldserver.getGameRules().getBoolean("doDaylightCycle")));
        if (worldserver.Q()) {
            // CraftBukkit start - handle player weather
            // entityplayer.playerConnection.sendPacket(new PacketPlayOutGameStateChange(1, 0.0F));
            // entityplayer.playerConnection.sendPacket(new PacketPlayOutGameStateChange(7, worldserver.j(1.0F)));
            // entityplayer.playerConnection.sendPacket(new PacketPlayOutGameStateChange(8, worldserver.h(1.0F)));
            entityplayer.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL, false);
            // CraftBukkit end
        }
    }

    public void updateClient(EntityPlayer entityplayer) {
        entityplayer.updateInventory(entityplayer.defaultContainer);
        entityplayer.getBukkitEntity().updateScaledHealth(); // CraftBukkit - Update scaled health on respawn and worldchange
        entityplayer.playerConnection.sendPacket(new PacketPlayOutHeldItemSlot(entityplayer.inventory.itemInHandIndex));
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    public String[] getSeenPlayers() {
        // CraftBukkit - fix reference to worldserver array
        return this.server.worlds.get(0).getDataManager().getPlayerFileData().getSeenPlayers();
    }

    public boolean getHasWhitelist() {
        return this.hasWhitelist;
    }

    public void setHasWhitelist(boolean flag) {
        this.hasWhitelist = flag;
    }

    public List b(String s) {
        ArrayList arraylist = new ArrayList();
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            EntityPlayer entityplayer = (EntityPlayer) iterator.next();

            if (entityplayer.s().equals(s)) {
                arraylist.add(entityplayer);
            }
        }

        return arraylist;
    }

    public int s() {
        return this.q;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public NBTTagCompound t() {
        return null;
    }

    private void a(EntityPlayer entityplayer, EntityPlayer entityplayer1, World world) {
        if (entityplayer1 != null) {
            entityplayer.playerInteractManager.setGameMode(entityplayer1.playerInteractManager.getGameMode());
        } else if (this.r != null) {
            entityplayer.playerInteractManager.setGameMode(this.r);
        }

        entityplayer.playerInteractManager.b(world.getWorldData().getGameType());
    }

    public void u() {
        while (!this.players.isEmpty()) {
            // Spigot start
            EntityPlayer p = (EntityPlayer) this.players.get( 0 );
            p.playerConnection.disconnect( this.server.server.getShutdownMessage() );
            if ( ( !this.players.isEmpty() ) && ( this.players.get( 0 ) == p ) )
            {
                this.players.remove( 0 ); // Prevent shutdown hang if already disconnected
            }
            // Spigot end
        }
    }

    // CraftBukkit start - Support multi-line messages
    public void sendMessage(IChatBaseComponent[] ichatbasecomponent) {
        for (IChatBaseComponent component : ichatbasecomponent) {
            sendMessage(component, true);
        }
    }
    // CraftBukkit end

    public void sendMessage(IChatBaseComponent ichatbasecomponent, boolean flag) {
        this.server.sendMessage(ichatbasecomponent);
        this.sendAll(new PacketPlayOutChat(ichatbasecomponent, flag));
    }

    public void sendMessage(IChatBaseComponent ichatbasecomponent) {
        this.sendMessage(ichatbasecomponent, true);
    }

    public ServerStatisticManager a(EntityHuman entityhuman) {
        UUID uuid = entityhuman.getUniqueID();
        ServerStatisticManager serverstatisticmanager = uuid == null ? null : (ServerStatisticManager) this.n.get(uuid);

        if (serverstatisticmanager == null) {
            File file1 = new File(this.server.getWorldServer(0).getDataManager().getDirectory(), "stats");
            File file2 = new File(file1, uuid.toString() + ".json");

            if (!file2.exists()) {
                File file3 = new File(file1, entityhuman.getName() + ".json");

                if (file3.exists() && file3.isFile()) {
                    file3.renameTo(file2);
                }
            }

            serverstatisticmanager = new ServerStatisticManager(this.server, file2);
            serverstatisticmanager.a();
            this.n.put(uuid, serverstatisticmanager);
        }

        return serverstatisticmanager;
    }

    public void a(int i) {
        this.q = i;
        if (this.server.worldServer != null) {
            WorldServer[] aworldserver = this.server.worldServer;
            int j = aworldserver.length;

            for (int k = 0; k < j; ++k) {
                WorldServer worldserver = aworldserver[k];

                if (worldserver != null) {
                    worldserver.getPlayerChunkMap().a(i);
                }
            }
        }
    }

    public EntityPlayer getEntityPlayer(UUID uuid) {
        return this.uuidMap.get(uuid);
    }
}
