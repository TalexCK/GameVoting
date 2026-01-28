package com.talexck.gameVoting.commands;

import com.talexck.gameVoting.config.GamesConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VoteTabCompleter implements TabCompleter {
    private final GamesConfigManager gamesManager;

    public VoteTabCompleter(GamesConfigManager gamesManager) {
        this.gamesManager = gamesManager;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, 
                                                 @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - main subcommands
            List<String> subcommands = Arrays.asList("start", "stop", "forcestart", "ready", "gamestart", "holograms", "session", "reload", "join");
            
            // Filter based on permissions
            for (String sub : subcommands) {
                if (requiresAdmin(sub) && !sender.hasPermission("gamevoting.vote.admin")) {
                    continue;
                }
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            
            switch (subcommand) {
                case "start":
                    // Suggest time in minutes
                    completions.addAll(Arrays.asList("1", "3", "5", "10"));
                    break;
                    
                case "forcestart":
                    // Suggest game IDs
                    if (gamesManager != null) {
                        completions.addAll(gamesManager.getGames().stream()
                            .map(game -> game.getId())
                            .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList()));
                    }
                    break;
                    
                case "holograms":
                    // Hologram subcommands
                    List<String> hologramSubs = Arrays.asList("create", "remove", "list");
                    completions.addAll(hologramSubs.stream()
                        .filter(sub -> sub.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList()));
                    break;
                    
                case "session":
                    // Session subcommands
                    List<String> sessionSubs = Arrays.asList("list", "stop");
                    completions.addAll(sessionSubs.stream()
                        .filter(sub -> sub.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList()));
                    break;
            }
        } else if (args.length == 3) {
            String subcommand = args[0].toLowerCase();
            String subSubcommand = args[1].toLowerCase();
            
            if ("holograms".equals(subcommand) && "remove".equals(subSubcommand)) {
                // Suggest hologram IDs (numeric)
                completions.addAll(Arrays.asList("0", "1", "2", "3", "4"));
            } else if ("session".equals(subcommand) && "list".equals(subSubcommand)) {
                // Suggest page numbers
                completions.addAll(Arrays.asList("1", "2", "3"));
            }
        }

        return completions;
    }

    /**
     * Check if a subcommand requires admin permission.
     */
    private boolean requiresAdmin(String subcommand) {
        return Arrays.asList("start", "stop", "forcestart", "gamestart", "holograms", "session", "reload").contains(subcommand);
    }
}
