package me.lukiiy.corpse;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.lukiiy.manneInventory.MannequinInventoryManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Corpse extends JavaPlugin implements Listener {
    private final Set<Mannequin> tracked = ConcurrentHashMap.newKeySet();
    public static NamespacedKey KEY;
    private int lifespan;

    @Override
    public void onEnable() {
        setupConfig();
        KEY = new NamespacedKey(this, "corpse");
        getServer().getPluginManager().registerEvents(this, this);

        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            if (lifespan < 1) return;

            tracked.forEach(npc -> {
                String data = npc.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
                if (data == null || !data.contains(";")) return;

                String[] parts = data.split(";");
                if (parts.length < 2) return;

                try {
                    if (!npc.isDead() && npc.getWorld().getGameTime() - Long.parseLong(parts[0]) >= Long.parseLong(parts[1])) {
                        popInventory(npc);
                        npc.remove();
                    }
                } catch (NumberFormatException ignored) {}
            });
        }, 20L, 100L);

        registerCommand("corpse", new Cmd());
    }

    @Override
    public void onDisable() {}

    public static Corpse getInstance() {
        return JavaPlugin.getPlugin(Corpse.class);
    }

    // Config
    public void setupConfig() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        lifespan = getConfig().getInt("lifespan", 300);
    }

    private String doPlaceholders(String thing, Player player) {
        return thing.replace("%p", MiniMessage.miniMessage().serialize(player.displayName()));
    }

    public void makeCorpse(Player p) {
        PlayerInventory inv = p.getInventory();
        Location location = p.getLocation();

        if (location.getWorld() == null) return;

        location.getWorld().spawn(location, Mannequin.class, entity -> {
            entity.setProfile(getSkinProfile(p));
            entity.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, location.getWorld().getGameTime() + ";" + lifespan * 20);
            entity.setGravity(getConfig().getBoolean("gravity"));
            entity.setInvulnerable(getConfig().getBoolean("invulnerable"));
            entity.setImmovable(getConfig().getBoolean("immovable"));
            entity.setMainHand(p.getMainHand());

            if (getConfig().getBoolean("keepVelocity")) entity.setVelocity(p.getVelocity());

            switch (getConfig().getString("pose")) {
                case "fall_falling" -> entity.setPose(Pose.FALL_FLYING);
                case "crouching", "sneaking" -> entity.setPose(Pose.SNEAKING);
                case "sleeping" -> entity.setPose(Pose.SLEEPING);
                case null, default -> entity.setPose(Pose.SWIMMING);
            }

            double hp = getConfig().getDouble("health");
            if (hp > 0) {
                AttributeInstance hpInst = entity.getAttribute(Attribute.MAX_HEALTH);
                if (hpInst != null) hpInst.setBaseValue(hp);

                entity.setHealth(hp);
            }

            String itemMode = getConfig().getString("itemTreatment", "default");
            if (!inv.isEmpty() && !itemMode.equalsIgnoreCase("default")) {
                EntityEquipment equip = entity.getEquipment();

                equip.setHelmet(inv.getHelmet(), false);
                equip.setChestplate(inv.getChestplate(), false);
                equip.setLeggings(inv.getLeggings(), false);
                equip.setBoots(inv.getBoots(), false);
                equip.setItemInOffHand(inv.getItemInMainHand(), false);
                equip.setItemInOffHand(inv.getItemInOffHand(), false);

                Inventory inventory = Bukkit.createInventory(null, 54);

                inventory.setContents(Arrays.stream(inv.getContents()).filter(Objects::nonNull).toArray(ItemStack[]::new));
                MannequinInventoryManager.set(entity, inventory);
            }

            String label = getConfig().getString("label", "");
            entity.setDescription(label.isBlank() ? Component.empty() : MiniMessage.miniMessage().deserialize(doPlaceholders(label, p)));
            entity.setCustomNameVisible(!label.isBlank());
        });
    }

    private ResolvableProfile getSkinProfile(Player p) {
        String skin = getConfig().getString("skin", "").trim();
        if (skin.isEmpty()) return ResolvableProfile.resolvableProfile(p.getPlayerProfile());

        try {
            UUID uuid = UUID.fromString(skin);
            return ResolvableProfile.resolvableProfile(getServer().createProfile(uuid));
        } catch (IllegalArgumentException ex) {
            if (skin.length() > 16) {
                PlayerProfile profile = getServer().createProfile(UUID.randomUUID());

                profile.setProperty(new ProfileProperty("textures", skin));
                return ResolvableProfile.resolvableProfile(profile);
            } else {
                return ResolvableProfile.resolvableProfile(getServer().createProfile(skin));
            }
        }
    }

    // Listener
    @EventHandler(priority = EventPriority.MONITOR)
    public void death(PlayerDeathEvent e) {
        if (!getConfig().getString("itemTreatment", "default").equalsIgnoreCase("default")) {
            e.getDrops().clear();
        }

        makeCorpse(e.getEntity());
    }

    private void popInventory(Mannequin mannequin) {
        Inventory npcInv = MannequinInventoryManager.get(mannequin);
        if (npcInv == null || npcInv.isEmpty()) return;

        Location loc = mannequin.getLocation();
        if (loc.getWorld() == null) return;

        Location spawn = loc.add(0, .3, 0);

        for (ItemStack item : npcInv.getContents()) {
            if (item == null) continue;

            loc.getWorld().dropItemNaturally(spawn, item);
        }

        mannequin.getEquipment().clear();
        npcInv.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void npcDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Mannequin npc) || !npc.getPersistentDataContainer().has(KEY)) return;
        popInventory(npc);
    }

    @EventHandler
    public void rightClick(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Mannequin npc) || !npc.getPersistentDataContainer().has(KEY) || !getConfig().getString("itemTreatment", "").equalsIgnoreCase("pop")) return;

        Inventory npcInv = MannequinInventoryManager.get(npc);
        if (npcInv == null || npcInv.isEmpty()) return;

        Player p = e.getPlayer();

        popInventory(npc);
        p.playSound(npc.getLocation(), Sound.BLOCK_CHEST_OPEN, 1, 1);
    }

    @EventHandler
    public void worldEntityAdd(EntityAddToWorldEvent e) {
        if (!(e.getEntity() instanceof Mannequin npc)) return;
        if (npc.getPersistentDataContainer().has(KEY, PersistentDataType.STRING)) tracked.add(npc);
    }

    @EventHandler
    public void worldEntityRemove(EntityRemoveFromWorldEvent e) {
        if (!(e.getEntity() instanceof Mannequin npc)) return;
        if (npc.getPersistentDataContainer().has(KEY, PersistentDataType.STRING)) tracked.remove(npc);
    }
}
