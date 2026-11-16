package dex.cosmetics

import dex.cosmetics.cosmetic.CosmeticType
import dex.cosmetics.data.SqlitePlayerDataStorage
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SqliteTransactionTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `revoke rolls back ownership if clearing equipped state fails`() {
        SqlitePlayerDataStorage(directory).use { storage ->
            storage.open()
            val uuid = UUID.randomUUID()
            storage.grant(uuid, "hat")
            storage.setEquipped(uuid, CosmeticType.HAT, "hat")
            DriverManager.getConnection("jdbc:sqlite:${directory.resolve("cosmetics.db")}").use {
                conn ->
                conn.createStatement().use {
                    it.execute(
                        "CREATE TRIGGER reject_equipped_delete BEFORE DELETE ON player_equipped BEGIN SELECT RAISE(ABORT, 'simulated failure'); END"
                    )
                }
            }
            assertThrows(SQLException::class.java) { storage.revoke(uuid, "hat") }
            assertEquals(setOf("hat"), storage.loadOwned(uuid))
            assertEquals("hat", storage.loadEquipped(uuid)[CosmeticType.HAT])
        }
    }
}
