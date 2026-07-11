package com.talexck.gameVoting.ui;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.config.GameConfig;
import com.talexck.gameVoting.config.GamesConfigManager;
import com.talexck.gameVoting.utils.ColorUtil;
import com.talexck.gameVoting.utils.gui.ChestUI;
import com.talexck.gameVoting.utils.gui.ClickableItem;
import com.talexck.gameVoting.utils.language.LanguageManager;
import com.talexck.gameVoting.utils.version.ClientVersionUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class SoloUI extends ChestUI {
  private static final int[] CONTENT_SLOTS = {
    10, 11, 12, 13, 14, 15, 16,
    19, 20, 21, 22, 23, 24, 25,
    28, 29, 30, 31, 32, 33, 34,
    37, 38, 39, 40, 41, 42, 43
  };
  private static final int PREVIOUS_SLOT = 46;
  private static final int CLOSE_SLOT = 49;
  private static final int NEXT_SLOT = 52;
  private final Player player;
  private final List<GameConfig> games;
  private final BiConsumer<Player, GameConfig> launch;
  private int page;

  public SoloUI(
      Player player,
      GamesConfigManager gamesManager,
      BiConsumer<Player, GameConfig> launch) {
    super(
        ColorUtil.stripColors(LanguageManager.getInstance().getMessage("solo.ui_title")),
        6);
    this.player = player;
    this.games = gamesManager.getSoloGames();
    this.launch = launch;
    render();
  }

  private void render() {
    ItemStack border = item(Material.CYAN_STAINED_GLASS_PANE, " ", List.of());
    for (int slot = 0; slot < 9; slot++) {
      setItem(slot, ClickableItem.of(border, ignored -> {}));
    }
    for (int slot = 45; slot < 54; slot++) {
      if (slot != PREVIOUS_SLOT && slot != CLOSE_SLOT && slot != NEXT_SLOT) {
        setItem(slot, ClickableItem.of(border, ignored -> {}));
      }
    }
    for (int row = 1; row < 5; row++) {
      setItem(row * 9, ClickableItem.of(border, ignored -> {}));
      setItem(row * 9 + 8, ClickableItem.of(border, ignored -> {}));
    }
    renderGames();
    renderNavigation();
  }

  private void renderGames() {
    for (int slot : CONTENT_SLOTS) {
      setItem(slot, ClickableItem.empty());
    }
    if (games.isEmpty()) {
      setItem(
          CONTENT_SLOTS[13],
          ClickableItem.of(
              item(
                  Material.BARRIER,
                  LanguageManager.getInstance().getMessage("solo.no_games"),
                  List.of()),
              ignored -> {}));
      return;
    }
    int pages = Math.max(1, (games.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
    page = Math.min(page, pages - 1);
    int start = page * CONTENT_SLOTS.length;
    int end = Math.min(games.size(), start + CONTENT_SLOTS.length);
    for (int index = start; index < end; index++) {
      GameConfig game = games.get(index);
      setItem(
          CONTENT_SLOTS[index - start],
          ClickableItem.of(createGameItem(game), ignored -> launch.accept(player, game)));
    }
  }

  private ItemStack createGameItem(GameConfig game) {
    LanguageManager language = LanguageManager.getInstance();
    List<String> lore = new ArrayList<>(game.getDescription());
    lore.add("");
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("min", String.valueOf(game.getMinPlayers()));
    placeholders.put("max", String.valueOf(Math.min(game.getMaxPlayers(), game.getSoloMaxPlayers())));
    placeholders.put("mode", language.getMessage("solo.mode_" + game.getSoloMode()));
    placeholders.put("startup", language.getMessage("solo.startup_" + game.getSoloStartup()));
    placeholders.put("days", String.valueOf(game.getSoloRetentionDays()));
    lore.add(language.getMessage("solo.ui_mode", placeholders));
    lore.add(language.getMessage("solo.ui_players", placeholders));
    lore.add(language.getMessage("solo.ui_startup", placeholders));
    String version =
        ClientVersionUtil.formatVersionRange(
            game.getVersion(), game.getMinVersion(), game.getMaxVersion());
    if (version == null
        || version.isBlank()
        || version.equalsIgnoreCase("any")
        || version.equals("*")) {
      lore.add(language.getMessage("ui.version_any"));
    } else {
      placeholders.put("version", version);
      lore.add(language.getMessage("ui.version_label", placeholders));
    }
    if (game.getSoloMode().equals("player_world")) {
      lore.add(language.getMessage("solo.ui_retention", placeholders));
    }
    lore.add("");
    lore.add(
        language.getMessage(
            game.getSoloMode().equals("shared")
                ? "solo.ui_click_shared"
                : "solo.ui_click_player_world"));
    ItemStack item = item(game.getMaterial(), game.getName(), lore);
    if (game.getCustomModelData() > 0) {
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
        meta.setCustomModelData(game.getCustomModelData());
        item.setItemMeta(meta);
      }
    }
    return item;
  }

  private void renderNavigation() {
    LanguageManager language = LanguageManager.getInstance();
    int pages = Math.max(1, (games.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
    if (page > 0) {
      setItem(
          PREVIOUS_SLOT,
          ClickableItem.of(
              item(Material.ARROW, language.getMessage("ui.prev_page"), List.of()),
              ignored -> {
                page--;
                renderGames();
                renderNavigation();
              }));
    } else {
      setItem(PREVIOUS_SLOT, ClickableItem.empty());
    }
    setItem(
        CLOSE_SLOT,
        ClickableItem.of(
            item(Material.BARRIER, language.getMessage("ui.close_button"), List.of()),
            ignored ->
                Bukkit.getScheduler()
                    .runTask(GameVoting.getInstance(), () -> ignored.closeInventory())));
    if (page + 1 < pages) {
      setItem(
          NEXT_SLOT,
          ClickableItem.of(
              item(Material.ARROW, language.getMessage("ui.next_page"), List.of()),
              ignored -> {
                page++;
                renderGames();
                renderNavigation();
              }));
    } else {
      setItem(NEXT_SLOT, ClickableItem.empty());
    }
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
