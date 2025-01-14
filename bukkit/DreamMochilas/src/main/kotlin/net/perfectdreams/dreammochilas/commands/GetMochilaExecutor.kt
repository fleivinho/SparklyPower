package net.perfectdreams.dreammochilas.commands

import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutorDeclaration
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreammochilas.DreamMochilas
import net.perfectdreams.dreammochilas.utils.MochilaData

class GetMochilaExecutor : SparklyCommandExecutor() {
    companion object : SparklyCommandExecutorDeclaration(GetMochilaExecutor::class) {
        object Options : CommandOptions() {
            val mochilaName = greedyString("mochila_name")
                .register()
        }

        override val options = Options
    }

    override fun execute(context: CommandContext, args: CommandArguments) {
        val player = context.requirePlayer()

        val mochilaName = args[Options.mochilaName]

        val mochilaData = MochilaData.list.firstOrNull { it.name.equals(mochilaName, true) }
            ?: context.fail("§cMochila desconhecida")

        val item = DreamMochilas.createMochila(mochilaData)

        player.inventory.addItem(item)
        context.sendMessage("Prontinho patrão, usando mochila data $mochilaData")
    }
}