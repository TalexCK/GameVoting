package com.talexck.gameVoting.party.commands;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.party.*;
import com.talexck.gameVoting.utils.message.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Command executor for /party commands.
 * Handles all party management operations including creation, invitations, and leadership.
 */
public class PartyCommand implements CommandExecutor {
    private final GameVoting plugin;

    public PartyCommand(GameVoting plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                           @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        PartyManager manager = PartyManager.getInstance();

        // No args - show help or party info
        if (args.length == 0) {
            return handleInfo(player, manager);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                return handleCreate(player, manager);
            case "invite":
                return handleInvite(player, args, manager);
            case "accept":
                return handleAccept(player, manager);
            case "decline":
            case "deny":
                return handleDecline(player, manager);
            case "exit":
            case "leave":
                return handleLeave(player, manager);
            case "list":
                return handleList(player, manager);
            case "transfer":
                return handleTransfer(player, args, manager);
            case "disband":
                return handleDisband(player, manager);
            case "vote":
                return handleVote(player, args, manager);
            case "forcestart":
                return handleForceStart(player, args, manager);
            default:
                MessageUtil.sendMessage(player, "&cUsage: /party <create|invite|accept|decline|exit|list|transfer|vote|forcestart>");
                return true;
        }
    }

    /**
     * Handle /party create - Create a new party.
     */
    private boolean handleCreate(Player player, PartyManager manager) {
        if (manager.hasParty(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou are already in a party!");
            return true;
        }

        Party party = manager.createParty(player.getUniqueId());
        MessageUtil.sendMessage(player, "&aParty created! You are the leader.");
        MessageUtil.sendMessage(player, "&7Use &e/party invite <player> &7to invite members.");
        return true;
    }

    /**
     * Handle /party invite <player> - Invite a player to the party.
     */
    private boolean handleInvite(Player player, String[] args, PartyManager manager) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&cUsage: /party invite <player>");
            return true;
        }

        Party party = manager.getPartyByPlayer(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendMessage(player, "&cYou are not in a party! Use &e/party create");
            return true;
        }

        if (!party.isLeader(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cOnly the party leader can invite players!");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(player, "&cPlayer not found!");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou cannot invite yourself!");
            return true;
        }

        if (manager.hasParty(target.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cThat player is already in a party!");
            return true;
        }

        if (!party.canInvite()) {
            MessageUtil.sendMessage(player, "&cYour party is full!");
            return true;
        }

        if (manager.sendInvite(player.getUniqueId(), target.getUniqueId())) {
            MessageUtil.sendMessage(player, "&aInvitation sent to &e" + target.getName());

            // Send invitation to target
            MessageUtil.sendMessage(target, "&e&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            MessageUtil.sendMessage(target, "&aYou've been invited to &e" + player.getName() + "'s &aparty!");
            MessageUtil.sendMessage(target, "&7Accept: &e/party accept");
            MessageUtil.sendMessage(target, "&7Decline: &e/party decline");
            MessageUtil.sendMessage(target, "&7This invitation expires in 30 seconds.");
            MessageUtil.sendMessage(target, "&e&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        } else {
            MessageUtil.sendMessage(player, "&cFailed to send invitation!");
        }

        return true;
    }

    /**
     * Handle /party accept - Accept a party invitation.
     */
    private boolean handleAccept(Player player, PartyManager manager) {
        if (manager.hasParty(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou are already in a party!");
            return true;
        }

        PartyInvite invite = manager.getPendingInvite(player.getUniqueId());
        if (invite == null) {
            MessageUtil.sendMessage(player, "&cYou don't have any pending invitations!");
            return true;
        }

        if (invite.isExpired()) {
            manager.declineInvite(player.getUniqueId());
            MessageUtil.sendMessage(player, "&cThat invitation has expired!");
            return true;
        }

        if (manager.acceptInvite(player.getUniqueId())) {
            Party party = manager.getPartyByPlayer(player.getUniqueId());
            Player leader = Bukkit.getPlayer(party.getLeaderId());

            MessageUtil.sendMessage(player, "&aYou joined the party!");

            // Notify all party members
            for (UUID memberId : party.getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && !member.getUniqueId().equals(player.getUniqueId())) {
                    MessageUtil.sendMessage(member, "&e" + player.getName() + " &ajoined the party!");
                }
            }
        } else {
            MessageUtil.sendMessage(player, "&cFailed to join party!");
        }

        return true;
    }

    /**
     * Handle /party decline - Decline a party invitation.
     */
    private boolean handleDecline(Player player, PartyManager manager) {
        PartyInvite invite = manager.getPendingInvite(player.getUniqueId());
        if (invite == null) {
            MessageUtil.sendMessage(player, "&cYou don't have any pending invitations!");
            return true;
        }

        manager.declineInvite(player.getUniqueId());
        MessageUtil.sendMessage(player, "&cInvitation declined.");

        Player inviter = Bukkit.getPlayer(invite.getInviterId());
        if (inviter != null) {
            MessageUtil.sendMessage(inviter, "&e" + player.getName() + " &cdeclined the party invitation.");
        }

        return true;
    }

    /**
     * Handle /party exit - Leave the current party.
     */
    private boolean handleLeave(Player player, PartyManager manager) {
        Party party = manager.getPartyByPlayer(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendMessage(player, "&cYou are not in a party!");
            return true;
        }

        boolean wasLeader = party.isLeader(player.getUniqueId());
        manager.leaveParty(player.getUniqueId());

        MessageUtil.sendMessage(player, "&cYou left the party.");

        // Notify remaining members
        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                MessageUtil.sendMessage(member, "&e" + player.getName() + " &cleft the party.");
                if (wasLeader && party.isLeader(memberId)) {
                    MessageUtil.sendMessage(member, "&aYou are now the party leader!");
                }
            }
        }

        return true;
    }

