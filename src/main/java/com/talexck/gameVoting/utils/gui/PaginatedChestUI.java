package com.talexck.gameVoting.utils.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PaginatedChestUI extends ChestUI {
    private final List<ClickableItem> contentItems;
    private final int itemsPerPage;
    private int currentPage;
    private final ItemStack prevButton;
    private final ItemStack nextButton;

    // Navigation button slots (last row)
    private static final int PREV_SLOT = 45; // Left side
    private static final int PAGE_INFO_SLOT = 49; // Center
    private static final int NEXT_SLOT = 53; // Right side

    protected PaginatedChestUI(String title, int rows, ItemStack prevButton, ItemStack nextButton, ItemStack borderItem) {
        super(title, rows);
        this.contentItems = new ArrayList<>();
        this.currentPage = 0;
        this.prevButton = prevButton;
        this.nextButton = nextButton;

        // Calculate items per page (exclude last row for navigation)
        this.itemsPerPage = (rows - 1) * 9;

        // Add border if specified
        if (borderItem != null) {
            fillBorder(borderItem);
        }
    }

    public void addItem(ClickableItem item) {
        contentItems.add(item);
    }

    public void nextPage(Player player) {
        if (currentPage < getTotalPages() - 1) {
            currentPage++;
            refresh(player);
        }
    }

    public void prevPage(Player player) {
        if (currentPage > 0) {
            currentPage--;
            refresh(player);
        }
    }

    public int getTotalPages() {
        return (int) Math.ceil((double) contentItems.size() / itemsPerPage);
    }

    private void refresh(Player player) {
        // Clear content area (not the border or navigation)
        for (int i = 9; i < getRows() * 9 - 9; i++) {
            if (!isNavigationSlot(i)) {
                getInventory().setItem(i, new ItemStack(Material.AIR));
            }
        }

        // Display items for current page
        int start = currentPage * itemsPerPage;
        int end = Math.min(start + itemsPerPage, contentItems.size());

        for (int i = start; i < end; i++) {
            int slot = (i - start) + 9; // Start after first row (border)
            ClickableItem item = contentItems.get(i);
            setItem(slot, item);
        }

        // Update navigation buttons
        updateNavigationButtons();

        player.updateInventory();
    }

    private void updateNavigationButtons() {
        // Previous page button
        if (currentPage > 0) {
            setItem(PREV_SLOT, ClickableItem.of(prevButton, player -> prevPage(player)));
        } else {
            ItemStack disabled = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            setItem(PREV_SLOT, ClickableItem.of(disabled, player -> {}));
        }

        // Page info
        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemMeta meta = pageInfo.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Page " + (currentPage + 1) + " / " + getTotalPages());
            pageInfo.setItemMeta(meta);
        }
        setItem(PAGE_INFO_SLOT, ClickableItem.of(pageInfo, player -> {}));

        // Next page button
        if (currentPage < getTotalPages() - 1) {
            setItem(NEXT_SLOT, ClickableItem.of(nextButton, player -> nextPage(player)));
        } else {
            ItemStack disabled = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            setItem(NEXT_SLOT, ClickableItem.of(disabled, player -> {}));
        }
    }

    private boolean isNavigationSlot(int slot) {
        int lastRow = (getRows() - 1) * 9;
        return slot >= lastRow && slot < getRows() * 9;
    }

    @Override
    public void open(Player player) {
        refresh(player);
        super.open(player);
    }

    public static Builder paginatedBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String title = "Menu";
        private int rows = 6;
        private ItemStack prevButton = createDefaultButton(Material.ARROW, "Previous Page");
        private ItemStack nextButton = createDefaultButton(Material.ARROW, "Next Page");
        private ItemStack borderItem = null;
        private final List<ClickableItem> items = new ArrayList<>();

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder rows(int rows) {
            if (rows < 2 || rows > 6) {
                throw new IllegalArgumentException("Paginated menu rows must be between 2 and 6");
            }
            this.rows = rows;
            return this;
        }

        public Builder items(List<ClickableItem> items) {
            this.items.addAll(items);
            return this;
        }

        public Builder addItem(ClickableItem item) {
            this.items.add(item);
            return this;
        }

        public Builder prevButton(Material material, String name) {
            this.prevButton = createDefaultButton(material, name);
            return this;
        }

        public Builder nextButton(Material material, String name) {
            this.nextButton = createDefaultButton(material, name);
            return this;
        }

        public Builder borderItem(Material material) {
            this.borderItem = new ItemStack(material);
            return this;
        }

        public PaginatedChestUI build() {
            PaginatedChestUI menu = new PaginatedChestUI(title, rows, prevButton, nextButton, borderItem);
            items.forEach(menu::addItem);
            return menu;
        }

        private static ItemStack createDefaultButton(Material material, String name) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                item.setItemMeta(meta);
            }
            return item;
        }
    }
}
