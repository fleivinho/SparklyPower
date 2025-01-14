package net.perfectdreams.dreamcorreios.tables

import net.perfectdreams.dreamcore.DreamCore
import org.jetbrains.exposed.dao.id.UUIDTable

object ContaCorreios : UUIDTable() {
    override val tableName: String
        get() = "${DreamCore.dreamConfig.getTablePrefix()}_contacorreios"

    val items = text("items")
}