    /**
     * Handle /party list - Show party members.
     */
    private boolean handleList(Player player, PartyManager manager) {
        Party party = manager.getPartyByPlayer(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendMessage(player, "&cYou are not in a party!");
            return true;
        }

        MessageUtil.sendMessage(player, "&e&l▬▬▬▬▬▬▬▬ Party Members ▬▬▬▬▬▬▬▬");
        MessageUtil.sendMessage(player, "&7Total: &e" + party.getMembers().size() + "/" + party.getMaxMembers());
        MessageUtil.sendMessage(player, "");

        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                String prefix = party.isLeader(memberId) ? "&6⭐ " : "&7- ";
                MessageUtil.sendMessage(player, prefix + member.getName());
            }
        }

        MessageUtil.sendMessage(player, "&e&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        return true;
    }

    /**
     * Handle /party transfer <player> - Transfer leadership to another member.
     */
    private boolean handleTransfer(Player player, String[] args, PartyManager manager) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&cUsage: /party transfer <player>");
            return true;
        }

        Party party = manager.getPartyByPlayer(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendMessage(player, "&cYou are not in a party!");
            return true;
        }

        if (!party.isLeader(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cOnly the party leader can transfer leadership!");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(player, "&cPlayer not found!");
            return true;
        }

        if (!party.isMember(target.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cThat player is not in your party!");
            return true;
        }

        if (manager.transferLeadership(player.getUniqueId(), target.getUniqueId())) {
            // Notify all party members
            for (UUID memberId : party.getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    MessageUtil.sendMessage(member, "&e" + target.getName() + " &ais now the party leader!");
                }
            }
        } else {
            MessageUtil.sendMessage(player, "&cFailed to transfer leadership!");
        }

        return true;
    }

    /**
     * Handle /party disband - Disband the party (leader only).
     */
    private boolean handleDisband(Player player, PartyManager manager) {
        Party party = manager.getPartyByPlayer(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendMessage(player, "&cYou are not in a party!");
            return true;
        }

        if (!party.isLeader(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cOnly the party leader can disband the party!");
            return true;
        }

        // Notify all members
        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                MessageUtil.sendMessage(member, "&cThe party has been disbanded.");
            }
        }

        manager.disbandParty(party.getPartyId());
        return true;
    }

    /**
     * Handle /party vote - Start party voting (placeholder for future implementation).
     */
    private boolean handleVote(Player player, String[] args, PartyManager manager) {
        Party party = manager.getPartyByPlayer(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendMessage(player, "&cYou are not in a party!");
            return true;
        }

        if (!party.isLeader(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cOnly the party leader can start party voting!");
            return true;
        }

        // TODO: Implement party voting (Phase 2)
        MessageUtil.sendMessage(player, "&aParty voting will be implemented soon!");
        return true;
    }

    /**
     * Handle /party forcestart <game-id> - Force start a game (placeholder for future implementation).
     */
    private boolean handleForceStart(Player player, String[] args, PartyManager manager) {
        Party party = manager.getPartyByPlayer(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendMessage(player, "&cYou are not in a party!");
            return true;
        }

        if (!party.isLeader(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cOnly the party leader can force start games!");
            return true;
        }

        // TODO: Implement force start (Phase 3)
        MessageUtil.sendMessage(player, "&aForce start will be implemented soon!");
        return true;
    }

    /**
     * Handle /party (no args) - Show help or party info.
     */
    private boolean handleInfo(Player player, PartyManager manager) {
        Party party = manager.getPartyByPlayer(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendMessage(player, "&e&l▬▬▬▬▬▬▬ Party Commands ▬▬▬▬▬▬▬");
            MessageUtil.sendMessage(player, "&e/party create &7- Create a new party");
            MessageUtil.sendMessage(player, "&e/party accept &7- Accept party invitation");
            MessageUtil.sendMessage(player, "&e/party decline &7- Decline party invitation");
            MessageUtil.sendMessage(player, "&e&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        } else {
            return handleList(player, manager);
        }
        return true;
    }
}
