package com.talexck.gameVoting.ui;

import com.talexck.gameVoting.GameVoting;
import com.talexck.gameVoting.config.GameConfig;
import com.talexck.gameVoting.utils.ColorUtil;
import com.talexck.gameVoting.utils.gui.ChestUI;
import com.talexck.gameVoting.utils.gui.ClickableItem;
import com.talexck.gameVoting.utils.language.LanguageManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class SoloPlayerPickerUI extends ChestUI {
  private static final int[] CONTENT_SLOTS = {
    10, 11, 12, 13, 14, 15, 16,
    19, 20, 21, 22, 23, 24, 25,
    28, 29, 30, 31, 32, 33, 34,
    37, 38, 39, 40, 41, 42, 43
  };
  private final Player owner;
  private final List<Player> players;
  private final BiConsumer<Player, Player> select;
  private final Consumer<Player> back;
  private int page;

  public SoloPlayerPickerUI(
      Player owner,
      GameConfig game,
      BiConsumer<Player, Player> select,
      Consumer<Player> back) {
    super(
        ColorUtil.stripColors(
            LanguageManager.getInstance()
                .getMessage("solo.picker_title", Map.of("game", ColorUtil.stripColors(game.getName())))),
        6);
    this.owner = owner;
    this.select = select;
    this.back = back;
    this.players =
        Bukkit.getOnlinePlayers().stream()
            .filter(player -> !player.getUniqueId().equals(owner.getUniqueId()))
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    render();
  }

  private void render() {
    ItemStack border = item(Material.BLUE_STAINED_GLASS_PANE, " ", List.of());
    fillBorder(border);
    renderPlayers();
    renderNavigation();
  }

  private void renderPlayers() {
    for (int slot : CONTENT_SLOTS) {
      setItem(slot, ClickableItem.empty());
    }
    LanguageManager language = LanguageManager.getInstance();
    if (players.isEmpty()) {
      setItem(
          CONTENT_SLOTS[13],
          ClickableItem.of(
              item(
                  Material.BARRIER,
                  language.getMessage("solo.picker_empty_name"),
                  List.of(language.getMessage("solo.picker_empty_lore"))),
              ignored -> {}));
      return;
    }
    int pages = Math.max(1, (players.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
    page = Math.min(page, pages - 1);
    int start = page * CONTENT_SLOTS.length;
    int end = Math.min(players.size(), start + CONTENT_SLOTS.length);
    for (int index = start; index < end; index++) {
      Player target = players.get(index);
      setItem(
          CONTENT_SLOTS[index - start],
          ClickableItem.of(playerItem(target), ignored -> select.accept(owner, target)));
    }
  }

  private void renderNavigation() {
    LanguageManager language = LanguageManager.getInstance();
    int pages = Math.max(1, (players.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
    if (page > 0) {
      setItem(
          46,
          ClickableItem.of(
              item(Material.ARROW, language.getMessage("ui.prev_page"), List.of()),
              ignored -> {
                page--;
                renderPlayers();
                renderNavigation();
              }));
    } else {
      setItem(
          46,
          ClickableItem.of(
              item(Material.ARROW, language.getMessage("ui.back_button"), List.of()), back));
    }
    setItem(
        49,
        ClickableItem.of(
            item(Material.BARRIER, language.getMessage("ui.close_button"), List.of()),
            ignored ->
                Bukkit.getScheduler()
                    .runTask(GameVoting.getInstance(), () -> ignored.closeInventory())));
    if (page + 1 < pages) {
      setItem(
          52,
          ClickableItem.of(
              item(Material.ARROW, language.getMessage("ui.next_page"), List.of()),
              ignored -> {
                page++;
                renderPlayers();
                renderNavigation();
              }));
    } else {
      setItem(52, ClickableItem.empty());
    }
  }

  private ItemStack playerItem(Player player) {
    LanguageManager language = LanguageManager.getInstance();
    ItemStack item =
        item(
            Material.PLAYER_HEAD,
            language.getMessage("solo.picker_player_name", Map.of("player", player.getName())),
            List.of(language.getMessage("solo.picker_player_lore")));
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
      meta.lore(lore.stream().map(ColorUtil::colorize).toList());
      item.setItemMeta(meta);
    }
    return item;
  }
}
