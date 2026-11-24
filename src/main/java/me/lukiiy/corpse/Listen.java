package me.lukiiy.corpse;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import me.lukiiy.manneInventory.MannequinInventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
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

import java.util.Arrays;
import java.util.Objects;

public class Listen implements Listener {
    private final Corpse plugin;

    public Listen(Corpse plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void death(PlayerDeathEvent e) {
        Player p = e.getEntity();

        if ((p.getGameMode().isInvulnerable() || p.isInvulnerable()) && plugin.getConfig().getBoolean("ignoreInvulnerability")) return;
        if (plugin.getConfig().getBoolean("ignoreEmptyInventories") && p.getInventory().isEmpty() && e.getDroppedExp() == 0) return;

        Mannequin npc = plugin.makeCorpse(e.getEntity());
        if (npc == null) return;

        if (plugin.storeItems) {
            e.getDrops().clear();

            PlayerInventory inv = p.getInventory();
            EntityEquipment equip = npc.getEquipment();

            equip.setHelmet(inv.getHelmet(), false);
            equip.setChestplate(inv.getChestplate(), false);
            equip.setLeggings(inv.getLeggings(), false);
            equip.setBoots(inv.getBoots(), false);
            equip.setItemInMainHand(inv.getItemInMainHand(), false);
            equip.setItemInOffHand(inv.getItemInOffHand(), false);

            Inventory inventory = Bukkit.createInventory(null, 54);
            inventory.setContents(Arrays.stream(inv.getContents()).filter(Objects::nonNull).toArray(ItemStack[]::new));

            MannequinInventoryManager.set(npc, inventory);
        }

        if (plugin.storeXp) {
            e.setShouldDropExperience(false);

            npc.getPersistentDataContainer().set(Corpse.getXpKey(), PersistentDataType.INTEGER, e.getDroppedExp());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void damage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Mannequin npc) || !npc.getPersistentDataContainer().has(Corpse.getKey()) || plugin.getConfig().getBoolean("onlyAcceptEntityDamage")) return;

        if (e.getDamageSource().getCausingEntity() == null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void npcDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Mannequin npc) || !npc.getPersistentDataContainer().has(Corpse.getKey())) return;

        plugin.popCorpseData(npc);
    }

    @EventHandler
    public void interaction(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Mannequin npc) || !npc.getPersistentDataContainer().has(Corpse.getKey()) || !plugin.getConfig().getString("itemTreatment", "").equalsIgnoreCase("pop")) return;

        Player p = e.getPlayer();

        p.playSound(npc.getLocation(), plugin.getConfig().getString("interactSfx.id", "block.chest.open"), (float) plugin.getConfig().getDouble("interactSfx.volume", 1.0), (float) plugin.getConfig().getDouble("interactSfx.pitch", 1.0));
        plugin.popCorpseData(npc);
        npc.getScheduler().run(plugin, (t) -> npc.setHealth(0), null);
    }

    @EventHandler
    public void worldEntityAdd(EntityAddToWorldEvent e) {
        if (!(e.getEntity() instanceof Mannequin npc)) return;

        if (npc.getPersistentDataContainer().has(Corpse.getKey(), PersistentDataType.STRING)) {
            if (plugin.tracked.contains(npc)) return;

            plugin.tracked.add(npc);

            if (plugin.lifespan < 1) return;

            npc.getScheduler().runDelayed(plugin, task -> {
                if (!npc.isValid() || npc.isDead()) {
                    plugin.tracked.remove(npc);
                    return;
                }

                plugin.popCorpseData(npc);
                plugin.tracked.remove(npc);
                npc.remove();
            }, null, plugin.lifespan * 20L);
        }
    }

    @EventHandler
    public void worldEntityRemove(EntityRemoveFromWorldEvent e) {
        if (!(e.getEntity() instanceof Mannequin npc)) return;

        if (npc.getPersistentDataContainer().has(Corpse.getKey(), PersistentDataType.STRING)) plugin.tracked.remove(npc);
    }
}
