package com.lston.customswords;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomSwordsPlugin extends org.bukkit.plugin.java.JavaPlugin implements Listener {
    private static final List<String> SWORDS = List.of("bad", "explosion", "lightning", "tp", "fall");
    private final NamespacedKey swordKey = new NamespacedKey(this, "custom_sword");
    private final Set<UUID> explosionProtected = ConcurrentHashMap.newKeySet();
    private final Set<UUID> awaitingFallLanding = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> fallAttackers = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> tpAttackers = new ConcurrentHashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("sword")) return false;
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /sword <@a,@s,@r,etc> <bad|explosion|lightning|tp|fall>", NamedTextColor.RED));
            return true;
        }

        String swordName = args[1].toLowerCase(Locale.ROOT);
        if (!SWORDS.contains(swordName)) {
            sender.sendMessage(Component.text("Unknown sword: " + args[1] + ". Use bad, explosion, lightning, tp, or fall.", NamedTextColor.RED));
            return true;
        }

        List<Player> targets = resolveTargets(sender, args[0]);
        if (targets.isEmpty()) {
            sender.sendMessage(Component.text("No matching players found.", NamedTextColor.RED));
            return true;
        }

        for (Player target : targets) {
            target.getInventory().addItem(createSword(swordName));
            target.sendMessage(Component.text("You received the " + swordName + " sword.", NamedTextColor.GOLD));
        }
        sender.sendMessage(Component.text("Gave " + swordName + " sword to " + targets.size() + " player(s).", NamedTextColor.GREEN));
        return true;
    }

    private List<Player> resolveTargets(CommandSender sender, String selector) {
        try {
            if (selector.equalsIgnoreCase("@s") && sender instanceof Player player) {
                return List.of(player);
            }
            List<Player> targets = new ArrayList<>();
            for (Entity entity : Bukkit.selectEntities(sender, selector)) {
                if (entity instanceof Player player) targets.add(player);
            }
            return targets;
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }

    private ItemStack createSword(String swordName) {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.displayName(switch (swordName) {
            case "bad" -> Component.text("Bad Sword", NamedTextColor.DARK_PURPLE);
            case "explosion" -> Component.text("Explosion Sword", NamedTextColor.RED);
            case "lightning" -> Component.text("Lightning Sword", NamedTextColor.AQUA);
            case "tp" -> Component.text("TP Sword", NamedTextColor.LIGHT_PURPLE);
            default -> Component.text("Fall Sword", NamedTextColor.YELLOW);
        });
        meta.lore(List.of(
            Component.text(switch (swordName) {
                case "bad" -> "Applies damaging-status effects for 3 seconds";
                case "explosion" -> "TNT-like damage with no block breaking";
                case "lightning" -> "Strikes the hit player with lightning";
                case "tp" -> "Teleports the hit player, then triggers a safe explosion";
                default -> "Drops the hit player for 2.5 hearts of fall damage";
            }, NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(swordKey, PersistentDataType.STRING, swordName);
        sword.setItemMeta(meta);
        return sword;
    }

    @EventHandler
    public void onSwordHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack held = attacker.getInventory().getItemInMainHand();
        ItemMeta meta = held.getItemMeta();
        if (meta == null) return;
        String swordName = meta.getPersistentDataContainer().get(swordKey, PersistentDataType.STRING);
        if (swordName == null) return;

        switch (swordName) {
            case "bad" -> applyBadEffects(victim);
            case "explosion" -> createTntExplosion(attacker, victim);
            case "lightning" -> victim.getWorld().strikeLightning(victim.getLocation());
            case "tp" -> teleportTarget(attacker, victim);
            case "fall" -> launchForFall(attacker, victim);
        }
    }

    private void applyBadEffects(LivingEntity victim) {
        int duration = 60;
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, 1));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 1));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, 1));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duration, 1));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration, 0));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, duration, 0));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, duration, 1));
    }

    private void createTntExplosion(Player attacker, LivingEntity victim) {
        protectFromExplosion(attacker);
        victim.getWorld().createExplosion(attacker, victim.getLocation(), 4.0F, false, false);
    }

    private void protectFromExplosion(Player attacker) {
        explosionProtected.add(attacker.getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, () -> explosionProtected.remove(attacker.getUniqueId()), 20L);
    }

    private void teleportTarget(Player attacker, LivingEntity victim) {
        Location start = victim.getLocation().clone();
        Location surface = randomSurfaceLocation(start, 5);
        victim.teleport(surface);
        tpAttackers.put(victim.getUniqueId(), attacker.getUniqueId());

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!victim.isValid()) return;
            Location high = surface.clone().add(0, 3, 0);
            victim.teleport(high);
            protectFromExplosion(attacker);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!victim.isValid()) return;
                victim.getWorld().createExplosion(attacker, victim.getLocation(), 4.0F, false, false);
                tpAttackers.remove(victim.getUniqueId());
            }, 30L);
        }, 5L);
    }

    private void launchForFall(Player attacker, LivingEntity victim) {
        Location start = victim.getLocation().clone();
        Location high = start.clone().add(0, 20, 0);
        victim.teleport(high);
        awaitingFallLanding.add(victim.getUniqueId());
        fallAttackers.put(victim.getUniqueId(), attacker.getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (awaitingFallLanding.remove(victim.getUniqueId())) {
                fallAttackers.remove(victim.getUniqueId());
            }
        }, 600L);
    }

    private Location randomSurfaceLocation(Location origin, int radius) {
        World world = origin.getWorld();
        int dx = random.nextInt(radius * 2 + 1) - radius;
        int dz;
        do {
            dz = random.nextInt(radius * 2 + 1) - radius;
        } while (dx * dx + dz * dz > radius * radius);

        int x = origin.getBlockX() + dx;
        int z = origin.getBlockZ() + dz;
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
    }

    @EventHandler
    public void onExplosionDamage(EntityDamageEvent event) {
        if (explosionProtected.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!awaitingFallLanding.contains(player.getUniqueId())) return;
        if (!player.isOnGround() || player.getFallDistance() < 2.0F) return;

        awaitingFallLanding.remove(player.getUniqueId());
        UUID attackerId = fallAttackers.remove(player.getUniqueId());
        Player attacker = attackerId == null ? null : Bukkit.getPlayer(attackerId);
        if (attacker != null) {
            player.damage(5.0D, attacker);
        } else {
            player.damage(5.0D);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("sword")) return List.of();
        if (args.length == 1) return List.of("@a", "@p", "@r", "@s");
        if (args.length == 2) return SWORDS;
        return List.of();
    }
}
