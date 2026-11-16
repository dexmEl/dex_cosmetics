package dex.cosmetics

import net.minecraft.Bootstrap
import net.minecraft.SharedConstants
import org.junit.jupiter.api.BeforeAll

abstract class MinecraftTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            SharedConstants.createGameVersion()
            Bootstrap.initialize()
        }
    }
}
