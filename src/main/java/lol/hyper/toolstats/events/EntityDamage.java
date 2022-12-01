/*
 * This file is part of ToolStats.
 *
 * ToolStats is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ToolStats is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ToolStats.  If not, see <https://www.gnu.org/licenses/>.
 */

package lol.hyper.toolstats.events;

import lol.hyper.toolstats.ToolStats;
import lol.hyper.toolstats.tools.ItemChecker;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class EntityDamage implements Listener {

    private final ToolStats toolStats;
    public final Set<UUID> trackedMobs = new HashSet<>();
    private final List<EntityDamageEvent.DamageCause> ignoredCauses = Arrays.asList(EntityDamageEvent.DamageCause.SUICIDE, EntityDamageEvent.DamageCause.VOID, EntityDamageEvent.DamageCause.CUSTOM);

    public EntityDamage(ToolStats toolStats) {
        this.toolStats = toolStats;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        LivingEntity mobBeingAttacked = (LivingEntity) event.getEntity();

        // ignore void and /kill damage
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (ignoredCauses.contains(cause)) {
            return;
        }

        // mob is going to die
        if (mobBeingAttacked.getHealth() - event.getFinalDamage() <= 0) {
            // a player is killing something
            if (event.getDamager() instanceof Player) {
                Player attackingPlayer = (Player) event.getDamager();
                if (attackingPlayer.getGameMode() == GameMode.CREATIVE || attackingPlayer.getGameMode() == GameMode.SPECTATOR) {
                    return;
                }
                PlayerInventory attackingPlayerInventory = attackingPlayer.getInventory();
                int heldItemSlot = attackingPlayerInventory.getHeldItemSlot();
                ItemStack heldItem = attackingPlayerInventory.getItem(attackingPlayer.getInventory().getHeldItemSlot());
                // a player killed something with their fist
                if (heldItem == null || heldItem.getType() == Material.AIR) {
                    return;
                }
                // only check certain items
                if (!ItemChecker.isMeleeWeapon(heldItem.getType())) {
                    return;
                }
                // a player is killing another player
                if (mobBeingAttacked instanceof Player) {
                    Bukkit.getScheduler().runTaskLater(toolStats, () -> attackingPlayerInventory.setItem(heldItemSlot, updatePlayerKills(heldItem)), 1);
                    return;
                }
                // player is killing regular mob
                Bukkit.getScheduler().runTaskLater(toolStats, () -> attackingPlayerInventory.setItem(heldItemSlot, updateMobKills(heldItem)), 1);
                trackedMobs.add(mobBeingAttacked.getUniqueId());
            }
            // trident is being thrown at something
            if (event.getDamager() instanceof Trident) {
                Trident trident = (Trident) event.getDamager();
                ItemStack clone;
                // trident is killing player
                if (mobBeingAttacked instanceof Player) {
                    clone = updatePlayerKills(trident.getItem());
                } else {
                    clone = updateMobKills(trident.getItem());
                }
                if (clone == null) {
                    return;
                }
                Bukkit.getScheduler().runTaskLater(toolStats, () -> trident.setItem(clone), 1);
            }
            // arrow is being shot
            if (event.getDamager() instanceof Arrow) {
                Arrow arrow = (Arrow) event.getDamager();
                // if the shooter is a player
                if (arrow.getShooter() instanceof Player) {
                    Player shootingPlayer = (Player) arrow.getShooter();
                    if (shootingPlayer.getGameMode() == GameMode.CREATIVE || shootingPlayer.getGameMode() == GameMode.SPECTATOR) {
                        return;
                    }
                    PlayerInventory shootingPlayerInventory = shootingPlayer.getInventory();
                    int heldItemSlot = shootingPlayerInventory.getHeldItemSlot();
                    ItemStack heldItem = shootingPlayerInventory.getItem(heldItemSlot);
                    if (heldItem == null) {
                        return;
                    }
                    // if the player is holding the bow/crossbow
                    // if they switch then oh well
                    if (heldItem.getType() == Material.BOW || heldItem.getType() == Material.CROSSBOW) {
                        if (mobBeingAttacked instanceof Player) {
                            Bukkit.getScheduler().runTaskLater(toolStats, () -> shootingPlayerInventory.setItem(heldItemSlot, updatePlayerKills(heldItem)), 1);
                        } else {
                            Bukkit.getScheduler().runTaskLater(toolStats, () -> shootingPlayerInventory.setItem(heldItemSlot, updateMobKills(heldItem)), 1);
                        }
                    }
                }
            }
        }
        // player is taken damage but not being killed
        if (mobBeingAttacked instanceof Player) {
            Player playerTakingDamage = (Player) mobBeingAttacked;
            if (playerTakingDamage.getGameMode() == GameMode.CREATIVE || playerTakingDamage.getGameMode() == GameMode.SPECTATOR) {
                return;
            }
            PlayerInventory inventory = playerTakingDamage.getInventory();
            for (ItemStack armorPiece : inventory.getArmorContents()) {
                if (armorPiece != null) {
                    if (ItemChecker.isArmor(armorPiece.getType())) {
                        Bukkit.getScheduler().runTaskLater(toolStats, () -> updateArmorDamage(armorPiece, event.getFinalDamage()), 1);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        // ignore void and /kill damage
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (ignoredCauses.contains(cause)) {
            return;
        }

        LivingEntity mobBeingAttacked = (LivingEntity) event.getEntity();
        // player is taken damage but not being killed
        if (mobBeingAttacked instanceof Player) {
            Player playerTakingDamage = (Player) mobBeingAttacked;
            if (playerTakingDamage.getGameMode() == GameMode.CREATIVE || playerTakingDamage.getGameMode() == GameMode.SPECTATOR) {
                return;
            }
            PlayerInventory inventory = playerTakingDamage.getInventory();
            for (ItemStack armorPiece : inventory.getArmorContents()) {
                if (armorPiece != null) {
                    if (ItemChecker.isArmor(armorPiece.getType())) {
                        Bukkit.getScheduler().runTaskLater(toolStats, () -> updateArmorDamage(armorPiece, event.getFinalDamage()), 1);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByBlockEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        // ignore void and /kill damage
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (ignoredCauses.contains(cause)) {
            return;
        }

        LivingEntity mobBeingAttacked = (LivingEntity) event.getEntity();
        // player is taken damage but not being killed
        if (mobBeingAttacked instanceof Player) {
            Player playerTakingDamage = (Player) mobBeingAttacked;
            if (playerTakingDamage.getGameMode() == GameMode.CREATIVE || playerTakingDamage.getGameMode() == GameMode.SPECTATOR) {
                return;
            }
            PlayerInventory inventory = playerTakingDamage.getInventory();
            for (ItemStack armorPiece : inventory.getArmorContents()) {
                if (armorPiece != null) {
                    if (ItemChecker.isArmor(armorPiece.getType())) {
                        Bukkit.getScheduler().runTaskLater(toolStats, () -> updateArmorDamage(armorPiece, event.getFinalDamage()), 1);
                    }
                }
            }
        }
    }

    /**
     * Updates a weapon's player kills.
     *
     * @param itemStack The item to update.
     * @return A copy of the item.
     */
    private ItemStack updatePlayerKills(ItemStack itemStack) {
        ItemStack finalItem = itemStack.clone();
        ItemMeta meta = finalItem.getItemMeta();
        if (meta == null) {
            toolStats.logger.warning(itemStack + " does NOT have any meta! Unable to update stats.");
            return null;
        }
        Integer playerKills = 0;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(toolStats.swordPlayerKills, PersistentDataType.INTEGER)) {
            playerKills = container.get(toolStats.swordPlayerKills, PersistentDataType.INTEGER);
        }

        if (playerKills == null) {
            playerKills = 0;
            toolStats.logger.warning(itemStack + " does not have valid player-kills set! Resting to zero. This should NEVER happen.");
        }

        playerKills++;
        container.set(toolStats.swordPlayerKills, PersistentDataType.INTEGER, playerKills);

        String playerKillsLore = toolStats.getLoreFromConfig("kills.player", false);
        String playerKillsLoreRaw = toolStats.getLoreFromConfig("kills.player", true);

        if (playerKillsLore == null || playerKillsLoreRaw == null) {
            toolStats.logger.warning("There is no lore message for messages.kills.player!");
            return null;
        }

        List<String> lore;
        String newLine = playerKillsLoreRaw.replace("{kills}", toolStats.numberFormat.formatInt(playerKills));
        if (meta.hasLore()) {
            lore = meta.getLore();
            boolean hasLore = false;
            // we do a for loop like this, we can keep track of index
            // this doesn't mess the lore up of existing items
            for (int x = 0; x < lore.size(); x++) {
                if (lore.get(x).contains(playerKillsLore)) {
                    hasLore = true;
                    lore.set(x, newLine);
                    break;
                }
            }
            // if the item has lore but doesn't have the tag, add it
            if (!hasLore) {
                lore.add(newLine);
            }
        } else {
            // if the item has no lore, create a new list and add the string
            lore = new ArrayList<>();
            lore.add(newLine);
        }
        // do we add the lore based on the config?
        if (toolStats.checkConfig(itemStack, "player-kills")) {
            meta.setLore(lore);
        }
        finalItem.setItemMeta(meta);
        return finalItem;
    }

    /**
     * Updates a weapon's mob kills.
     *
     * @param itemStack The item to update.
     * @return A copy of the item.
     */
    private ItemStack updateMobKills(ItemStack itemStack) {
        ItemStack finalItem = itemStack.clone();
        ItemMeta meta = finalItem.getItemMeta();
        if (meta == null) {
            toolStats.logger.warning(itemStack + " does NOT have any meta! Unable to update stats.");
            return null;
        }
        Integer mobKills = 0;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(toolStats.swordMobKills, PersistentDataType.INTEGER)) {
            mobKills = container.get(toolStats.swordMobKills, PersistentDataType.INTEGER);
        }

        if (mobKills == null) {
            mobKills = 0;
            toolStats.logger.warning(itemStack + " does not have valid mob-kills set! Resting to zero. This should NEVER happen.");
        }

        mobKills++;
        container.set(toolStats.swordMobKills, PersistentDataType.INTEGER, mobKills);

        String mobKillsLore = toolStats.getLoreFromConfig("kills.mob", false);
        String mobKillsLoreRaw = toolStats.getLoreFromConfig("kills.mob", true);

        if (mobKillsLore == null || mobKillsLoreRaw == null) {
            toolStats.logger.warning("There is no lore message for messages.kills.mob!");
            return null;
        }

        List<String> lore;
        String newLine = mobKillsLoreRaw.replace("{kills}", toolStats.numberFormat.formatInt(mobKills));
        if (meta.hasLore()) {
            lore = meta.getLore();
            boolean hasLore = false;
            // we do a for loop like this, we can keep track of index
            // this doesn't mess the lore up of existing items
            for (int x = 0; x < lore.size(); x++) {
                if (lore.get(x).contains(mobKillsLore)) {
                    hasLore = true;
                    lore.set(x, newLine);
                    break;
                }
            }
            // if the item has lore but doesn't have the tag, add it
            if (!hasLore) {
                lore.add(newLine);
            }
        } else {
            // if the item has no lore, create a new list and add the string
            lore = new ArrayList<>();
            lore.add(newLine);
        }
        // do we add the lore based on the config?
        if (toolStats.checkConfig(itemStack, "mob-kills")) {
            meta.setLore(lore);
        }
        finalItem.setItemMeta(meta);
        return finalItem;
    }

    /**
     * Updates a player's armor damage stats.
     *
     * @param itemStack The armor piece.
     * @param damage    How much damage is being added.
     */
    private void updateArmorDamage(ItemStack itemStack, double damage) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            toolStats.logger.warning(itemStack + " does NOT have any meta! Unable to update stats.");
            return;
        }
        Double damageTaken = 0.0;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(toolStats.armorDamage, PersistentDataType.DOUBLE)) {
            damageTaken = container.get(toolStats.armorDamage, PersistentDataType.DOUBLE);
        }

        if (damageTaken == null) {
            damageTaken = 0.0;
            toolStats.logger.warning(itemStack + " does not have valid damage-taken set! Resting to zero. This should NEVER happen.");
        }

        damageTaken = damageTaken + damage;
        container.set(toolStats.armorDamage, PersistentDataType.DOUBLE, damageTaken);

        String damageTakenLore = toolStats.getLoreFromConfig("damage-taken", false);
        String damageTakenLoreRaw = toolStats.getLoreFromConfig("damage-taken", true);

        if (damageTakenLore == null || damageTakenLoreRaw == null) {
            toolStats.logger.warning("There is no lore message for messages.damage-taken!");
            return;
        }

        List<String> lore;
        String newLine = damageTakenLoreRaw.replace("{damage}", toolStats.numberFormat.formatDouble(damageTaken));
        if (meta.hasLore()) {
            lore = meta.getLore();
            boolean hasLore = false;
            // we do a for loop like this, we can keep track of index
            // this doesn't mess the lore up of existing items
            for (int x = 0; x < lore.size(); x++) {
                if (lore.get(x).contains(damageTakenLore)) {
                    hasLore = true;
                    lore.set(x, newLine);
                    break;
                }
            }
            // if the item has lore but doesn't have the tag, add it
            if (!hasLore) {
                lore.add(newLine);
            }
        } else {
            // if the item has no lore, create a new list and add the string
            lore = new ArrayList<>();
            lore.add(newLine);
        }
        if (toolStats.config.getBoolean("enabled.armor-damage")) {
            meta.setLore(lore);
        }
        itemStack.setItemMeta(meta);
    }
}
