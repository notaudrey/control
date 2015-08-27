package net.spacemc.control.commands;

import com.earth2me.essentials.User;
import net.spacemc.control.SpaceControl;
import net.spacemc.control.punishment.Punishments;
import net.spacemc.control.util.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sun.net.util.IPAddressUtil;

/**
 * @author audrey
 * @since 8/23/15.
 */
@Deprecated
public class CommandCMute extends CCommand {
    public CommandCMute(SpaceControl control) {
        super(control);
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        if(args.length >= 1) {
            String target = args[0];
            User essUser;
            int time = Integer.MAX_VALUE;
            boolean t = false;
            if(args.length > 1) {
                if(TimeUtil.isValidTime(args[1])) {
                    time = TimeUtil.parseTimeIntoMinutes(args[1]);
                    t = true;
                }
            }

            String reason = "§cCommand-mute§7";
            if(t) {
                if(args.length > 2) {
                    reason = "";
                    for(int i = 2; i < args.length; i++) {
                        reason += args[i] + " ";
                    }
                    reason = reason.trim();
                }
            } else {
                if(args.length > 1) {
                    reason = "";
                    for(int i = 1; i < args.length; i++) {
                        reason += args[i] + " ";
                    }
                    reason = reason.trim();
                }
            }
            if((essUser = getEssentials().getOfflineUser(target)) != null) {
                getControl().getActivePunishments()
                        .insertPunishment(Punishments.COMMAND_MUTE,
                                commandSender instanceof Player ? ((Player) commandSender).getUniqueId().toString() : "Console",
                                essUser.getConfigUUID().toString(), reason, time);
                getControl().getCmutes().add(essUser.getConfigUUID().toString());
                announce(commandSender.getName(), essUser.getName(), Punishments.COMMAND_MUTE, reason, "" + time);
                return true;
            } else {
                // Is it an IP?
                if(IPAddressUtil.isIPv4LiteralAddress(target)) {
                    getControl().getActivePunishments()
                            .insertPunishment(Punishments.COMMAND_MUTE,
                                    commandSender instanceof Player ? ((Player) commandSender).getUniqueId().toString() : "Console",
                                    target, reason, time);
                    getControl().getCmutes().add(target);
                    String hiddenIP = target.replaceFirst("\\.[0-9]{1,3}\\.[0-9]{1,3}$", ".XXX.XXX");
                    announce(commandSender.getName(), hiddenIP, Punishments.COMMAND_MUTE, reason, "" + time);
                } else {
                    commandSender.sendMessage("§7\"§a" + args[0] + "§7\" is not a valid target!");
                }
                return true;
            }
        } else {
            return false;
        }
    }
}
