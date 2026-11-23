package com.lston.shopsys;

import de.imdacro.economySystem.EconomySystem;
import de.imdacro.economySystem.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ShopSysPlugin extends org.bukkit.plugin.java.JavaPlugin implements Listener {
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int BALANCE_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final long DEFAULT_KIT_COOLDOWN_HOURS = 24;
    private static final List<Kit> KITS = List.of(
        new Kit("wooden", "Wooden", "WOODEN_SWORD"),
        new Kit("stone", "Stone", "STONE_SWORD"),
        new Kit("copper", "Copper", "COPPER_SWORD"),
        new Kit("iron", "Iron", "IRON_SWORD"),
        new Kit("golden", "Golden", "GOLDEN_SWORD"),
        new Kit("diamond", "Diamond", "DIAMOND_SWORD"),
        new Kit("netherite", "Netherite", "NETHERITE_SWORD")
    );
    private static final List<Category> CATEGORIES = List.of(
        new Category("blocks", "Blocks", Material.BRICKS, List.of(
            item(Material.STONE, 64, 16), item(Material.COBBLESTONE, 64, 12), item(Material.DIRT, 64, 10),
            item(Material.GRASS_BLOCK, 32, 24), item(Material.OAK_PLANKS, 64, 20), item(Material.SPRUCE_PLANKS, 64, 20),
            item(Material.BIRCH_PLANKS, 64, 20), item(Material.JUNGLE_PLANKS, 64, 20), item(Material.ACACIA_PLANKS, 64, 20),
            item(Material.DARK_OAK_PLANKS, 64, 20), item(Material.MANGROVE_PLANKS, 64, 20), item(Material.CHERRY_PLANKS, 64, 20),
            item(Material.GLASS, 32, 30), item(Material.SAND, 64, 18), item(Material.GRAVEL, 64, 18),
            item(Material.BRICKS, 32, 40), item(Material.OBSIDIAN, 16, 120), item(Material.TNT, 8, 100),
            item(Material.STONE_BRICKS, 32, 36), item(Material.CLAY, 32, 28)
        )),
        new Category("ores", "Ores & Resources", Material.IRON_INGOT, List.of(
            item(Material.COAL, 32, 24), item(Material.RAW_COPPER, 16, 24), item(Material.COPPER_INGOT, 16, 36),
            item(Material.RAW_IRON, 16, 48), item(Material.IRON_INGOT, 16, 64), item(Material.RAW_GOLD, 16, 72),
            item(Material.GOLD_INGOT, 16, 96), item(Material.REDSTONE, 32, 48), item(Material.LAPIS_LAZULI, 16, 80),
            item(Material.QUARTZ, 16, 80), item(Material.AMETHYST_SHARD, 16, 100), item(Material.EMERALD, 4, 160),
            item(Material.DIAMOND, 2, 240), item(Material.ANCIENT_DEBRIS, 1, 500), item(Material.NETHERITE_INGOT, 1, 1500),
            item(Material.STRING, 16, 24), item(Material.LEATHER, 16, 36), item(Material.BONE, 16, 24),
            item(Material.GUNPOWDER, 16, 42), item(Material.ENDER_PEARL, 4, 100)
        )),
        new Category("food", "Food & Farming", Material.COOKED_BEEF, List.of(
            item(Material.BREAD, 16, 24), item(Material.COOKED_BEEF, 16, 64), item(Material.COOKED_PORKCHOP, 16, 64),
            item(Material.COOKED_CHICKEN, 16, 48), item(Material.COOKED_COD, 16, 40), item(Material.BAKED_POTATO, 16, 32),
            item(Material.GOLDEN_CARROT, 8, 80), item(Material.GOLDEN_APPLE, 2, 150), item(Material.CARROT, 16, 24),
            item(Material.POTATO, 16, 24), item(Material.WHEAT, 16, 24), item(Material.SUGAR_CANE, 16, 28),
            item(Material.PUMPKIN, 8, 32), item(Material.MELON, 8, 32), item(Material.APPLE, 8, 32),
            item(Material.EGG, 16, 20), item(Material.MILK_BUCKET, 1, 30), item(Material.HONEY_BOTTLE, 4, 48),
            item(Material.CAKE, 1, 80), item(Material.COOKIE, 16, 32)
        )),
        new Category("combat", "Combat", Material.IRON_SWORD, List.of(
            item(Material.WOODEN_SWORD, 1, 12), item(Material.STONE_SWORD, 1, 28), item(Material.IRON_SWORD, 1, 90),
            item(Material.GOLDEN_SWORD, 1, 70), item(Material.DIAMOND_SWORD, 1, 450), item(Material.BOW, 1, 80),
            item(Material.CROSSBOW, 1, 120), item(Material.ARROW, 32, 24), item(Material.SHIELD, 1, 65),
            item(Material.LEATHER_CHESTPLATE, 1, 40), item(Material.IRON_CHESTPLATE, 1, 180),
            item(Material.DIAMOND_CHESTPLATE, 1, 750), item(Material.GOLDEN_APPLE, 1, 75),
            item(Material.TOTEM_OF_UNDYING, 1, 1000), item(Material.TRIDENT, 1, 600),
            item(Material.SNOWBALL, 16, 12), item(Material.FIREWORK_ROCKET, 16, 36),
            item(Material.TURTLE_HELMET, 1, 120), item(Material.ENDER_PEARL, 4, 100), item(Material.WIND_CHARGE, 8, 60)
        )),
        new Category("tools", "Tools & Utility", Material.IRON_PICKAXE, List.of(
            item(Material.WOODEN_PICKAXE, 1, 12), item(Material.STONE_PICKAXE, 1, 28), item(Material.IRON_PICKAXE, 1, 90),
            item(Material.GOLDEN_PICKAXE, 1, 70), item(Material.DIAMOND_PICKAXE, 1, 450),
            item(Material.IRON_AXE, 1, 85), item(Material.IRON_SHOVEL, 1, 45), item(Material.IRON_HOE, 1, 55),
            item(Material.SHEARS, 1, 45), item(Material.FLINT_AND_STEEL, 1, 45), item(Material.WATER_BUCKET, 1, 50),
            item(Material.LAVA_BUCKET, 1, 90), item(Material.TORCH, 32, 20), item(Material.CRAFTING_TABLE, 1, 10),
            item(Material.FURNACE, 1, 20), item(Material.ANVIL, 1, 180), item(Material.ENDER_CHEST, 1, 300),
            item(Material.CHEST, 4, 24), item(Material.WHITE_BED, 1, 30), item(Material.COMPASS, 1, 40)
        ))
    );
    private final Map<String, Category> categoriesByKey = new LinkedHashMap<>();
    private DatabaseManager database;
    private EconomySystem economy;

    @Override
    public void onEnable() {
        Plugin economyPlugin = getServer().getPluginManager().getPlugin("EconomySystem");
        if (!(economyPlugin instanceof EconomySystem economySystem)) {
            getLogger().severe("EconomySystem is required; disabling ShopSys.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        economy = economySystem;
        database = economy.getDatabaseManager();
        if (database == null) {
            getLogger().severe("EconomySystem did not provide a database manager; disabling ShopSys.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        for (Category category : CATEGORIES) categoriesByKey.put(category.key(), category);
        initializeConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void initializeConfig() {
        getConfig().addDefault("kitCooldownHours", DEFAULT_KIT_COOLDOWN_HOURS);
        getConfig().options().copyDefaults(true);
        for (Category category : CATEGORIES) {
            String path = "shop.categories." + category.key() + ".items";
            if (!getConfig().contains(path)) writeShopItems(category.key(), category.defaults());
        }
        for (Kit kit : KITS) {
            String path = "kits." + kit.key() + ".items";
            if (!getConfig().contains(path)) getConfig().set(path, defaultKitItems(kit));
        }
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "shop" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can open the shop.");
                    return true;
                }
                String categoryKey = args.length == 0 ? CATEGORIES.getFirst().key() : normalizeCategory(args[0]);
                if (categoryKey == null || !categoriesByKey.containsKey(categoryKey)) {
                    sender.sendMessage("Usage: /shop [blocks|ores|food|combat|tools]");
                    return true;
                }
                openShop(player, categoryKey, 0);
                return true;
            }
            case "kit" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can open the kit menu.");
                    return true;
                }
                openKits(player);
                return true;
            }
            case "shopedit" -> {
                if (!(sender instanceof Player player) || !sender.hasPermission("shopsys.admin")) {
                    sender.sendMessage(Component.text("You do not have permission to edit the shop.", NamedTextColor.RED));
                    return true;
                }
                editShop(player, args);
                return true;
            }
            case "kitedit" -> {
                if (!(sender instanceof Player player) || !sender.hasPermission("shopsys.admin")) {
                    sender.sendMessage(Component.text("You do not have permission to edit kits.", NamedTextColor.RED));
                    return true;
                }
                editKit(player, args);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void openShop(Player player, String categoryKey, int page) {
        Category category = categoriesByKey.get(categoryKey);
        List<ShopItem> entries = readShopItems(categoryKey);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        MenuHolder holder = new MenuHolder(MenuType.SHOP, player.getUniqueId(), categoryKey, safePage);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Shop - " + category.title()));
        holder.setInventory(inventory);

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = safePage * PAGE_SIZE + slot;
            if (index >= entries.size()) break;
            inventory.setItem(slot, shopDisplayItem(entries.get(index)));
        }
        addToolbar(inventory, player);
        inventory.setItem(PREVIOUS_SLOT, button(Material.ARROW, "Previous page", safePage == 0 ? "You are on the first page" : "Page " + safePage + " of " + pages));
        for (int i = 0; i < CATEGORIES.size(); i++) {
            Category tab = CATEGORIES.get(i);
            inventory.setItem(categorySlot(i), button(tab.icon(), tab.title(), tab.key().equals(categoryKey) ? "Selected category" : "Click to browse"));
        }
        inventory.setItem(NEXT_SLOT, button(Material.SPECTRAL_ARROW, "Next page", safePage + 1 >= pages ? "You are on the last page" : "Page " + (safePage + 2) + " of " + pages));
        player.openInventory(inventory);
    }

    private void openKits(Player player) {
        MenuHolder holder = new MenuHolder(MenuType.KITS, player.getUniqueId(), "", 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Kits"));
        holder.setInventory(inventory);
        for (int i = 0; i < KITS.size(); i++) {
            Kit kit = KITS.get(i);
            Material icon = materialOr(kit.swordMaterial(), Material.CHEST);
            inventory.setItem(10 + i, button(icon, kit.name() + " Kit", kitLore(player, kit)));
        }
        addToolbar(inventory, player);
        player.openInventory(inventory);
    }

    private List<Component> kitLore(Player player, Kit kit) {
        long remaining = cooldownRemaining(player.getUniqueId(), kit);
        if (remaining <= 0) return List.of(Component.text("Click to claim", NamedTextColor.GREEN));
        long hours = remaining / 3_600_000L;
        long minutes = (remaining % 3_600_000L) / 60_000L;
        return List.of(Component.text("Available in " + hours + "h " + minutes + "m", NamedTextColor.RED));
    }

    private void addToolbar(Inventory inventory, Player player) {
        inventory.setItem(BALANCE_SLOT, button(Material.BOOK, "Your balance", economy.formatBalance(balance(player.getName()))));
    }

    private ItemStack shopDisplayItem(ShopItem shopItem) {
        ItemStack display = shopItem.item().clone();
        ItemMeta meta = display.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.text("Price: " + economy.formatBalance(shopItem.price()), NamedTextColor.GOLD));
        lore.add(Component.text("Click to buy", NamedTextColor.GREEN));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack button(Material material, String name, String... loreLines) {
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) lore.add(Component.text(line, NamedTextColor.GRAY));
        return button(material, name, lore);
    }

    private ItemStack button(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer().equals(player.getUniqueId())) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        if (slot == BALANCE_SLOT) {
            addToolbar(event.getView().getTopInventory(), player);
            return;
        }
        if (holder.type() == MenuType.KITS) {
            int kitIndex = slot - 10;
            if (kitIndex >= 0 && kitIndex < KITS.size()) claimKit(player, KITS.get(kitIndex));
            return;
        }

        if (slot == PREVIOUS_SLOT) {
            openShop(player, holder.category(), holder.page() - 1);
            return;
        }
        if (slot == NEXT_SLOT) {
            openShop(player, holder.category(), holder.page() + 1);
            return;
        }
        for (int i = 0; i < CATEGORIES.size(); i++) {
            if (slot == categorySlot(i)) {
                openShop(player, CATEGORIES.get(i).key(), 0);
                return;
            }
        }
        if (slot < 0 || slot >= PAGE_SIZE) return;
        List<ShopItem> entries = readShopItems(holder.category());
        int index = holder.page() * PAGE_SIZE + slot;
        if (index < entries.size()) buyItem(player, entries.get(index), holder.category(), holder.page());
    }

    private void buyItem(Player player, ShopItem shopItem, String category, int page) {
        double balance = balance(player.getName());
        if (balance < shopItem.price()) {
            player.sendMessage(Component.text("You cannot afford that item.", NamedTextColor.RED));
            return;
        }

        Inventory simulatedStorage = Bukkit.createInventory(null, 36);
        simulatedStorage.setContents(copyStorageContents(player));
        if (!simulatedStorage.addItem(shopItem.item().clone()).isEmpty()) {
            player.sendMessage(Component.text("Make room in your inventory first.", NamedTextColor.RED));
            return;
        }

        database.removeBalance(player.getName(), shopItem.price());
        for (ItemStack leftover : player.getInventory().addItem(shopItem.item().clone()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.sendMessage(Component.text("Purchased " + shopItem.item().getAmount() + " "
            + shopItem.item().getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".", NamedTextColor.GREEN));
        openShop(player, category, page);
    }

    private void claimKit(Player player, Kit kit) {
        long remaining = cooldownRemaining(player.getUniqueId(), kit);
        if (remaining > 0) {
            player.sendMessage(Component.text("That kit is on cooldown for " + (remaining / 3_600_000L) + "h "
                + ((remaining % 3_600_000L) / 60_000L) + "m.", NamedTextColor.RED));
            return;
        }

        List<ItemStack> kitItems = readKitItems(kit);
        Inventory simulatedStorage = Bukkit.createInventory(null, 36);
        simulatedStorage.setContents(copyStorageContents(player));
        for (ItemStack item : kitItems) {
            if (!simulatedStorage.addItem(item.clone()).isEmpty()) {
                player.sendMessage(Component.text("Make room in your inventory before claiming this kit.", NamedTextColor.RED));
                return;
            }
        }
        for (ItemStack item : kitItems) {
            for (ItemStack leftover : player.getInventory().addItem(item.clone()).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        long cooldownHours = Math.max(0, getConfig().getLong("kitCooldownHours", DEFAULT_KIT_COOLDOWN_HOURS));
        if (cooldownHours > 0) {
            getConfig().set("kit-cooldowns." + player.getUniqueId() + "." + kit.key(), System.currentTimeMillis() + cooldownHours * 3_600_000L);
            saveConfig();
        }
        player.sendMessage(Component.text("You claimed the " + kit.name() + " kit.", NamedTextColor.GREEN));
        openKits(player);
    }

    private ItemStack[] copyStorageContents(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) contents[i] = contents[i].clone();
        }
        return contents;
    }

    private double balance(String playerName) {
        if (!database.accountExists(playerName)) database.createAccount(playerName);
        return database.getBalance(playerName);
    }

    private long cooldownRemaining(UUID playerId, Kit kit) {
        long until = getConfig().getLong("kit-cooldowns." + playerId + "." + kit.key(), 0);
        return Math.max(0, until - System.currentTimeMillis());
    }

    private void editShop(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /shopedit <category> <add|remove|list> [index|price]", NamedTextColor.YELLOW));
            return;
        }
        String category = normalizeCategory(args[0]);
        if (category == null || !categoriesByKey.containsKey(category)) {
            player.sendMessage(Component.text("Category must be blocks, ores, food, combat, or tools.", NamedTextColor.RED));
            return;
        }
        List<ShopItem> items = readShopItems(category);
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length != 3 || player.getInventory().getItemInMainHand().getType().isAir()) {
                    player.sendMessage(Component.text("Hold an item and use /shopedit <category> add <price>.", NamedTextColor.YELLOW));
                    return;
                }
                double price = parsePrice(player, args[2]);
                if (price < 0) return;
                items.add(new ShopItem(player.getInventory().getItemInMainHand().clone(), price));
                writeShopItems(category, items);
                player.sendMessage(Component.text("Added held item to " + category + " for " + economy.formatBalance(price) + ".", NamedTextColor.GREEN));
            }
            case "remove" -> {
                int index = parseIndex(player, args, items.size());
                if (index < 0) return;
                items.remove(index);
                writeShopItems(category, items);
                player.sendMessage(Component.text("Removed shop item #" + (index + 1) + ".", NamedTextColor.GREEN));
            }
            case "list" -> listShopItems(player, category, items);
            default -> player.sendMessage(Component.text("Use add, remove, or list.", NamedTextColor.RED));
        }
    }

    private void editKit(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /kitedit <kit> <add|remove|list> [index]", NamedTextColor.YELLOW));
            return;
        }
        Kit kit = findKit(args[0]);
        if (kit == null) {
            player.sendMessage(Component.text("Kit must be wooden, stone, copper, iron, golden, diamond, or netherite.", NamedTextColor.RED));
            return;
        }
        List<ItemStack> items = readKitItems(kit);
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (args.length != 2 || held.getType().isAir()) {
                    player.sendMessage(Component.text("Hold an item and use /kitedit <kit> add.", NamedTextColor.YELLOW));
                    return;
                }
                items.add(held.clone());
                writeKitItems(kit, items);
                player.sendMessage(Component.text("Added held item to the " + kit.name() + " kit.", NamedTextColor.GREEN));
            }
            case "remove" -> {
                int index = parseIndex(player, args, items.size());
                if (index < 0) return;
                items.remove(index);
                writeKitItems(kit, items);
                player.sendMessage(Component.text("Removed kit item #" + (index + 1) + ".", NamedTextColor.GREEN));
            }
            case "list" -> {
                if (items.isEmpty()) player.sendMessage(Component.text("The " + kit.name() + " kit is empty.", NamedTextColor.YELLOW));
                for (int i = 0; i < items.size(); i++) {
                    ItemStack item = items.get(i);
                    player.sendMessage(Component.text((i + 1) + ". " + item.getAmount() + " "
                        + item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '), NamedTextColor.GRAY));
                }
            }
            default -> player.sendMessage(Component.text("Use add, remove, or list.", NamedTextColor.RED));
        }
    }

    private void listShopItems(Player player, String category, List<ShopItem> items) {
        if (items.isEmpty()) player.sendMessage(Component.text("This shop category is empty.", NamedTextColor.YELLOW));
        for (int i = 0; i < items.size(); i++) {
            ShopItem item = items.get(i);
            player.sendMessage(Component.text((i + 1) + ". " + item.item().getType().name().toLowerCase(Locale.ROOT)
                .replace('_', ' ') + " x" + item.item().getAmount() + " - " + economy.formatBalance(item.price()), NamedTextColor.GRAY));
        }
        player.sendMessage(Component.text("Use /shopedit " + category + " remove <index> to remove an item.", NamedTextColor.YELLOW));
    }

    private double parsePrice(Player player, String value) {
        try {
            double price = Double.parseDouble(value);
            if (Double.isFinite(price) && price >= 0) return price;
        } catch (NumberFormatException ignored) {
        }
        player.sendMessage(Component.text("Price must be a non-negative number.", NamedTextColor.RED));
        return -1;
    }

    private int parseIndex(Player player, String[] args, int size) {
        if (args.length != 3) {
            player.sendMessage(Component.text("Provide the item number shown by the list command.", NamedTextColor.YELLOW));
            return -1;
        }
        try {
            int index = Integer.parseInt(args[2]) - 1;
            if (index >= 0 && index < size) return index;
        } catch (NumberFormatException ignored) {
        }
        player.sendMessage(Component.text("Item number must be from 1 to " + size + ".", NamedTextColor.RED));
        return -1;
    }

    private List<ShopItem> readShopItems(String category) {
        List<ShopItem> items = new ArrayList<>();
        for (Map<?, ?> raw : getConfig().getMapList("shop.categories." + category + ".items")) {
            Object rawItem = raw.get("item");
            Object rawPrice = raw.get("price");
            if (rawItem instanceof ItemStack item && rawPrice instanceof Number price && !item.getType().isAir()) {
                items.add(new ShopItem(item.clone(), price.doubleValue()));
            }
        }
        return items;
    }

    private void writeShopItems(String category, List<ShopItem> items) {
        List<Map<String, Object>> saved = new ArrayList<>();
        for (ShopItem item : items) saved.add(Map.of("item", item.item().clone(), "price", item.price()));
        getConfig().set("shop.categories." + category + ".items", saved);
        saveConfig();
    }

    private List<ItemStack> readKitItems(Kit kit) {
        List<ItemStack> items = new ArrayList<>();
        for (Object raw : getConfig().getList("kits." + kit.key() + ".items", List.of())) {
            if (raw instanceof ItemStack item && !item.getType().isAir()) items.add(item.clone());
        }
        return items;
    }

    private void writeKitItems(Kit kit, List<ItemStack> items) {
        List<ItemStack> saved = new ArrayList<>();
        for (ItemStack item : items) saved.add(item.clone());
        getConfig().set("kits." + kit.key() + ".items", saved);
        saveConfig();
    }

    private List<ItemStack> defaultKitItems(Kit kit) {
        String tier = kit.key().equals("golden") ? "GOLDEN" : kit.name().toUpperCase(Locale.ROOT);
        if (kit.key().equals("wooden")) tier = "WOODEN";
        List<ItemStack> items = new ArrayList<>();
        for (String part : List.of("SWORD", "PICKAXE", "AXE", "SHOVEL")) {
            addMaterial(items, tier + "_" + part, 1, kit.key().equals("copper") ? "STONE_" + part : null);
        }
        String armorTier = kit.key().equals("wooden") || kit.key().equals("stone") ? "LEATHER"
            : kit.key().equals("copper") ? "CHAINMAIL" : tier;
        if (kit.key().equals("copper")) armorTier = "CHAINMAIL";
        for (String part : List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")) {
            addMaterial(items, armorTier + "_" + part, 1, kit.key().equals("copper") ? "IRON_" + part : null);
        }
        items.add(new ItemStack(Material.SHIELD));
        items.add(new ItemStack(Material.BREAD, 16));
        items.add(new ItemStack(Material.TORCH, 16));
        return items;
    }

    private void addMaterial(List<ItemStack> items, String name, int amount, String fallback) {
        Material material = Material.matchMaterial(name);
        if (material == null && fallback != null) material = Material.matchMaterial(fallback);
        if (material == null) {
            getLogger().warning("Skipping unsupported kit material " + name + ".");
            return;
        }
        items.add(new ItemStack(material, amount));
    }

    private static ShopItem item(Material material, int amount, double price) {
        return new ShopItem(new ItemStack(material, amount), price);
    }

    private static int categorySlot(int index) {
        return index < 3 ? 46 + index : 50 + index - 3;
    }

    private String normalizeCategory(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("resources") || normalized.equals("ore")) return "ores";
        if (normalized.equals("tools")) return "tools";
        return normalized;
    }

    private Kit findKit(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return KITS.stream().filter(kit -> kit.key().equals(normalized)).findFirst().orElse(null);
    }

    private Material materialOr(String materialName, Material fallback) {
        Material found = Material.matchMaterial(materialName);
        return found == null ? fallback : found;
    }

    private enum MenuType { SHOP, KITS }

    private record ShopItem(ItemStack item, double price) {}
    private record Category(String key, String title, Material icon, List<ShopItem> defaults) {}
    private record Kit(String key, String name, String swordMaterial) {}

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final UUID viewer;
        private final String category;
        private final int page;
        private Inventory inventory;

        private MenuHolder(MenuType type, UUID viewer, String category, int page) {
            this.type = type;
            this.viewer = viewer;
            this.category = category;
            this.page = page;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private MenuType type() { return type; }
        private UUID viewer() { return viewer; }
        private String category() { return category; }
        private int page() { return page; }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
