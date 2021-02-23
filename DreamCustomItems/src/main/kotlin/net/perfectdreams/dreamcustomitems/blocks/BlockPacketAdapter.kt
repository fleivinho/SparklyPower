package net.perfectdreams.dreamcustomitems.blocks

import com.comphenix.packetwrapper.WrapperPlayServerBlockChange
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.wrappers.WrappedBlockData
import io.netty.buffer.Unpooled
import net.minecraft.server.v1_16_R3.Blocks
import net.perfectdreams.dreamcustomitems.DreamCustomItems
import net.perfectdreams.dreamcustomitems.utils.BlockPosition
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.craftbukkit.v1_16_R3.block.data.CraftBlockData
import us.myles.ViaVersion.api.minecraft.chunks.ChunkSection
import us.myles.ViaVersion.api.type.types.version.ChunkSectionType1_16

class BlockPacketAdapter(val m: DreamCustomItems) : PacketAdapter(
    m,
    ListenerPriority.NORMAL, // Listener priority
    PacketType.Play.Server.MAP_CHUNK,
    PacketType.Play.Server.BLOCK_CHANGE,
    PacketType.Play.Server.MULTI_BLOCK_CHANGE
) {
    override fun onPacketSending(event: PacketEvent) {
        // println(event.packetType)
        val packet = event.packet

        // I hope this is correct ;w; (concurrent API access oof)
        val playerWorld = event.player.world

        if (event.packetType == PacketType.Play.Server.MAP_CHUNK) {
            val chunkX = packet.integers.read(0)
            val chunkZ = packet.integers.read(1)
            val coordinateX = chunkX * 16
            val coordinateZ = chunkZ * 16

            // println("Current Chunk: $chunkX, $chunkZ")

            // https://launcher.mojang.com/v1/objects/41285beda6d251d190f2bf33beadd4fee187df7a/server.txt
            val availableSections = event.packet.integers.read(2)
            // println(availableSections)

            val chunkDataByteArray = event.packet.byteArrays.read(0)

            // Read sections
            val sections = arrayOfNulls<ChunkSection>(16)
            val buf = Unpooled.copiedBuffer(chunkDataByteArray)

            var requiresEdits = false

            for (i in 0..15) {
                if (availableSections and (1 shl i) == 0) continue  // Section not set
                val nonAirBlocksCount: Short = buf.readShort()
                val section = ChunkSectionType1_16().read(buf)
                section.nonAirBlocksCount = nonAirBlocksCount.toInt()
                sections[i] = section

                // Quick fail: Only edit if the palette contains note blocks
                // Empty palette = Global palette (I think?)
                if (section.palette.isNotEmpty() && !section.palette.any { net.minecraft.server.v1_16_R3.ChunkSection.GLOBAL_PALETTE.getObject(
                        it
                    )?.bukkitMaterial == Material.NOTE_BLOCK })
                    continue

                var hasNoteBlock = false

                // Replace all note blocks with default note block data
                for (y in 0 until 16) {
                    for (x in 0 until 16) {
                        for (z in 0 until 16) {
                            val blockId = section.getFlatBlock(x, y, z)

                            val blockData = net.minecraft.server.v1_16_R3.ChunkSection.GLOBAL_PALETTE.getObject(blockId)

                            if (blockData?.bukkitMaterial == Material.GOLD_BLOCK)
                                continue

                            if (blockData != null) {
                                if (blockData.bukkitMaterial == Material.NOTE_BLOCK) {
                                    // Okay, so it is a note block... but what if it is a *custom* block?
                                    val position = BlockPosition(
                                        coordinateX + x,
                                        y + (16 * i),
                                        coordinateZ + z
                                    )

                                    // println("Coordinate X: ${position.x}")
                                    // println("Coordinate Y: ${position.y}")
                                    // println("Coordinate Z: ${position.z}")

                                    val isCustomBlock = m.getCustomBlocksInWorld(playerWorld.name).contains(position)

                                    // If it is a custom block, leave the block as is :3
                                    if (isCustomBlock)
                                        continue

                                    section.setFlatBlock(
                                        x,
                                        y,
                                        z,
                                        net.minecraft.server.v1_16_R3.ChunkSection.GLOBAL_PALETTE.getOrCreateIdFor(
                                            Blocks.NOTE_BLOCK.blockData
                                        )
                                    )
                                    hasNoteBlock = true
                                }
                            }
                        }
                    }
                }

                if (!requiresEdits)
                    requiresEdits = hasNoteBlock
            }

            if (requiresEdits) {
                // Only rewrite the packet if we really need to edit the packet
                val byteBuf = Unpooled.buffer()
                sections.filterNotNull().forEach {
                    byteBuf.writeShort(it.nonAirBlocksCount)
                    ChunkSectionType1_16().write(byteBuf, it)
                }
                packet.byteArrays.write(0, byteBuf.array())
            }
        } else if (event.packetType == PacketType.Play.Server.BLOCK_CHANGE) {
            val wrapper = WrapperPlayServerBlockChange(event.packet)

            if (wrapper.blockData.type == Material.NOTE_BLOCK) {
                // So, we are changing a note block? Interesting...

                // If it is a custom block, just leave it as is :3
                if (m.getCustomBlocksInWorld(playerWorld.name).contains(
                        BlockPosition(
                            wrapper.location.x,
                            wrapper.location.y,
                            wrapper.location.z
                        )
                    )
                ) {
                    // println("Block Update is a custom block, we will not change it...")
                    return
                }

                // Oh no, NMS!!!
                wrapper.blockData = WrappedBlockData.createData(CraftBlockData.fromData(Blocks.NOTE_BLOCK.blockData))
            }

            event.packet = wrapper.handle
        } else if (event.packetType == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            // println("Multi block change")
            // This is the CHUNK SECTION POSITION
            val blockPosition = event.packet.sectionPositions.read(0)

            // println(blockPosition.x.toString() + ", " + blockPosition.y + ", " + blockPosition.z)
            val shorts = event.packet.shortArrays.read(0)

            /* println("Shorts:")
            shorts.forEach {
                val x = it.toInt() ushr 8 and 15
                val y = it.toInt() ushr 0 and 15
                val z = it.toInt() ushr 4 and 15
                println("$it $x, $y, $z")
            } */

            val changedBlocks = event.packet.blockDataArrays.read(0)

            // If there isn't any note blocks in this packet, let's ignore it
            if (changedBlocks.all { it.type != Material.NOTE_BLOCK })
                return

            // Sadly PacketWrapper isn't updated yet...
            val array = mutableListOf<WrappedBlockData>()

            // If there's any note blocks in this packet...
            changedBlocks.forEachIndexed { index, it ->
                // println(it.handle)
                // println(it.handle::class.java)

                if (it.type == Material.NOTE_BLOCK) {
                    val blockPositionRelativeToTheSection = shorts[index]

                    val x = (blockPositionRelativeToTheSection.toInt() ushr 8 and 15) + blockPosition.x
                    val y = (blockPositionRelativeToTheSection.toInt() ushr 0 and 15) + blockPosition.y
                    val z = (blockPositionRelativeToTheSection.toInt() ushr 4 and 15) + blockPosition.z

                    // println("Update coords: $x; $y; $z")

                    if (m.getCustomBlocksInWorld(playerWorld.name).contains(
                            BlockPosition(
                                x,
                                y,
                                z
                            )
                        )
                    ) {
                        // println("Multi Block Update is a custom block, we will keep it as is...")
                        array.add(it)
                        return
                    }

                    // Create a default note block
                    val craftBlockData = Bukkit.createBlockData(Material.NOTE_BLOCK) as CraftBlockData

                    // And add a wrapped block data!
                    array.add(WrappedBlockData.fromHandle(craftBlockData.state))
                } else {
                    // Keep as it if it isn't a note block
                    array.add(it)
                }
            }

            // And write the new array to the packet
            event.packet.blockDataArrays.write(0, array.toTypedArray())
        }
    }
}