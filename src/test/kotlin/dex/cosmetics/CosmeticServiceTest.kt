package dex.cosmetics

import dex.cosmetics.cosmetic.CosmeticRegistry
import dex.cosmetics.cosmetic.CosmeticService
import dex.cosmetics.cosmetic.CosmeticType
import dex.cosmetics.cosmetic.types.HatCosmetic
import dex.cosmetics.cosmetic.types.ItemSkinCosmetic
import dex.cosmetics.data.PlayerDataManager
import dex.cosmetics.data.PlayerDataStorage
import dex.cosmetics.data.SqlitePlayerDataStorage
import dex.cosmetics.polymer.AppliedSkinComponent
import java.nio.file.Path
import java.sql.SQLException
import java.util.UUID
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Identifier
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CosmeticServiceTest : MinecraftTest() {
    @TempDir lateinit var directory: Path
    private val uuid = UUID.randomUUID()
    private val hat =
        HatCosmetic(
            "hat",
            "Hat",
            emptyList(),
            Identifier.of("test:hat"),
            Identifier.of("test:hat"),
            Identifier.of("minecraft:item/generated"),
            Vector3f(),
            Vector3f(1f),
        )
    private val skin =
        ItemSkinCosmetic(
            "skin",
            "Skin",
            emptyList(),
            Identifier.of("test:skin"),
            Identifier.of("test:skin"),
            Identifier.of("minecraft:item/handheld"),
            setOf(Identifier.of("minecraft:diamond_sword")),
            emptySet(),
        )

    private class FailingStorage(private val delegate: PlayerDataStorage) :
        PlayerDataStorage by delegate {
        var fail = false

        override fun grant(uuid: UUID, cosmeticId: String) {
            if (fail) throw SQLException("simulated disk failure")
            delegate.grant(uuid, cosmeticId)
        }

        override fun revoke(uuid: UUID, cosmeticId: String) {
            if (fail) throw SQLException("simulated disk failure")
            delegate.revoke(uuid, cosmeticId)
        }

        override fun setEquipped(uuid: UUID, type: CosmeticType, cosmeticId: String?) {
            if (fail) throw SQLException("simulated disk failure")
            delegate.setEquipped(uuid, type, cosmeticId)
        }
    }

    @Test
    fun `revoking an equipped cosmetic refreshes rendering and survives reconnect`() {
        SqlitePlayerDataStorage(directory).use { storage ->
            storage.open()
            val data = PlayerDataManager(storage)
            val registry = CosmeticRegistry().apply { replaceAll(listOf(hat)) }
            val refreshes = mutableListOf<UUID>()
            val service = CosmeticService(registry, data, refreshes::add)
            assertTrue(service.grant(uuid, hat.id))
            service.equip(uuid, CosmeticType.HAT, hat.id)
            refreshes.clear()
            assertTrue(service.revoke(uuid, hat.id))
            assertEquals(listOf(uuid), refreshes)
            assertNull(data.get(uuid)!!.equipped[CosmeticType.HAT])
            data.onQuit(uuid)
            assertFalse(data.getOrLoad(uuid).owns(hat.id))
            assertNull(data.get(uuid)!!.equipped[CosmeticType.HAT])
        }
    }

    @Test
    fun `skin application rechecks ownership after a menu was opened`() {
        SqlitePlayerDataStorage(directory).use { storage ->
            storage.open()
            val data = PlayerDataManager(storage)
            val registry = CosmeticRegistry().apply { replaceAll(listOf(skin)) }
            val service = CosmeticService(registry, data) {}
            service.grant(uuid, skin.id)
            val capturedCosmetic = registry.get(skin.id)!!
            val stack = ItemStack(Items.DIAMOND_SWORD)
            service.revoke(uuid, skin.id)
            assertThrows(IllegalArgumentException::class.java) {
                service.applySkin(uuid, capturedCosmetic.id, stack)
            }
            assertNull(AppliedSkinComponent.read(stack))
            service.grant(uuid, skin.id)
            service.applySkin(uuid, skin.id, stack)
            assertEquals(skin.id, AppliedSkinComponent.read(stack))
        }
    }

    @Test
    fun `failed and partial pickups preserve the skin on ground remainders`() {
        SqlitePlayerDataStorage(directory).use { storage ->
            storage.open()
            val service = CosmeticService(CosmeticRegistry(), PlayerDataManager(storage)) {}
            val ground = ItemStack(Items.STONE, 16)
            AppliedSkinComponent.apply(ground, skin.id)
            assertFalse(
                service.insertPickedUpStack(uuid, ground, false) { candidate ->
                    assertNull(AppliedSkinComponent.read(candidate))
                    false
                }
            )
            assertEquals(16, ground.count)
            assertEquals(skin.id, AppliedSkinComponent.read(ground))
            assertTrue(
                service.insertPickedUpStack(uuid, ground, false) { candidate ->
                    assertNull(AppliedSkinComponent.read(candidate))
                    candidate.decrement(5)
                    true
                }
            )
            assertEquals(11, ground.count)
            assertEquals(skin.id, AppliedSkinComponent.read(ground))
            assertTrue(
                service.insertPickedUpStack(uuid, ground, false) { candidate ->
                    assertNull(AppliedSkinComponent.read(candidate))
                    candidate.count = 0
                    true
                }
            )
            assertTrue(ground.isEmpty)
        }
    }

    @Test
    fun `owners and sharing mode keep skins during pickup`() {
        SqlitePlayerDataStorage(directory).use { storage ->
            storage.open()
            val registry = CosmeticRegistry().apply { replaceAll(listOf(skin)) }
            val service = CosmeticService(registry, PlayerDataManager(storage)) {}
            val ground = ItemStack(Items.DIAMOND_SWORD)
            AppliedSkinComponent.apply(ground, skin.id)
            assertTrue(
                service.insertPickedUpStack(uuid, ground, true) { candidate ->
                    assertSame(ground, candidate)
                    assertEquals(skin.id, AppliedSkinComponent.read(candidate))
                    true
                }
            )
            service.grant(uuid, skin.id)
            assertTrue(
                service.insertPickedUpStack(uuid, ground, false) { candidate ->
                    assertSame(ground, candidate)
                    assertEquals(skin.id, AppliedSkinComponent.read(candidate))
                    true
                }
            )
        }
    }

    @Test
    fun `failed writes leave ownership equipment and rendering unchanged and can be retried`() {
        SqlitePlayerDataStorage(directory).use { sql ->
            sql.open()
            val storage = FailingStorage(sql)
            val data = PlayerDataManager(storage)
            val registry = CosmeticRegistry().apply { replaceAll(listOf(hat)) }
            var refreshes = 0
            val service = CosmeticService(registry, data) { refreshes++ }
            storage.fail = true
            assertThrows(SQLException::class.java) { service.grant(uuid, hat.id) }
            assertFalse(data.getOrLoad(uuid).owns(hat.id))
            storage.fail = false
            service.grant(uuid, hat.id)
            storage.fail = true
            assertThrows(SQLException::class.java) { service.equip(uuid, CosmeticType.HAT, hat.id) }
            assertNull(data.get(uuid)!!.equipped[CosmeticType.HAT])
            storage.fail = false
            service.equip(uuid, CosmeticType.HAT, hat.id)
            storage.fail = true
            val previousRefreshes = refreshes
            assertThrows(SQLException::class.java) { service.revoke(uuid, hat.id) }
            assertTrue(data.get(uuid)!!.owns(hat.id))
            assertEquals(hat.id, data.get(uuid)!!.equipped[CosmeticType.HAT])
            assertEquals(previousRefreshes, refreshes)
            storage.fail = false
            assertTrue(service.revoke(uuid, hat.id))
        }
    }

    @Test
    fun `successful writes are durable before quitting or shutdown`() {
        val storage = SqlitePlayerDataStorage(directory).apply { open() }
        val data = PlayerDataManager(storage)
        val registry = CosmeticRegistry().apply { replaceAll(listOf(hat)) }
        val service = CosmeticService(registry, data) {}
        service.grant(uuid, hat.id)
        service.equip(uuid, CosmeticType.HAT, hat.id)
        data.onQuit(uuid)
        assertEquals(hat.id, data.getOrLoad(uuid).equipped[CosmeticType.HAT])
        data.shutdown()
        SqlitePlayerDataStorage(directory).use { reopened ->
            reopened.open()
            assertEquals(setOf(hat.id), reopened.loadOwned(uuid))
            assertEquals(hat.id, reopened.loadEquipped(uuid)[CosmeticType.HAT])
        }
    }

    @Test
    fun `reconcile removes obsolete and wrong-type equipment but retains ownership`() {
        SqlitePlayerDataStorage(directory).use { storage ->
            storage.open()
            val data = PlayerDataManager(storage)
            val registry = CosmeticRegistry().apply { replaceAll(listOf(hat)) }
            val service = CosmeticService(registry, data) {}
            service.grant(uuid, hat.id)
            service.equip(uuid, CosmeticType.HAT, hat.id)
            registry.replaceAll(emptyList())
            service.reconcile(uuid)
            assertTrue(data.get(uuid)!!.owns(hat.id))
            assertNull(data.get(uuid)!!.equipped[CosmeticType.HAT])
        }
    }
}
