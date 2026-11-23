package com.lston.customswords;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomSwordsPlugin extends org.bukkit.plugin.java.JavaPlugin implements Listener {
    private static final List<String> SWORDS = List.of("lightning", "explosion", "pearl", "fall", "bad", "tp");
    private static final List<String> SWORD_CHOICES = List.of("lightning", "explosion", "pearl", "fall", "bad", "tp", "all");
    private static final List<PotionEffectType> BAD_EFFECTS = List.of(
        PotionEffectType.SLOWNESS,
        PotionEffectType.MINING_FATIGUE,
        PotionEffectType.NAUSEA,
        PotionEffectType.BLINDNESS,
        PotionEffectType.HUNGER,
        PotionEffectType.WEAKNESS,
        PotionEffectType.LEVITATION,
        PotionEffectType.UNLUCK,
        PotionEffectType.INSTANT_DAMAGE
    );
    private final NamespacedKey swordKey = new NamespacedKey(this, "custom_sword");
    private final Set<UUID> slamming = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("sword")) return false;
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(Component.text("Usage: /sword <player|selector> <lightning|explosion|pearl|fall|bad|tp|all> [amount]", NamedTextColor.RED));
            return true;
        }

        String swordName = args[1].toLowerCase(Locale.ROOT);
        boolean allSwords = swordName.equals("all");
        if (!allSwords && !SWORDS.contains(swordName)) {
            sender.sendMessage(Component.text("Unknown sword: " + args[1] + ". Use lightning, explosion, pearl, fall, bad, tp, or all.", NamedTextColor.RED));
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
            List<String> swordsToGive = allSwords ? SWORDS : List.of(swordName);
            for (String type : swordsToGive) {
                for (int i = 0; i < amount; i++) {
                    for (ItemStack leftover : target.getInventory().addItem(createSword(type)).values()) {
                        target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                    }
                }
            }
            String description = allSwords ? "every custom sword" : swordName + " sword";
            target.sendMessage(Component.text("You received " + amount + " of " + description + ".", NamedTextColor.GOLD));
        }
        String description = allSwords ? "every custom sword" : swordName + " sword";
        sender.sendMessage(Component.text("Gave " + amount + " of " + description + " to " + targets.size() + " player(s).", NamedTextColor.GREEN));
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
        ItemStack sword = new ItemStack(switch (swordName) {
            case "lightning" -> Material.GOLDEN_SWORD;
            case "explosion" -> Material.IRON_SWORD;
            case "pearl" -> Material.DIAMOND_SWORD;
            case "fall" -> Material.STONE_SWORD;
            case "bad" -> Material.WOODEN_SWORD;
            case "tp" -> Material.DIAMOND_AXE;
            default -> Material.DIAMOND_SWORD;
        });
        ItemMeta meta = sword.getItemMeta();
        meta.displayName(switch (swordName) {
            case "bad" -> Component.text("Bad Sword", NamedTextColor.DARK_PURPLE);
            case "explosion" -> Component.text("Explosion Sword", NamedTextColor.RED);
            case "lightning" -> Component.text("Lightning Sword", NamedTextColor.AQUA);
            case "pearl" -> Component.text("Pearl Sword", NamedTextColor.DARK_PURPLE);
            case "tp" -> Component.text("TP Sword", NamedTextColor.LIGHT_PURPLE);
            default -> Component.text("Fall Sword", NamedTextColor.YELLOW);
        });
        meta.lore(List.of(
            Component.text(switch (swordName) {
                case "bad" -> "Gives whoever you hit every bad effect for 3 seconds.";
                case "explosion" -> "Explodes where you hit without breaking blocks.";
                case "lightning" -> "Strikes lightning on whoever you hit.";
                case "pearl" -> "Teleports you to whoever you hit.";
                case "fall" -> "Launches whoever you hit 8 blocks into the air.";
                case "tp" -> "Launches whoever you hit 8 blocks straight up. They take 5 damage and a slam when they land.";
                default -> "Launches whoever you hit 8 blocks into the air.";
            }, NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(swordKey, PersistentDataType.STRING, swordName);
        meta.setItemModel(new NamespacedKey("lston", "sword_" + swordName));
        sword.setItemMeta(meta);
        return sword;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwordHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (victim.getUniqueId().equals(attacker.getUniqueId())) return;

        ItemStack held = attacker.getInventory().getItemInMainHand();
        ItemMeta meta = held.getItemMeta();
        if (meta == null) return;
        String swordName = meta.getPersistentDataContainer().get(swordKey, PersistentDataType.STRING);
        if (swordName == null) return;

        switch (swordName) {
            case "bad" -> curse(victim);
            case "explosion" -> createTntExplosion(victim);
            case "lightning" -> strikeLightning(victim);
            case "pearl" -> teleportAttacker(attacker, victim);
            case "fall" -> liftFrom(victim);
            case "tp" -> {
                if (liftFrom(victim)) trackLanding(victim);
            }
        }
    }

    private void strikeLightning(LivingEntity victim) {
        Location location = victim.getLocation();
        World world = location.getWorld();
        if (world == null) return;
        world.strikeLightning(location);
        world.spawnParticle(Particle.CRIT, location.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.1);
    }

    private void teleportAttacker(Player attacker, LivingEntity victim) {
        Location origin = attacker.getLocation();
        Location destination = victim.getLocation().clone();
        destination.setYaw(origin.getYaw());
        destination.setPitch(origin.getPitch());
        World world = destination.getWorld();
        if (world == null || origin.getWorld() == null) return;
        origin.getWorld().playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        if (!attacker.teleport(destination)) return;
        world.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        world.spawnParticle(Particle.PORTAL, destination.clone().add(0, 1, 0), 60, 0.5, 1.0, 0.5, 0.5);
    }

    private void curse(LivingEntity victim) {
        for (PotionEffectType type : BAD_EFFECTS) {
            victim.addPotionEffect(new PotionEffect(type, 60, 0), true);
        }
        Location location = victim.getLocation();
        World world = location.getWorld();
        if (world == null) return;
        world.playSound(location, Sound.ENTITY_WITHER_SPAWN, 0.5F, 1.5F);
        world.spawnParticle(Particle.LARGE_SMOKE, location.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.02);
    }

    private void createTntExplosion(LivingEntity victim) {
        victim.getWorld().createExplosion(victim.getLocation(), 3.0F, false, false);
    }

    private boolean liftFrom(Entity victim) {
        Location origin = victim.getLocation();
        World world = origin.getWorld();
        if (world == null) return false;

        int lift = 0;
        for (; lift < 8; lift++) {
            if (origin.getY() + lift + 1 >= world.getMaxHeight()) break;
            if (!origin.clone().add(0, lift + 1, 0).getBlock().isPassable()
                || !origin.clone().add(0, lift + 2, 0).getBlock().isPassable()) break;
        }
        if (lift == 0) return false;

        Location destination = origin.clone().add(0, lift, 0);
        destination.setYaw(origin.getYaw());
        destination.setPitch(origin.getPitch());
        if (!victim.teleport(destination)) return false;
        victim.setFallDistance(0.0F);
        world.playSound(destination, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0F, 1.0F);
        world.spawnParticle(Particle.CLOUD, origin.clone().add(0, 1, 0), 40, 0.4, 0.5, 0.4, 0.1);
        return true;
    }

    private void trackLanding(LivingEntity victim) {
        UUID victimId = victim.getUniqueId();
        if (!slamming.add(victimId)) return;

        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                ticks++;
                if (!victim.isValid() || victim.isDead() || ticks > 400) {
                    slamming.remove(victimId);
                    cancel();
                    return;
                }
                if (ticks > 5 && victim.isOnGround()) {
                    slamming.remove(victimId);
                    victim.setFallDistance(0.0F);
                    victim.damage(5.0D);
                    slamEffects(victim.getLocation());
                    cancel();
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    private void slamEffects(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        Location feet = location.clone().add(0, 0.1, 0);
        Block ground = location.clone().add(0, -0.5, 0).getBlock();
        ring(world, feet, 0.8);
        Bukkit.getScheduler().runTaskLater(this, () -> ring(world, feet.clone().add(0, 0.1, 0), 1.6), 3L);
        if (!ground.getType().isAir()) {
            world.spawnParticle(Particle.BLOCK, feet, 60, 0.4, 0.1, 0.4, 0.15, ground.getBlockData());
        }
        world.spawnParticle(Particle.EXPLOSION, feet, 1, 0, 0, 0, 0);
        world.playSound(feet, Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 1.6F);
        if (!ground.getType().isAir()) world.playSound(feet, Sound.BLOCK_STONE_BREAK, 1.0F, 0.8F);
    }

    private void ring(World world, Location center, double radius) {
        Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(0, 89, 255), 1.0F);
        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2 * i) / 24.0;
            Location point = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, blue);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
            && slamming.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("sword")) return List.of();
        if (args.length == 1) return List.of("@a", "@p", "@r", "@s");
        if (args.length == 2) return SWORD_CHOICES;
        if (args.length == 3) return List.of("1", "2", "4", "8", "16", "64");
        return List.of();
    }
}
