package com.talexck.gameVoting.velocity.commands;

import com.talexck.gameVoting.velocity.config.BridgeConfig;
import com.talexck.gameVoting.velocity.utils.LegacyColorUtil;
import com.velocitypowered.api.command.SimpleCommand;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

public final class ProxyHelpCommand implements SimpleCommand {

    private final Supplier<BridgeConfig> configSupplier;

    public ProxyHelpCommand(Supplier<BridgeConfig> configSupplier) {
        this.configSupplier = configSupplier;
    }

    @Override
    public void execute(Invocation invocation) {
        BridgeConfig config = configSupplier.get();
        Set<String> lines = new LinkedHashSet<>(config.getDefaultHelpLines());
        config.getPermissionHelpSections().stream()
            .filter(section -> invocation.source().hasPermission(section.permission()))
            .forEach(section -> lines.addAll(section.lines()));

        invocation.source().sendMessage(LegacyColorUtil.colorize("&6=== GameVoting 帮助 ==="));
        if (lines.isEmpty()) {
            invocation.source().sendMessage(LegacyColorUtil.colorize("&e/game <game> &7- &f查看游戏介绍与规则"));
            invocation.source().sendMessage(LegacyColorUtil.colorize("&e/vote &7- &f打开投票菜单（或查看投票帮助）"));
            invocation.source().sendMessage(LegacyColorUtil.colorize("&e/hub &7- &f返回大厅服"));
            invocation.source().sendMessage(LegacyColorUtil.colorize("&e/lobby &7- &f返回大厅服"));
            return;
        }

        for (String line : lines) {
            invocation.source().sendMessage(LegacyColorUtil.colorize(line));
        }
    }
}
