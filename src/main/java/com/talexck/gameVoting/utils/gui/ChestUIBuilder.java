package com.talexck.gameVoting.utils.gui;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ChestUIBuilder {
    private String title = "Menu";
    private int rows = 3;
    private final Map<Integer, ClickableItem> items = new HashMap<>();
    private ItemStack borderItem = null;

    ChestUIBuilder() {
    }

    public ChestUIBuilder title(String title) {
        this.title = title;
        return this;
    }

    public ChestUIBuilder rows(int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("Rows must be between 1 and 6");
        }
        this.rows = rows;
        return this;
    }

    public ChestUIBuilder item(int slot, ClickableItem item) {
        items.put(slot, item);
        return this;
    }

    public ChestUIBuilder fillBorder(ItemStack item) {
        this.borderItem = item;
        return this;
    }

    public ChestUI build() {
        ChestUI menu = new ChestUI(title, rows);

        if (borderItem != null) {
            menu.fillBorder(borderItem);
        }

        items.forEach(menu::setItem);

        return menu;
    }
}
