package net.lstonstudios.gameplayrewards;

import de.imdacro.economySystem.EconomySystem;
import de.imdacro.economySystem.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.BitSet;

public final class GameplayRewardsPlugin extends org.bukkit.plugin.java.JavaPlugin implements Listener {
    private NamespacedKey placedBlocksKey;
    private DatabaseManager database;

    @Override
    public void onEnable() {
        Plugin economyPlugin = getServer().getPluginManager().getPlugin("EconomySystem");
        if (!(economyPlugin instanceof EconomySystem economy)) {
            getLogger().severe("EconomySystem is required; disabling GameplayRewards.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        database = economy.getDatabaseManager();
        if (database == null) {
            getLogger().severe("EconomySystem did not provide a database manager; disabling GameplayRewards.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        placedBlocksKey = new NamespacedKey(this, "player_placed_blocks");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        markPlayerPlaced(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        Block block = event.getBlock();
        if (isPlayerPlaced(block)) return;

        int reward = blockReward(block.getType());
        credit(player, reward, "for mining " + block.getType().name().toLowerCase().replace('_', ' '));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || entity instanceof ArmorStand) return;

        Player killer = entity.getKiller();
        if (killer == null || killer.getGameMode() != GameMode.SURVIVAL) return;

        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        int reward = entityReward(maxHealth == null ? entity.getHealth() : maxHealth.getValue());
        credit(killer, reward, "for defeating " + entity.getType().name().toLowerCase().replace('_', ' '));
    }

    private void credit(Player player, int amount, String reason) {
        String playerName = player.getName();
        if (!database.accountExists(playerName)) database.createAccount(playerName);
        database.addBalance(playerName, amount);
        player.sendMessage(Component.text("Earned $" + amount + " " + reason + ".", NamedTextColor.GREEN));
    }

    private void markPlayerPlaced(Block block) {
        PersistentDataContainer data = block.getChunk().getPersistentDataContainer();
        byte[] stored = data.get(placedBlocksKey, PersistentDataType.BYTE_ARRAY);
        BitSet positions = BitSet.valueOf(stored == null ? new byte[0] : stored);
        positions.set(blockIndex(block));
        data.set(placedBlocksKey, PersistentDataType.BYTE_ARRAY, positions.toByteArray());
    }

    private boolean isPlayerPlaced(Block block) {
        PersistentDataContainer data = block.getChunk().getPersistentDataContainer();
        byte[] stored = data.get(placedBlocksKey, PersistentDataType.BYTE_ARRAY);
        if (stored == null) return false;

        BitSet positions = BitSet.valueOf(stored);
        int index = blockIndex(block);
        if (!positions.get(index)) return false;

        positions.clear(index);
        if (positions.isEmpty()) data.remove(placedBlocksKey);
        else data.set(placedBlocksKey, PersistentDataType.BYTE_ARRAY, positions.toByteArray());
        return true;
    }

    private int blockIndex(Block block) {
        int y = block.getY() - block.getWorld().getMinHeight();
        int x = block.getX() & 15;
        int z = block.getZ() & 15;
        return y * 256 + x * 16 + z;
    }

    static int blockReward(Material material) {
        String name = material.name();
        String oreName = name.startsWith("DEEPSLATE_") ? name.substring("DEEPSLATE_".length()) : name;
        if (name.equals("ANCIENT_DEBRIS") || oreName.equals("EMERALD_ORE")) return 100;
        if (oreName.equals("DIAMOND_ORE")) return 75;
        if (name.equals("SPONGE") || name.equals("WET_SPONGE")) return 60;
        if (oreName.equals("GOLD_ORE") || name.equals("NETHER_GOLD_ORE")) return 30;
        if (oreName.equals("REDSTONE_ORE") || oreName.equals("LAPIS_ORE")) return 25;
        if (name.equals("NETHER_QUARTZ_ORE")) return 20;
        if (oreName.equals("IRON_ORE")) return 15;
        if (oreName.equals("COPPER_ORE") || oreName.equals("COAL_ORE")) return 10;
        return 5;
    }

    static int entityReward(double maxHealth) {
        if (maxHealth > 150) return 100;
        if (maxHealth > 80) return 70;
        if (maxHealth > 40) return 40;
        if (maxHealth > 20) return 20;
        if (maxHealth > 10) return 10;
        return 5;
    }
}
