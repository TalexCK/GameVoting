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

import java.util.HashMap;
import java.util.Map;
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
            sender.sendMessage(com.talexck.gameVoting.utils.language.LanguageManager.getInstance().getMessage("command.only_players"));
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
                MessageUtil.sendTranslated(player, "party.usage");
                return true;
        }
    }

    /**
     * Handle /party create - Create a new party.
     */
    private boolean handleCreate(Player player, PartyManager manager) {
        if (manager.hasParty(player.getUniqueId())) {
            MessageUtil.sendTranslated(player, "party.already_in_party");
            return true;
        }

        Party party = manager.createParty(player.getUniqueId());
        MessageUtil.sendTranslated(player, "party.created");
        MessageUtil.sendTranslated(player, "party.invite_instructions");
        return true;
    }

    /**
     * Handle /party invite <player> - Invite a player to the party.
     */
    private boolean handleInvite(Player player, String[] args, PartyManager manager) {
        if (args.length < 2) {
            MessageUtil.sendTranslated(player, "party.invite_usage");
            return true;
        }

        Party party = manager.getPartyByPlayer(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendTranslated(player, "party.not_in_party");
            return true;
        }

        if (!party.isLeader(player.getUniqueId())) {
            MessageUtil.sendTranslated(player, "party.not_leader_invite");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendTranslated(player, "party.player_not_found");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            MessageUtil.sendTranslated(player, "party.cannot_invite_self");
            return true;
        }

        if (manager.hasParty(target.getUniqueId())) {
            MessageUtil.sendTranslated(player, "party.target_already_in_party");
            return true;
        }

        if (!party.canInvite()) {
            MessageUtil.sendTranslated(player, "party.party_full");
            return true;
        }

        if (manager.sendInvite(player.getUniqueId(), target.getUniqueId())) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", target.getName());
            MessageUtil.sendTranslated(player, "party.invite_sent", placeholders);

            // Send invitation to target
            placeholders.put("player", player.getName());
            MessageUtil.sendTranslated(target, "party.invite_header");
            MessageUtil.sendTranslated(target, "party.invite_message", placeholders);
            MessageUtil.sendTranslated(target, "party.invite_accept");
            MessageUtil.sendTranslated(target, "party.invite_decline");
            MessageUtil.sendTranslated(target, "party.invite_expires");
            MessageUtil.sendTranslated(target, "party.invite_footer");
        } else {
            MessageUtil.sendTranslated(player, "party.invite_failed");
        }

        return true;
    }

    /**
     * Handle /party accept - Accept a party invitation.
     */
    private boolean handleAccept(Player player, PartyManager manager) {
        if (manager.hasParty(player.getUniqueId())) {
            MessageUtil.sendTranslated(player, "party.already_in_party");
            return true;
        }

        PartyInvite invite = manager.getPendingInvite(player.getUniqueId());
        if (invite == null) {
            MessageUtil.sendTranslated(player, "party.no_pending_invite");
            return true;
        }

        if (invite.isExpired()) {
            manager.declineInvite(player.getUniqueId());
            MessageUtil.sendTranslated(player, "party.invite_expired");
            return true;
        }

        if (manager.acceptInvite(player.getUniqueId())) {
            Party party = manager.getPartyByPlayer(player.getUniqueId());
            Player leader = Bukkit.getPlayer(party.getLeaderId());

            MessageUtil.sendTranslated(player, "party.joined");

            // Notify all party members
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            for (UUID memberId : party.getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && !member.getUniqueId().equals(player.getUniqueId())) {
                    MessageUtil.sendTranslated(member, "party.player_joined", placeholders);
                }
            }
        } else {
            MessageUtil.sendTranslated(player, "party.join_failed");
        }

        return true;
    }

    /**
     * Handle /party decline - Decline a party invitation.
     */
    private boolean handleDecline(Player player, PartyManager manager) {
        PartyInvite invite = manager.getPendingInvite(player.getUniqueId());
        if (invite == null) {
            MessageUtil.sendTranslated(player, "party.no_pending_invite");
            return true;
        }

        manager.declineInvite(player.getUniqueId());
        MessageUtil.sendTranslated(player, "party.invite_declined");

        Player inviter = Bukkit.getPlayer(invite.getInviterId());
        if (inviter != null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            MessageUtil.sendTranslated(inviter, "party.invite_declined_notify", placeholders);
        }

        return true;
    }

    /**
     * Handle /party exit - Leave the current party.
     */
    private boolean handleLeave(Player player, PartyManager manager) {
        Party party = manager.getPartyByPlayer(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendTranslated(player, "party.not_in_party");
            return true;
        }

        boolean wasLeader = party.isLeader(player.getUniqueId());
        manager.leaveParty(player.getUniqueId());

        MessageUtil.sendTranslated(player, "party.left");

        // Notify remaining members
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                MessageUtil.sendTranslated(member, "party.player_left", placeholders);
                if (wasLeader && party.isLeader(memberId)) {
                    MessageUtil.sendTranslated(member, "party.now_leader");
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
            MessageUtil.sendTranslated(player, "party.not_in_party");
            return true;
        }

        MessageUtil.sendTranslated(player, "party.list_header");
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("count", String.valueOf(party.getMembers().size()));
        placeholders.put("max", String.valueOf(party.getMaxMembers()));
        MessageUtil.sendTranslated(player, "party.list_total", placeholders);
        MessageUtil.sendMessage(player, "");

        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                String prefix = party.isLeader(memberId) ? "&6⭐ " : "&7- ";
                MessageUtil.sendMessage(player, prefix + member.getName());
            }
        }

        MessageUtil.sendTranslated(player, "party.invite_footer");
        return true;
    }

    /**
     * Handle /party transfer <player> - Transfer leadership to another member.
     */
    private boolean handleTransfer(Player player, String[] args, PartyManager manager) {
        if (args.length < 2) {
            MessageUtil.sendTranslated(player, "party.transfer_usage");
            return true;
        }

        Party party = manager.getPartyByPlayer(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendTranslated(player, "party.not_in_party");
            return true;
        }

        if (!party.isLeader(player.getUniqueId())) {
            MessageUtil.sendTranslated(player, "party.not_leader_transfer");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendTranslated(player, "party.player_not_found");
            return true;
        }

        if (!party.isMember(target.getUniqueId())) {
            MessageUtil.sendTranslated(player, "party.target_not_in_party");
            return true;
        }

        if (manager.transferLeadership(player.getUniqueId(), target.getUniqueId())) {
            // Notify all party members
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", target.getName());
            for (UUID memberId : party.getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    MessageUtil.sendTranslated(member, "party.leadership_transferred", placeholders);
                }
            }
        } else {
            MessageUtil.sendTranslated(player, "party.transfer_failed");
        }

        return true;
    }

    /**
     * Handle /party disband - Disband the party (leader only).
     */
    private boolean handleDisband(Player player, PartyManager manager) {
        Party party = manager.getPartyByPlayer(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendTranslated(player, "party.not_in_party");
            return true;
        }

        if (!party.isLeader(player.getUniqueId())) {
            MessageUtil.sendTranslated(player, "party.not_leader_disband");
            return true;
        }

        // Notify all members
        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                MessageUtil.sendTranslated(member, "party.disbanded");
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
            MessageUtil.sendTranslated(player, "party.not_in_party");
            return true;
        }

        if (!party.isLeader(player.getUniqueId())) {
            MessageUtil.sendTranslated(player, "party.not_leader_vote");
            return true;
        }

        // TODO: Implement party voting (Phase 2)
        MessageUtil.sendTranslated(player, "party.vote_coming_soon");
        return true;
    }

    /**
     * Handle /party forcestart <game-id> - Force start a game (placeholder for future implementation).
     */
    private boolean handleForceStart(Player player, String[] args, PartyManager manager) {
        Party party = manager.getPartyByPlayer(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendTranslated(player, "party.not_in_party");
            return true;
        }

        if (!party.isLeader(player.getUniqueId())) {
            MessageUtil.sendTranslated(player, "party.not_leader_forcestart");
            return true;
        }

        // TODO: Implement force start (Phase 3)
        MessageUtil.sendTranslated(player, "party.forcestart_coming_soon");
        return true;
    }

    /**
     * Handle /party (no args) - Show help or party info.
     */
    private boolean handleInfo(Player player, PartyManager manager) {
        Party party = manager.getPartyByPlayer(player.getUniqueId());
        if (party == null) {
            MessageUtil.sendTranslated(player, "party.help_header");
            MessageUtil.sendTranslated(player, "party.help_create");
            MessageUtil.sendTranslated(player, "party.help_accept");
            MessageUtil.sendTranslated(player, "party.help_decline");
            MessageUtil.sendTranslated(player, "party.help_footer");
        } else {
            return handleList(player, manager);
        }
        return true;
    }
}
