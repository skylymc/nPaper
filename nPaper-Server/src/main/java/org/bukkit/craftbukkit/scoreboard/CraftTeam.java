package org.bukkit.craftbukkit.scoreboard;

import java.util.Set;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scoreboard.Team;

import com.google.common.collect.ImmutableSet;

import net.minecraft.server.ScoreboardTeam;

final class CraftTeam extends CraftScoreboardComponent implements Team {
    private final ScoreboardTeam team;

    CraftTeam(CraftScoreboard scoreboard, ScoreboardTeam team) {
        super(scoreboard);
        this.team = team;
        scoreboard.teams.put(team.getName(), this);
    }

    public String getName() throws IllegalStateException {
        //CraftScoreboard scoreboard = checkState();

        return team.getName();
    }

    public String getDisplayName() throws IllegalStateException {
        //CraftScoreboard scoreboard = checkState();

        return team.getDisplayName();
    }

    public void setDisplayName(String displayName) throws IllegalStateException {
        Validate.notNull(displayName, "Display name cannot be null");
        Validate.isTrue(displayName.length() <= 32, "Display name '" + displayName + "' is longer than the limit of 32 characters");
        //CraftScoreboard scoreboard = checkState();

        team.setDisplayName(displayName);
    }

    public String getPrefix() throws IllegalStateException {
        //CraftScoreboard scoreboard = checkState();

        return team.getPrefix();
    }

    public void setPrefix(String prefix) throws IllegalStateException, IllegalArgumentException {
        Validate.notNull(prefix, "Prefix cannot be null");
        Validate.isTrue(prefix.length() <= 32, "Prefix '" + prefix + "' is longer than the limit of 32 characters");
        //CraftScoreboard scoreboard = checkState();

        team.setPrefix(prefix);
    }

    public String getSuffix() throws IllegalStateException {
        //CraftScoreboard scoreboard = checkState();

        return team.getSuffix();
    }

    public void setSuffix(String suffix) throws IllegalStateException, IllegalArgumentException {
        Validate.notNull(suffix, "Suffix cannot be null");
        Validate.isTrue(suffix.length() <= 32, "Suffix '" + suffix + "' is longer than the limit of 32 characters");
        //CraftScoreboard scoreboard = checkState();

        team.setSuffix(suffix);
    }

    public boolean allowFriendlyFire() throws IllegalStateException {
        //CraftScoreboard scoreboard = checkState();

        return team.allowFriendlyFire();
    }

    public void setAllowFriendlyFire(boolean enabled) throws IllegalStateException {
        //CraftScoreboard scoreboard = checkState();

        team.setAllowFriendlyFire(enabled);
    }

    public boolean canSeeFriendlyInvisibles() throws IllegalStateException {
        //CraftScoreboard scoreboard = checkState();

        return team.canSeeFriendlyInvisibles();
    }

    public void setCanSeeFriendlyInvisibles(boolean enabled) throws IllegalStateException {
        //CraftScoreboard scoreboard = checkState();

        team.setCanSeeFriendlyInvisibles(enabled);
    }

    public Set<OfflinePlayer> getPlayers() throws IllegalStateException {
        //CraftScoreboard scoreboard = checkState();

        ImmutableSet.Builder<OfflinePlayer> players = ImmutableSet.builder();
        for (Object o : team.getPlayerNameSet()) {
            players.add(Bukkit.getOfflinePlayer(o.toString()));
        }
        return players.build();
    }

    // Spigot start
    @Override
    public Set<String> getEntries() throws IllegalStateException {
        //CraftScoreboard scoreboard = checkState();
        
        ImmutableSet.Builder<String> entries = ImmutableSet.builder();
        for (Object o : team.getPlayerNameSet()){
            entries.add(o.toString());
        }
        return entries.build();
    }
    // Spigot end

    public int getSize() throws IllegalStateException {
        //CraftScoreboard scoreboard = checkState();

        return team.getPlayerNameSet().size();
    }

    public void addPlayer(OfflinePlayer player) throws IllegalStateException, IllegalArgumentException {
        Validate.notNull(player, "OfflinePlayer cannot be null");
        // Spigot Start
        addEntry(player.getName());
    }
    
    public void addEntry(String entry) throws IllegalStateException, IllegalArgumentException {
        Validate.notNull(entry, "Entry cannot be null");
        CraftScoreboard scoreboard = checkState();

        scoreboard.board.addPlayerToTeam(entry, team.getName());
        // Spigot end
    }

    public boolean removePlayer(OfflinePlayer player) throws IllegalStateException, IllegalArgumentException {
        Validate.notNull(player, "OfflinePlayer cannot be null");
        // Spigot start
        return removeEntry(player.getName());
    }
    
    public boolean removeEntry(String entry) throws IllegalStateException, IllegalArgumentException {
        Validate.notNull(entry, "Entry cannot be null");
        CraftScoreboard scoreboard = checkState();

        if (!team.getPlayerNameSet().contains(entry)) {
            return false;
        }

        scoreboard.board.removePlayerFromTeam(entry, team);
        // Spigot end
        return true;
    }

    public boolean hasPlayer(OfflinePlayer player) throws IllegalArgumentException, IllegalStateException {
        Validate.notNull(player, "OfflinePlayer cannot be null");
        // Spigot start
        return hasEntry(player.getName());
    }

    public boolean hasEntry(String entry) throws IllegalArgumentException, IllegalStateException {
        Validate.notNull("Entry cannot be null");

        //CraftScoreboard scoreboard = checkState();

        return team.getPlayerNameSet().contains(entry);
        // Spigot end
    }

    @Override
    public void unregister() throws IllegalStateException {
        CraftScoreboard scoreboard = checkState();

        scoreboard.board.removeTeam(team);
        scoreboard.teams.remove(team.getName());
        setUnregistered();
    }
}
