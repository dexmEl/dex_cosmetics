package dex.cosmetics.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dex.cosmetics.DexCosmetics
import dex.cosmetics.cosmetic.CosmeticType
import java.nio.file.Path
import java.sql.Connection
import java.util.EnumMap
import java.util.UUID
import kotlin.io.path.createDirectories

class SqlitePlayerDataStorage(private val directory: Path) : PlayerDataStorage {
    private lateinit var dataSource: HikariDataSource

    override fun open() {
        val dir = directory
        dir.createDirectories()
        val dbFile = dir.resolve("cosmetics.db").toAbsolutePath().toString()

        val config =
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:$dbFile"
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 1
                minimumIdle = 1
                connectionTimeout = 2000
                connectionInitSql = "PRAGMA busy_timeout=1000"
                poolName = "DexCosmetics-SQLite"
                connectionTestQuery = "SELECT 1"
            }
        dataSource = HikariDataSource(config)

        try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { st ->
                    st.execute("PRAGMA journal_mode=WAL")
                    st.execute("PRAGMA foreign_keys=ON")
                    st.execute(
                        """
                        CREATE TABLE IF NOT EXISTS player_owned (
                            uuid TEXT NOT NULL,
                            cosmetic_id TEXT NOT NULL,
                            granted_at INTEGER NOT NULL DEFAULT (strftime('%s','now')),
                            PRIMARY KEY (uuid, cosmetic_id)
                        )
                        """
                            .trimIndent()
                    )
                    st.execute(
                        """
                        CREATE TABLE IF NOT EXISTS player_equipped (
                            uuid TEXT NOT NULL,
                            type TEXT NOT NULL,
                            cosmetic_id TEXT NOT NULL,
                            PRIMARY KEY (uuid, type)
                        )
                        """
                            .trimIndent()
                    )
                    st.execute(
                        "CREATE INDEX IF NOT EXISTS idx_owned_cosmetic ON player_owned(cosmetic_id)"
                    )
                }
            }
        } catch (e: Exception) {
            dataSource.close()
            throw e
        }
        DexCosmetics.LOGGER.info("Opened SQLite player data store at $dbFile")
    }

    override fun loadOwned(uuid: UUID): Set<String> = withConn { conn ->
        val out = mutableSetOf<String>()
        conn.prepareStatement("SELECT cosmetic_id FROM player_owned WHERE uuid = ?").use { ps ->
            ps.setString(1, uuid.toString())
            ps.executeQuery().use { rs -> while (rs.next()) out.add(rs.getString(1)) }
        }
        out
    }

    override fun loadEquipped(uuid: UUID): Map<CosmeticType, String> = withConn { conn ->
        val out: MutableMap<CosmeticType, String> = EnumMap(CosmeticType::class.java)
        conn.prepareStatement("SELECT type, cosmetic_id FROM player_equipped WHERE uuid = ?").use {
            ps ->
            ps.setString(1, uuid.toString())
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val type = CosmeticType.fromConfigKey(rs.getString(1)) ?: continue
                    out[type] = rs.getString(2)
                }
            }
        }
        out
    }

    override fun grant(uuid: UUID, cosmeticId: String) = withConn { conn ->
        conn
            .prepareStatement("INSERT OR IGNORE INTO player_owned(uuid, cosmetic_id) VALUES(?, ?)")
            .use { ps ->
                ps.setString(1, uuid.toString())
                ps.setString(2, cosmeticId)
                ps.executeUpdate()
            }
        Unit
    }

    override fun revoke(uuid: UUID, cosmeticId: String) = transaction { conn ->
        conn.prepareStatement("DELETE FROM player_owned WHERE uuid = ? AND cosmetic_id = ?").use {
            ps ->
            ps.setString(1, uuid.toString())
            ps.setString(2, cosmeticId)
            ps.executeUpdate()
        }
        conn
            .prepareStatement("DELETE FROM player_equipped WHERE uuid = ? AND cosmetic_id = ?")
            .use { ps ->
                ps.setString(1, uuid.toString())
                ps.setString(2, cosmeticId)
                ps.executeUpdate()
            }
        Unit
    }

    override fun setEquipped(uuid: UUID, type: CosmeticType, cosmeticId: String?) =
        withConn { conn ->
            if (cosmeticId == null) {
                conn
                    .prepareStatement("DELETE FROM player_equipped WHERE uuid = ? AND type = ?")
                    .use { ps ->
                        ps.setString(1, uuid.toString())
                        ps.setString(2, type.configKey)
                        ps.executeUpdate()
                    }
            } else {
                conn
                    .prepareStatement(
                        "INSERT INTO player_equipped(uuid, type, cosmetic_id) VALUES(?, ?, ?) " +
                            "ON CONFLICT(uuid, type) DO UPDATE SET cosmetic_id = excluded.cosmetic_id"
                    )
                    .use { ps ->
                        ps.setString(1, uuid.toString())
                        ps.setString(2, type.configKey)
                        ps.setString(3, cosmeticId)
                        ps.executeUpdate()
                    }
            }
            Unit
        }

    override fun close() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    private fun <T> transaction(block: (Connection) -> T): T = withConn { connection ->
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (e: Exception) {
            try {
                connection.rollback()
            } catch (rollback: Exception) {
                e.addSuppressed(rollback)
            }
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    private inline fun <T> withConn(block: (Connection) -> T): T {
        return dataSource.connection.use(block)
    }
}
