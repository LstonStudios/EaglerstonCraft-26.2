package com.lston.customtnt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomTNTPlugin extends org.bukkit.plugin.java.JavaPlugin implements Listener {
    private static final List<String> TYPES = List.of("heavy", "fire", "lightning", "x-ray", "black-hole", "anti-tnt");
    private static final long TNT_RESTORE_WINDOW_MILLIS = 10 * 60 * 1000L;
    private static final double TNT_RESTORE_RADIUS_SQUARED = 16 * 16;
    private record BlockPosition(UUID worldId, int x, int y, int z) {}
    private record TntDamageSnapshot(long capturedAt, BlockData blockData) {}

    private final NamespacedKey tntKey = new NamespacedKey(this, "custom_tnt");
    private final Map<Location, String> placedTnt = new ConcurrentHashMap<>();
    private final Map<BlockPosition, TntDamageSnapshot> recentTntDamage = new LinkedHashMap<>();
    private final Random random = new Random();
    private int customTntExplosionDepth;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("customtnt") && !command.getName().equalsIgnoreCase("tnt")) return false;
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(Component.text("Usage: /tnt <@a,@s,@r,etc> <heavy|fire|lightning|x-ray|black-hole|anti-tnt> [amount]", NamedTextColor.RED));
            return true;
        }

        String type = args[1].toLowerCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            sender.sendMessage(Component.text("Unknown TNT type: " + args[1] + ". Use heavy, fire, lightning, x-ray, black-hole, or anti-tnt.", NamedTextColor.RED));
            return true;
        }

        int amount = 1;
        if (args.length == 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1 || amount > 64) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                sender.sendMessage(Component.text("Amount must be a whole number from 1 to 64.", NamedTextColor.RED));
                return true;
            }
        }

        List<Player> targets = resolveTargets(sender, args[0]);
        if (targets.isEmpty()) {
            sender.sendMessage(Component.text("No matching players found.", NamedTextColor.RED));
            return true;
        }

        for (Player target : targets) {
            target.getInventory().addItem(createTnt(type, amount));
            target.sendMessage(Component.text("You received " + amount + " " + type + " TNT.", NamedTextColor.GOLD));
        }
        sender.sendMessage(Component.text("Gave " + type + " TNT to " + targets.size() + " player(s).", NamedTextColor.GREEN));
        return true;
    }

    private List<Player> resolveTargets(CommandSender sender, String selector) {
        try {
            if (selector.equalsIgnoreCase("@s") && sender instanceof Player player) return List.of(player);
            List<Player> targets = new ArrayList<>();
            for (Entity entity : Bukkit.selectEntities(sender, selector)) {
                if (entity instanceof Player player) targets.add(player);
            }
            return targets;
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }

    private ItemStack createTnt(String type, int amount) {
        ItemStack item = new ItemStack(Material.TNT, amount);
        ItemMeta meta = item.getItemMeta();
        NamedTextColor color = switch (type) {
            case "heavy" -> NamedTextColor.DARK_RED;
            case "fire" -> NamedTextColor.GOLD;
            case "lightning" -> NamedTextColor.AQUA;
            case "x-ray" -> NamedTextColor.GREEN;
            case "anti-tnt" -> NamedTextColor.WHITE;
            default -> NamedTextColor.DARK_PURPLE;
        };
        meta.displayName(Component.text(type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1) + " TNT", color));
        meta.lore(List.of(Component.text(typeDescription(type), NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(tntKey, PersistentDataType.STRING, type);
        meta.setItemModel(new NamespacedKey("lston", switch (type) {
            case "heavy" -> "boss_tnt";
            case "fire" -> "fire_tnt";
            case "lightning" -> "firework_tnt";
            case "x-ray" -> "xray_tnt";
            case "anti-tnt" -> "anti_tnt";
            default -> "black_hole_tnt";
        }));
        item.setItemMeta(meta);
        return item;
    }

    private String typeDescription(String type) {
        return switch (type) {
            case "heavy" -> "Large explosion that breaks blocks";
            case "fire" -> "Explosion that sets nearby blocks on fire";
            case "lightning" -> "Many lightning bolts strike around the explosion";
            case "x-ray" -> "Clears a 3x3x3 area except ores and unbreakable blocks";
            case "anti-tnt" -> "Restores blocks destroyed by TNT nearby in the last 10 minutes";
            default -> "Creates a black-hole that lasts 6 seconds";
        };
    }

    @EventHandler
    public void onTntPlace(BlockPlaceEvent event) {
        BlockPosition position = blockPosition(event.getBlockPlaced());
        recentTntDamage.remove(position);

        ItemMeta meta = event.getItemInHand().getItemMeta();
        if (meta == null) return;
        String type = meta.getPersistentDataContainer().get(tntKey, PersistentDataType.STRING);
        if (type != null) placedTnt.put(event.getBlock().getLocation(), type);
    }

    @EventHandler
    public void onTntBreak(BlockBreakEvent event) {
        placedTnt.remove(event.getBlock().getLocation());
        recentTntDamage.remove(blockPosition(event.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() != null) recentTntDamage.remove(blockPosition(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTntExplosion(EntityExplodeEvent event) {
        if (customTntExplosionDepth == 0 && !(event.getEntity() instanceof TNTPrimed)) return;

        long now = System.currentTimeMillis();
        pruneExpiredTntDamage(now);
        for (Block block : event.blockList()) {
            rememberTntDamage(block, now);
        }
    }

    @EventHandler
    public void onTntPrime(TNTPrimeEvent event) {
        Block block = event.getBlock();
        String type = placedTnt.remove(block.getLocation());
        if (type == null) return;

        event.setCancelled(true);
        block.setType(Material.AIR);
        Location location = block.getLocation().add(0.5, 0.5, 0.5);

        Entity primer = event.getPrimingEntity();
        switch (type) {
            case "heavy" -> createTntExplosion(primer, location, 7.0F, false, true);
            case "fire" -> createTntExplosion(primer, location, 4.0F, true, true);
            case "lightning" -> {
                createTntExplosion(primer, location, 4.0F, false, false);
                for (int i = 0; i < 12; i++) {
                    Location bolt = location.clone().add(
                        random.nextInt(7) - 3,
                        0,
                        random.nextInt(7) - 3
                    );
                    bolt.setY(block.getWorld().getHighestBlockYAt(bolt));
                    block.getWorld().strikeLightning(bolt);
                }
            }
            case "x-ray" -> explodeXrayCube(location);
            case "black-hole" -> startBlackHole(location, primer);
            case "anti-tnt" -> restoreRecentTntDamage(location);
        }
    }

    private void createTntExplosion(Entity source, Location location, float power, boolean setFire, boolean breakBlocks) {
        customTntExplosionDepth++;
        try {
            location.getWorld().createExplosion(source, location, power, setFire, breakBlocks);
        } finally {
            customTntExplosionDepth--;
        }
    }

    private void rememberTntDamage(Block block, long capturedAt) {
        if (block.getType().isAir()) return;
        BlockPosition position = blockPosition(block);
        recentTntDamage.putIfAbsent(position, new TntDamageSnapshot(capturedAt, block.getBlockData().clone()));
    }

    private BlockPosition blockPosition(Block block) {
        return new BlockPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private void pruneExpiredTntDamage(long now) {
        long oldestValid = now - TNT_RESTORE_WINDOW_MILLIS;
        recentTntDamage.entrySet().removeIf(entry -> entry.getValue().capturedAt() < oldestValid);
    }

    private void restoreRecentTntDamage(Location center) {
        long now = System.currentTimeMillis();
        pruneExpiredTntDamage(now);
        World world = center.getWorld();
        UUID worldId = world.getUID();
        int restored = 0;
        Iterator<Map.Entry<BlockPosition, TntDamageSnapshot>> iterator = recentTntDamage.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPosition, TntDamageSnapshot> entry = iterator.next();
            BlockPosition position = entry.getKey();
            if (!position.worldId().equals(worldId)) continue;

            double dx = position.x() + 0.5 - center.getX();
            double dy = position.y() + 0.5 - center.getY();
            double dz = position.z() + 0.5 - center.getZ();
            if (dx * dx + dy * dy + dz * dz > TNT_RESTORE_RADIUS_SQUARED) continue;

            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            Material currentType = block.getType();
            if (!currentType.isAir() && currentType != Material.FIRE && currentType != Material.SOUL_FIRE) {
                iterator.remove();
                continue;
            }

            block.setBlockData(entry.getValue().blockData().clone(), false);
            iterator.remove();
            restored++;
        }
        world.spawnParticle(Particle.PORTAL, center, Math.min(100, Math.max(12, restored)), 1.5, 1.5, 1.5, 0.1);
    }

    private void explodeXrayCube(Location center) {
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 8, 1.0, 1.0, 1.0, 0.0);
        long now = System.currentTimeMillis();
        pruneExpiredTntDamage(now);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block block = center.clone().add(dx, dy, dz).getBlock();
                    if (isProtectedXrayBlock(block.getType())) continue;
                    rememberTntDamage(block, now);
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    private boolean isProtectedXrayBlock(Material material) {
        String name = material.name();
        if (name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS") || name.equals("RAW_IRON_BLOCK") || name.equals("RAW_COPPER_BLOCK") || name.equals("RAW_GOLD_BLOCK")) return true;
        return EnumSet.of(
            Material.BEDROCK,
            Material.BARRIER,
            Material.END_PORTAL,
            Material.END_PORTAL_FRAME,
            Material.END_GATEWAY,
            Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID,
            Material.JIGSAW,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.REINFORCED_DEEPSLATE
        ).contains(material);
    }

    private void startBlackHole(Location center, Entity primer) {
        Location core = center.clone();
        core.setX(Math.floor(core.getX()));
        core.setY(Math.floor(core.getY()));
        core.setZ(Math.floor(core.getZ()));

        core.getWorld().createExplosion(primer, core.clone().add(0.5, 0.5, 0.5), 3.0F, false, false);
        core.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, core.clone().add(0.5, 0.5, 0.5), 5, 1.0, 1.0, 1.0, 0.0);
        core.getWorld().spawnParticle(Particle.LARGE_SMOKE, core.clone().add(0.5, 0.5, 0.5), 180, 1.5, 1.5, 1.5, 0.05);

        HashMap<Block, BlockData> originalBlocks = new HashMap<>();

        // Seven-block plus/cross core inside a 3x3x3 area.
        // Center, top, bottom, north, south, east, and west.
        Location plusCenter = core.clone().add(0.5, 0.5, 0.5);
        int[][] plusPositions = {
            {0, 0, 0},
            {1, 0, 0},
            {-1, 0, 0},
            {0, 1, 0},
            {0, -1, 0},
            {0, 0, 1},
            {0, 0, -1}
        };

        for (int[] position : plusPositions) {
            Block block = plusCenter.clone()
                .add(position[0], position[1], position[2])
                .getBlock();
            if (block.getType() == Material.BEDROCK || block.getType() == Material.BARRIER) continue;
            originalBlocks.put(block, block.getBlockData().clone());
            block.setType(Material.BLACK_CONCRETE, false);
        }
        Particle.DustOptions purple = new Particle.DustOptions(Color.fromRGB(170, 70, 255), 1.6F);
        Particle.DustOptions black = new Particle.DustOptions(Color.fromRGB(8, 8, 12), 2.0F);

        final int[] elapsedTicks = {0};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            elapsedTicks[0]++;
            int elapsed = elapsedTicks[0];
            Location visualCore = core.clone().add(1.0, 1.0, 1.0);

            core.getWorld().spawnParticle(Particle.DUST, visualCore, 240, 0.75, 0.75, 0.75, 0.0, black);
            core.getWorld().spawnParticle(Particle.SONIC_BOOM, visualCore, 3, 0.25, 0.25, 0.25, 0.0);
            core.getWorld().spawnParticle(Particle.PORTAL, visualCore, 180, 2.0, 2.0, 2.0, 0.3);

            for (int i = 0; i < 48; i++) {
                double angle = (Math.PI * 2 * i) / 48.0 + (elapsed * 0.18);
                double radius = 2.0 + (i % 4) * 0.45;
                Location ring = visualCore.clone().add(
                    Math.cos(angle) * radius,
                    Math.sin(angle * 3.0) * 0.65,
                    Math.sin(angle) * radius
                );
                core.getWorld().spawnParticle(Particle.DUST, ring, 4, 0.08, 0.08, 0.08, 0.0, purple);
            }

            for (Entity entity : core.getWorld().getNearbyEntities(visualCore, 10, 10, 10)) {
                Location entityLocation = entity.getLocation().clone().add(0, entity.getHeight() * 0.5, 0);
                double distance = entityLocation.distance(visualCore);
                if (distance > 10.0 || distance < 0.01) continue;

                Vector pull = visualCore.toVector().subtract(entityLocation.toVector()).normalize()
                    .multiply(Math.max(0.10, 0.55 * (11.0 - distance) / 10.0));
                entity.setVelocity(entity.getVelocity().multiply(0.30).add(pull));

                if (distance <= 1.5) {
                    if (entity instanceof Item) {
                        entity.remove();
                    } else if (entity instanceof LivingEntity living && !(entity instanceof org.bukkit.entity.ArmorStand)) {
                        if (elapsed % 20 == 0) living.damage(3.0D);
                    }
                }
            }

            if (elapsed >= 120) {
                for (Map.Entry<Block, BlockData> entry : originalBlocks.entrySet()) {
                    entry.getKey().setBlockData(entry.getValue(), false);
                }
                task.cancel();
            }
        }, 0L, 1L);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("customtnt") && !command.getName().equalsIgnoreCase("tnt")) return List.of();
        if (args.length == 1) return List.of("@a", "@p", "@r", "@s");
        if (args.length == 2) return TYPES;
        if (args.length == 3) return List.of("1", "4", "8", "16", "64");
        return List.of();
    }
}
