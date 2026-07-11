package com.talexck.gameVoting.ui;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.commands.SoloCreationManager;
import com.talexck.gameVoting.config.GameConfig;
import com.talexck.gameVoting.utils.ColorUtil;
import com.talexck.gameVoting.utils.gui.ChestUI;
import com.talexck.gameVoting.utils.gui.ClickableItem;
import com.talexck.gameVoting.utils.language.LanguageManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class SoloCreateUI extends ChestUI {
  private final String gameId;

  public SoloCreateUI(
      Player owner,
      GameConfig game,
      SoloCreationManager.DraftView draft,
      Consumer<Player> openPicker,
      Consumer<Player> create,
      Consumer<Player> clearSelection,
      Consumer<Player> back) {
    super(
        ColorUtil.stripColors(
            LanguageManager.getInstance()
                .getMessage("solo.create_title", Map.of("game", ColorUtil.stripColors(game.getName())))),
        6);
    this.gameId = game.getId();
    render(owner, game, draft, openPicker, create, clearSelection, back);
  }

  public String gameId() {
    return gameId;
  }

  private void render(
      Player owner,
      GameConfig game,
      SoloCreationManager.DraftView draft,
      Consumer<Player> openPicker,
      Consumer<Player> create,
      Consumer<Player> clearSelection,
      Consumer<Player> back) {
    LanguageManager language = LanguageManager.getInstance();
    ItemStack border = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", List.of());
    fillBorder(border);
    setItem(
        13,
        ClickableItem.of(
            item(game.getMaterial(), game.getName(), game.getDescription()), ignored -> {}));
    setItem(
        20,
        ClickableItem.of(
            head(
                owner,
                language.getMessage("solo.create_owner_name"),
                List.of(
                    language.getMessage(
                        "solo.create_owner_lore", Map.of("player", owner.getName())))),
            ignored -> {}));
    setItem(
        22,
        ClickableItem.of(
            item(Material.CHAIN, language.getMessage("solo.create_roster_link"), List.of()),
            ignored -> {}));
    setItem(24, teammateItem(draft, openPicker, clearSelection));
    boolean pending = draft.pendingPlayer() != null;
    if (pending) {
      setItem(
          31,
          ClickableItem.of(
              item(
                  Material.BARRIER,
                  language.getMessage("solo.create_waiting_name"),
                  List.of(language.getMessage("solo.create_waiting_lore"))),
              ignored -> {}));
    } else {
      int count = draft.acceptedPlayer() == null ? 1 : 2;
      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("count", String.valueOf(count));
      setItem(
          31,
          ClickableItem.of(
              item(
                  Material.EMERALD_BLOCK,
                  language.getMessage("solo.create_button_name"),
                  List.of(language.getMessage("solo.create_button_lore", placeholders))),
              create));
    }
    setItem(
        48,
        ClickableItem.of(
            item(Material.ARROW, language.getMessage("ui.back_button"), List.of()), back));
    setItem(
        50,
        ClickableItem.of(
            item(Material.BARRIER, language.getMessage("ui.close_button"), List.of()),
            ignored ->
                Bukkit.getScheduler()
                    .runTask(GameVoting.getInstance(), () -> ignored.closeInventory())));
  }

  private ClickableItem teammateItem(
      SoloCreationManager.DraftView draft,
      Consumer<Player> openPicker,
      Consumer<Player> clearSelection) {
    LanguageManager language = LanguageManager.getInstance();
    UUID accepted = draft.acceptedPlayer();
    if (accepted != null) {
      OfflinePlayer teammate = Bukkit.getOfflinePlayer(accepted);
      String name = teammate.getName() == null ? accepted.toString() : teammate.getName();
      Map<String, String> placeholders = Map.of("player", name);
      boolean online = Bukkit.getPlayer(accepted) != null;
      return ClickableItem.of(
          head(
              teammate,
              language.getMessage("solo.create_teammate_accepted_name"),
              List.of(
                  language.getMessage("solo.create_teammate_accepted_lore", placeholders),
                  language.getMessage(
                      online
                          ? "solo.create_teammate_online_lore"
                          : "solo.create_teammate_offline_lore"),
                  language.getMessage("solo.create_teammate_remove_lore"))),
          clearSelection);
    }
    UUID pending = draft.pendingPlayer();
    if (pending != null) {
      OfflinePlayer teammate = Bukkit.getOfflinePlayer(pending);
      String name = teammate.getName() == null ? pending.toString() : teammate.getName();
      Map<String, String> placeholders = Map.of("player", name);
      return ClickableItem.of(
          item(
              Material.CLOCK,
              language.getMessage("solo.create_teammate_pending_name"),
              List.of(
                  language.getMessage("solo.create_teammate_pending_lore", placeholders),
                  language.getMessage("solo.create_teammate_cancel_lore"))),
          clearSelection);
    }
    return ClickableItem.of(
        item(
            Material.PLAYER_HEAD,
            language.getMessage("solo.create_teammate_empty_name"),
            List.of(language.getMessage("solo.create_teammate_empty_lore"))),
        openPicker);
  }

  private static ItemStack head(OfflinePlayer player, String name, List<String> lore) {
    ItemStack item = item(Material.PLAYER_HEAD, name, lore);
    ItemMeta meta = item.getItemMeta();
    if (meta instanceof SkullMeta skull) {
      skull.setOwningPlayer(player);
      item.setItemMeta(skull);
    }
    return item;
  }

  private static ItemStack item(Material material, String name, List<String> lore) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(ColorUtil.colorize(name));
      List<Component> components = lore.stream().map(ColorUtil::colorize).toList();
      meta.lore(components);
      item.setItemMeta(meta);
    }
    return item;
  }
}
