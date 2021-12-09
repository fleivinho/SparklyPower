package net.perfectdreams.dreammochilas.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.md_5.bungee.api.ChatColor
import net.perfectdreams.dreamcore.utils.Databases
import net.perfectdreams.dreamcore.utils.DreamUtils
import net.perfectdreams.dreamcore.utils.toBase64
import net.perfectdreams.dreammochilas.dao.Mochila
import net.perfectdreams.dreammochilas.tables.Mochilas
import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.transactions.transaction
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

object MochilaUtils {
    val loadedMochilas = ConcurrentHashMap<Long, Mochila>()
    val plugin = Bukkit.getPluginManager().getPlugin("DreamMochilas")!!
    val mochilaLoadSaveMutex = Mutex()
    val mochilaCreationMutex = Mutex()

    suspend fun interactWithMochila(itemStack: ItemStack, mochilaId: Long, triggerType: String?, doesntExistBlock: () -> (Unit), block: suspend (Mochila) -> (Unit)) {
        val mochila = retrieveMochila(mochilaId, triggerType)
        if (mochila == null) {
            doesntExistBlock.invoke()
            return
        }

        mochila.lock()
        try {
            block.invoke(mochila)
        } finally {
            mochila.unlock()
            saveMochila(itemStack, mochila, triggerType)
        }
    }

    /**
     * Retrieves the mochila from the database or, if it is loaded in memory, gets the already loaded mochila
     *
     * @param mochilaId the mochila ID
     * @return the mochila object
     */
    suspend fun retrieveMochila(mochilaId: Long, triggerType: String? = null): Mochila? {
        DreamUtils.assertAsyncThread(true)

        plugin.logger.info { "Loading backpack $mochilaId, triggered by $triggerType; Is mutex locked? ${mochilaLoadSaveMutex.isLocked}" }

        mochilaLoadSaveMutex.withLock {
            // Load from memory if it exists
            val memoryMochila = loadedMochilas[mochilaId]

            if (memoryMochila != null) {
                plugin.logger.info { "Loaded backpack $mochilaId ($memoryMochila) from memory! Triggered by $triggerType" }
                return memoryMochila
            }

            val mochila = transaction(Databases.databaseNetwork) {
                Mochila.find { Mochilas.id eq mochilaId }
                    .firstOrNull()
            }

            if (mochila != null) {
                plugin.logger.info { "Loaded backpack $mochilaId ($mochila) from database! Triggered by $triggerType" }
                loadedMochilas[mochilaId] = mochila
            } else {
                plugin.logger.info { "Tried loading backpack $mochilaId ($mochila) from database, but it doesn't exist! Triggered by $triggerType" }
            }

            return mochila
        }
    }

