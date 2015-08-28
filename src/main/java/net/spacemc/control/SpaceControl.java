package net.spacemc.control;

import lombok.Getter;
import net.spacemc.control.commands.*;
import net.spacemc.control.db.Database;
import net.spacemc.control.db.SQLiteDB;
import net.spacemc.control.punishment.Punishment;
import net.spacemc.control.punishment.Punishments;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author audrey
 * @since 8/23/15.
 */
public class SpaceControl extends JavaPlugin {
    @Getter
    private final Database activePunishments = new SQLiteDB(this, "active_punishments");

    @Getter
    private final Database inactivePunishments = new SQLiteDB(this, "inactive_punishments");

    @Getter
    private List<String> mutes = new CopyOnWriteArrayList<>();

    @Getter
    private List<String> cmutes = new CopyOnWriteArrayList<>();

    @Getter
    private List<String> bans = new CopyOnWriteArrayList<>();

    @Getter
    private List<String> ipBans = new CopyOnWriteArrayList<>();

    @Getter
    private final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public void onEnable() {
        if(!this.getDataFolder().exists()) {
            getLogger().info("Data folder doesn't exist, making...");
            if(this.getDataFolder().mkdir()) {
                getLogger().info("Data folder made!");
            }
        }
        getLogger().info("Saving default config...");
        saveDefaultConfig();
        // Connect to dbs
        if(activePunishments.connect() && inactivePunishments.connect()) {
            getLogger().info("Connected to the databases!");
            if(activePunishments.initialize() && inactivePunishments.initialize()) {
                getLogger().info("Initialised databases!");
                // Load active mutes/cmutes/bans
                List<Punishment> all = activePunishments.getAllPunishments();
                all.stream().forEach(p -> {
                    System.out.println(p);
                    switch(p.getType()) {
                        case Punishments.BAN:
                            bans.add(p.getTarget());
                            break;
                        case Punishments.COMMAND_MUTE:
                            cmutes.add(p.getTarget());
                            break;
                        case Punishments.MUTE:
                            mutes.add(p.getTarget());
                            break;
                        case Punishments.IP_BAN:
                            ipBans.add(p.getTarget());
                            break;
                        default:
                            getLogger().warning("I don't know what \"" + p.getType() + "\" warning type is?");
                            break;
                    }
                });
            } else {
                getLogger().warning("Unable to initialise databases!");
            }
        } else {
            Bukkit.getPluginManager().disablePlugin(this);
            throw new IllegalStateException("Unable to connect to the databases!");
        }
        // Schedule the task to remove expired punishments and transfer them to the inactive db
        // Also automate adding active punishments to the active lists
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            List<Punishment> expired = activePunishments.getExpiredPunishments();
            expired.stream().forEach(p -> {
                getLogger().info("Removing inactive punishment: " + p.toString());
                activePunishments.removePunishment(p);
                switch(p.getType()) {
                    case Punishments.BAN:
                        bans.remove(p.getTarget());
                        break;
                    case Punishments.COMMAND_MUTE:
                        cmutes.remove(p.getTarget());
                        break;
                    case Punishments.MUTE:
                        mutes.remove(p.getTarget());
                        break;
                    case Punishments.IP_BAN:
                        ipBans.remove(p.getTarget());
                        break;
                    default:
                        break;
                }
                inactivePunishments.insertPunishment(p);
            });
        }, 0L, 20L);

        // Register event listener to handle mutes/bans
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @SuppressWarnings("unused")
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            public void onPlayerChatEvent(AsyncPlayerChatEvent e) {
                String uuid = e.getPlayer().getUniqueId().toString();
                String ip = e.getPlayer().getAddress().getAddress().toString();
                if(mutes.contains(uuid) || mutes.contains(ip)) {
                    if(!e.getMessage().startsWith("/")) {
                        e.setCancelled(true);
                    }
                }
            }

            @SuppressWarnings("unused")
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent e) {
                bans.stream().forEach(b -> System.out.println(b + ":" + e.getUniqueId()));
                ipBans.stream().forEach(b -> System.out.println(b + ":" + e.getAddress().toString()));
                if(bans.contains(e.getUniqueId().toString())) {
                    List<Punishment> p = activePunishments.getPunishments(e.getUniqueId().toString());
                    e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, "§4Banned§r: " + p.get(0).getReason() + "\n\nExpires: " + p.get(0).getEnd());
                }
                if(ipBans.contains(e.getAddress().toString())) {
                    System.out.println(e.getAddress().toString());
                    List<Punishment> p = activePunishments.getPunishments(e.getAddress().toString());
                    e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, "§4Banned§r: " + p.get(0).getReason() + "\n\nExpires: " + p.get(0).getEnd());
                }
            }

            @SuppressWarnings("unused")
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
                String uuid = e.getPlayer().getUniqueId().toString();
                String ip = e.getPlayer().getAddress().getAddress().toString();
                if(cmutes.contains(uuid) || cmutes.contains(ip)) {
                    e.setCancelled(true);
                }
            }
        }, this);
        // Set up commands

        // Utility commands
        getCommand("audit").setExecutor(new CommandAudit(this));
        getCommand("history").setExecutor(new CommandHistory(this));
        // Punishment commands
        getCommand("ban").setExecutor(new GenericPunishmentCommand(this, Punishments.BAN));
        getCommand("banip").setExecutor(new GenericPunishmentCommand(this, Punishments.IP_BAN));
        getCommand("cmute").setExecutor(new GenericPunishmentCommand(this, Punishments.COMMAND_MUTE));
        getCommand("mute").setExecutor(new GenericPunishmentCommand(this, Punishments.MUTE));
        // Undo commands
        getCommand("unban").setExecutor(new GenericPunishmentCommand(this, Punishments.BAN, true));
        getCommand("unbanip").setExecutor(new GenericPunishmentCommand(this, Punishments.IP_BAN, true));
        getCommand("uncmute").setExecutor(new GenericPunishmentCommand(this, Punishments.COMMAND_MUTE, true));
        getCommand("unmute").setExecutor(new GenericPunishmentCommand(this, Punishments.MUTE, true));
    }

    public void onDisable() {
        if(activePunishments.disconnect() && inactivePunishments.disconnect()) {
            getLogger().info("Successfully disconnected from the databases!");
        } else {
            throw new IllegalStateException("Unable to disconnect from the databases!");
        }
    }
}
