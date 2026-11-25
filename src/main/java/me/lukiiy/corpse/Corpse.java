package me.lukiiy.corpse;

import com.destroystokyo.paper.SkinParts;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import me.lukiiy.manneInventory.MannequinInventoryManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Corpse extends JavaPlugin {
    protected final Set<Mannequin> tracked = ConcurrentHashMap.newKeySet();

    private static NamespacedKey KEY;
    private static NamespacedKey XP_KEY;

    protected int lifespan;
    protected boolean storeItems;
    protected boolean storeXp;
    protected SkinParts skinParts;

    @Override
    public void onEnable() {
        KEY = new NamespacedKey(this, "corpse");
        XP_KEY = new NamespacedKey(this, "xp");

        setupConfig();
        updateCommons();

        getServer().getPluginManager().registerEvents(new Listen(this), this);
        registerCommand("corpse", new Cmd());
    }

    public static Corpse getInstance() {
        return JavaPlugin.getPlugin(Corpse.class);
    }

    public static NamespacedKey getKey() {
        return KEY;
    }

    public static NamespacedKey getXpKey() {
        return XP_KEY;
    }

    // Config
    public void setupConfig() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    public void updateCommons() {
        lifespan = getConfig().getInt("lifespan", 300);
        storeItems = !getConfig().getString("itemTreatment", "default").equalsIgnoreCase("default");
        storeXp = !getConfig().getString("xpTreatment", "default").equalsIgnoreCase("default");

        // skinParts
        ConfigurationSection partsSec = getConfig().getConfigurationSection("skinParts");
        SkinParts.Mutable parts = SkinParts.allParts();

        if (partsSec != null) {
            parts.setCapeEnabled(partsSec.getBoolean("cape", true));
            parts.setJacketEnabled(partsSec.getBoolean("jacket", true));
            parts.setLeftSleeveEnabled(partsSec.getBoolean("leftSleeve", true));
            parts.setRightSleeveEnabled(partsSec.getBoolean("rightSleeve", true));
            parts.setLeftPantsEnabled(partsSec.getBoolean("leftPants", true));
            parts.setRightPantsEnabled(partsSec.getBoolean("rightPants", true));
            parts.setHatsEnabled(partsSec.getBoolean("hat", true));
        }

        skinParts = parts.immutableCopy();
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
            entity.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, location.getWorld().getGameTime() + ";" + lifespan * 20 + ";" + p.getUniqueId());
            entity.setGravity(getConfig().getBoolean("gravity"));
            entity.setInvulnerable(getConfig().getBoolean("invulnerable"));
            entity.setImmovable(getConfig().getBoolean("immovable"));
            entity.setMainHand(p.getMainHand());
            entity.setSkinParts(skinParts);

            if (getConfig().getBoolean("scaleAttribute")) {
                AttributeInstance pScale = p.getAttribute(Attribute.SCALE);
                AttributeInstance scale = entity.getAttribute(Attribute.SCALE);

                if (pScale != null && scale != null) scale.setBaseValue(pScale.getValue());
            }

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
        Location loc = mannequin.getLocation();
        if (loc.getWorld() == null) return;

        Location spawn = loc.add(0, .3, 0);

        if (npcInv != null && !npcInv.isEmpty()) {
            for (ItemStack item : npcInv.getContents()) {
                if (item == null) continue;

                loc.getWorld().dropItemNaturally(spawn, item);
            }

            mannequin.getEquipment().clear();
            npcInv.clear();
        }

        int xp = mannequin.getPersistentDataContainer().getOrDefault(XP_KEY, PersistentDataType.INTEGER, 0);
        if (xp > 0) {
            loc.getWorld().spawn(spawn, ExperienceOrb.class, orb -> orb.setExperience(xp)); // one big xp orb

            mannequin.getPersistentDataContainer().remove(XP_KEY);
        }
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
}