    /**
     * Saves the mochila to the database, but ONLY if the mochila inventory doesn't have any viewers AND mochilaInventoryManipulationLock is not locked
     *
     * @param mochilaItem the mochila item, used for the slot size in the lore, if null, the lore won't be updated
     * @param mochila     the mochila object
     */
    suspend fun saveMochila(mochilaItem: ItemStack?, mochila: Mochila, triggerType: String? = null) {
        DreamUtils.assertAsyncThread(true)

        val cachedInventory = mochila.cachedInventory
        if (cachedInventory == null) {
            plugin.logger.info { "Not going to save backpack ${mochila.id.value} ($mochila) on database because there isn't a cached inventory present, so its content weren't modified! Triggered by $triggerType" }
            return
        }

        val viewerCount = cachedInventory.viewers.size
        val isManipulationLocked = mochila.mochilaInventoryManipulationLock.isLocked

        plugin.logger.info { "Saving backpack ${mochila.id.value} ($mochila), triggered by $triggerType; Is mutex locked? ${mochilaLoadSaveMutex.isLocked}; Viewer Count: $viewerCount; Is Manipulation Locked? $isManipulationLocked" }

        if (viewerCount > 1) {
            plugin.logger.info { "Not going to save backpack ${mochila.id.value} ($mochila) on database because there's $viewerCount looking at it! Triggered by $triggerType" }
            return
        }

        if (isManipulationLocked) {
            plugin.logger.info { "Not going to save backpack ${mochila.id.value} ($mochila) on database because it is locked for manipulation! Triggered by $triggerType" }
            return
        }

        // Unless if we are running this in the event itself (impossible because needs to be in a async task), this will be 0
        if (0 == viewerCount && !isManipulationLocked) {
            mochilaLoadSaveMutex.withLock {
                // Save ONLY if there's less (or equal to) one viewer
                // The reason there's a 1 >= check is because on InventoryCloseEvent the inventory is not closed yet

                plugin.logger.info { "Saving backpack ${mochila.id.value} ($mochila) on database! Triggered by $triggerType" }

                transaction(Databases.databaseNetwork) {
                    mochila.content = cachedInventory.toBase64(1)
                }

                if (mochilaItem != null)
                    updateMochilaItemLore(cachedInventory, mochilaItem)

                plugin.logger.info { "Saved backpack ${mochila.id.value} ($mochila) on database! Triggered by $triggerType" }

                // Remove from memory
                plugin.logger.info { "Removing backpack ${mochila.id.value} ($mochila) from memory, triggered by $triggerType" }
                removeCachedMochilaWithinLock(mochila, triggerType)
            }
        } else {
            plugin.logger.info { "Not going to save backpack ${mochila.id.value} ($mochila) on database! Triggered by $triggerType" }
        }
    }

    suspend fun removeCachedMochila(mochila: Mochila, triggerType: String? = null) {
        mochilaLoadSaveMutex.withLock {
            removeCachedMochilaWithinLock(mochila, "$triggerType (no db save)")
        }
    }

    private suspend fun removeCachedMochilaWithinLock(mochila: Mochila, triggerType: String? = null) {
        val count = mochila.getLockCount()

        if (count == 0) {
            plugin.logger.info { "Removing backpack ${mochila.id.value} ($mochila) from memory, triggered by $triggerType; Is mutex locked? ${mochilaLoadSaveMutex.isLocked}" }
            loadedMochilas.remove(mochila.id.value)
        } else {
            plugin.logger.info { "Not removing backpack ${mochila.id.value} ($mochila) from memory because there is still $count locks on it! Triggered by $triggerType; Is mutex locked? ${mochilaLoadSaveMutex.isLocked}" }
        }
    }

    suspend fun storeCachedMochila(mochila: Mochila) {
        mochilaLoadSaveMutex.withLock {
            loadedMochilas[mochila.id.value] = mochila
        }
    }

    /**
     * Updates the [mochilaItem] metadata
     */
    private fun updateMochilaItemLore(inventory: Inventory, mochilaItem: ItemStack) {
        val currentLore = mochilaItem.lore

        // Only update if the lore exists... it should always exist
        if (currentLore?.isNotEmpty() == true) {
            val lastLineOfTheLore = currentLore.last()
            val newLore = currentLore.toMutableList()

            if (!lastLineOfTheLore.contains("slots"))
                newLore.add("\n")
            else
                newLore.removeLast()

            var usedSize = inventory.count { it != null } // Count non empty slots
            if (usedSize == -1)
                usedSize = inventory.size

            // Do a nice transition from green to red, depending on how many slots are used
            val totalSizeInPercentage = usedSize / inventory.size.toDouble()
            val r = 0 + (200 * totalSizeInPercentage).toInt()
            val g = 255 - (255 * totalSizeInPercentage).toInt()
            val b = 125 - (125 * totalSizeInPercentage).toInt()
            val colorToBeUsed = ChatColor.of(Color(r, g, b))

            newLore.add(
                buildString {
                    append("$colorToBeUsed$usedSize/${inventory.size} §7slots usados")
                    if (usedSize == inventory.size) {
                        append(" §c§lCHEIA!")
                    }
                }
            )

            mochilaItem.lore = newLore
        }
    }
}