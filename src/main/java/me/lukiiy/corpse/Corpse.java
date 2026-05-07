package me.lukiiy.corpse;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import me.lukiiy.manneInventory.MannequinInventoryManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Corpse extends JavaPlugin implements Listener {
    private final Set<Mannequin> tracked = ConcurrentHashMap.newKeySet();

    public static NamespacedKey KEY;
    private NamespacedKey xpKey;

    private int lifespan;

    @Override
    public void onEnable() {
        KEY = new NamespacedKey(this, "corpse");
        xpKey = new NamespacedKey(this, "xp");

        setupConfig();
        getServer().getPluginManager().registerEvents(this, this);

        registerCommand("corpse", new Cmd());
    }

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

    // API
    public Mannequin makeCorpse(Player p) {
        Location location = p.getLocation();
        if (location.getWorld() == null) return null;

        return location.getWorld().spawn(location, Mannequin.class, entity -> {
            entity.setProfile(getSkinProfile(p));
            entity.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, location.getWorld().getGameTime() + ";" + lifespan * 20);
            entity.setGravity(getConfig().getBoolean("gravity"));
            entity.setInvulnerable(getConfig().getBoolean("invulnerable"));
            entity.setImmovable(getConfig().getBoolean("immovable"));
            entity.setMainHand(p.getMainHand());

            if (getConfig().getBoolean("keepVelocity")) entity.setVelocity(p.getVelocity());

            switch (getConfig().getString("pose")) {
                case "fall_falling", "fall falling" -> entity.setPose(Pose.FALL_FLYING);
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

            String name = getConfig().getString("name", "");
            String label = getConfig().getString("label", "");

            entity.customName(name.isBlank() ? null : MiniMessage.miniMessage().deserialize(doPlaceholders(name, p)));
            entity.setDescription(label.isBlank() ? null : MiniMessage.miniMessage().deserialize(doPlaceholders(label, p)));
            entity.setCustomNameVisible(getConfig().getBoolean("nameAlwaysVisible") && !name.isBlank());

            EntityScheduler scheduler = entity.getScheduler();

            if (getConfig().getBoolean("armAnimation")) scheduler.execute(this, entity::swingMainHand, null, 2L);
            if (getConfig().getBoolean("floatOnLiquids")) scheduler.runAtFixedRate(this, (task) -> {
                if (entity.isInWater() || entity.isInLava()) {
                    Vector vector = entity.getVelocity();

                    vector.setY(.1);

                    entity.setVelocity(vector);
                }
            }, null, 1L, 2L);
        });
    }

    public void popCorpseData(Mannequin mannequin) {
        Inventory npcInv = MannequinInventoryManager.get(mannequin);
        if (npcInv == null || npcInv.isEmpty()) return;

        Location loc = mannequin.getLocation();
        if (loc.getWorld() == null) return;

        Location spawn = loc.add(0, .3, 0);
        for (ItemStack item : npcInv.getContents()) {
            if (item == null) continue;

            loc.getWorld().dropItemNaturally(spawn, item);
        }

        int xp = mannequin.getPersistentDataContainer().getOrDefault(xpKey, PersistentDataType.INTEGER, 0);
        if (xp > 0) loc.getWorld().spawn(spawn, ExperienceOrb.class, orb -> orb.setExperience(xp));

        mannequin.getEquipment().clear();
        npcInv.clear();
    }

    public Set<Mannequin> getTracked() {
        return Collections.unmodifiableSet(tracked);
    }

    private ResolvableProfile getSkinProfile(Player p) {
        String skin = getConfig().getString("skin", "").trim();
        if (skin.isEmpty()) return ResolvableProfile.resolvableProfile(p.getPlayerProfile());

        try {
            return ResolvableProfile.resolvableProfile(getServer().createProfile(UUID.fromString(skin)));
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
        Player p = e.getEntity();
        Mannequin npc = makeCorpse(e.getEntity());
        if (npc == null) return;

        if (!getConfig().getString("itemTreatment", "default").equalsIgnoreCase("default")) {
            e.getDrops().clear();

            PlayerInventory inv = p.getInventory();
            EntityEquipment equip = npc.getEquipment();

            equip.setHelmet(inv.getHelmet(), false);
            equip.setChestplate(inv.getChestplate(), false);
            equip.setLeggings(inv.getLeggings(), false);
            equip.setBoots(inv.getBoots(), false);
            equip.setItemInOffHand(inv.getItemInMainHand(), false);
            equip.setItemInOffHand(inv.getItemInOffHand(), false);

            Inventory inventory = Bukkit.createInventory(null, 54);
            inventory.setContents(Arrays.stream(inv.getContents()).filter(Objects::nonNull).toArray(ItemStack[]::new));

            MannequinInventoryManager.set(npc, inventory);
        }

        if (!getConfig().getString("xpTreatment", "default").equalsIgnoreCase("default")) {
            e.setShouldDropExperience(false);

            npc.getPersistentDataContainer().set(xpKey, PersistentDataType.INTEGER, e.getDroppedExp());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void damage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Mannequin npc) || !npc.getPersistentDataContainer().has(KEY) || getConfig().getBoolean("onlyAcceptEntityDamage")) return;

        if (e.getDamageSource().getCausingEntity() == null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void npcDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Mannequin npc) || !npc.getPersistentDataContainer().has(KEY)) return;

        popCorpseData(npc);
    }

    @EventHandler
    public void interaction(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Mannequin npc) || !npc.getPersistentDataContainer().has(KEY) || !getConfig().getString("itemTreatment", "").equalsIgnoreCase("pop")) return;

        Inventory npcInv = MannequinInventoryManager.get(npc);
        if (npcInv == null || npcInv.isEmpty()) return;

        Player p = e.getPlayer();

        popCorpseData(npc);
        npc.setHealth(0);
        p.playSound(npc.getLocation(), Sound.BLOCK_CHEST_OPEN, 1, 1);
    }

    @EventHandler
    public void worldEntityAdd(EntityAddToWorldEvent e) {
        if (!(e.getEntity() instanceof Mannequin npc)) return;

        if (npc.getPersistentDataContainer().has(KEY, PersistentDataType.STRING)) {
            if (tracked.contains(npc)) return;

            tracked.add(npc);

            if (lifespan < 1) return;

            npc.getScheduler().runDelayed(this, task -> {
                if (!npc.isValid() || npc.isDead()) {
                    tracked.remove(npc);
                    return;
                }

                popCorpseData(npc);
                tracked.remove(npc);
                npc.remove();
            }, null, lifespan * 20L);
        }
    }

    @EventHandler
    public void worldEntityRemove(EntityRemoveFromWorldEvent e) {
        if (!(e.getEntity() instanceof Mannequin npc)) return;

        if (npc.getPersistentDataContainer().has(KEY, PersistentDataType.STRING)) tracked.remove(npc);
    }
}
