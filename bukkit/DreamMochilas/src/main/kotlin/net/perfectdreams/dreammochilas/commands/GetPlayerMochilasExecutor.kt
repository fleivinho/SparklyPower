package net.perfectdreams.dreammochilas.commands

import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import net.perfectdreams.dreamcore.utils.Databases
import net.perfectdreams.dreamcore.utils.DreamUtils
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutorDeclaration
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.scheduler
import net.perfectdreams.dreammochilas.DreamMochilas
import net.perfectdreams.dreammochilas.commands.GetPlayerMochilasExecutor.Companion.Options.playerName
import net.perfectdreams.dreammochilas.commands.GetPlayerMochilasExecutor.Companion.Options.skip
import net.perfectdreams.dreammochilas.dao.Mochila
import net.perfectdreams.dreammochilas.tables.Mochilas
import org.bukkit.Bukkit
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class GetPlayerMochilasExecutor : SparklyCommandExecutor() {
    companion object : SparklyCommandExecutorDeclaration(GetPlayerMochilasExecutor::class) {
        object Options : CommandOptions() {
            val playerName = quotableString("player_name")
                .register()

            val skip = optionalInteger("skip")
                .register()
        }

        override val options = Options
    }

    override fun execute(context: CommandContext, args: CommandArguments) {
        val playerName = args[Options.playerName]
        val player = context.requirePlayer()
        val skip = args[Options.skip]

        scheduler().schedule(DreamMochilas.INSTANCE) {
            switchContext(SynchronizationContext.ASYNC)
            val uniqueId = DreamUtils.retrieveUserUniqueId(playerName)
            switchContext(SynchronizationContext.SYNC)

            context.sendMessage("§aCriando inventário com mochilas de $uniqueId")

            val mochilas = transaction(Databases.databaseNetwork) {
                Mochila.find {
                    Mochilas.owner eq uniqueId
                }.toMutableList()
            }

            val inventory = Bukkit.createInventory(null, 54)
            mochilas.drop(skip ?: 0).forEach {
                inventory.addItem(
                    it.createItem()
                )
            }

            player.openInventory(inventory)

            context.sendMessage("§7É possível pular entradas usando §6/mochila player $playerName QuantidadeDeMochilasParaPular")
        }
    }
}