package net.perfectdreams.dreamchat.tables

import net.perfectdreams.dreamcore.DreamCore
import org.jetbrains.exposed.dao.id.IntIdTable

object EventMessages : IntIdTable() {
	override val tableName: String
		get() = "${DreamCore.dreamConfig.getTablePrefix()}_eventmessages"

	val message = text("message")
	val lastWinner = uuid("last_winner").nullable()
	val bestWinner = uuid("best_winner").nullable()
	val timeElapsed = long("time_elapsed").nullable()
}