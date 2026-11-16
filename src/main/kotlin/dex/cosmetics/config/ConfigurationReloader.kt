package dex.cosmetics.config

import dex.cosmetics.cosmetic.Cosmetic
import dex.cosmetics.cosmetic.CosmeticRegistry

/**
 * Stages a complete candidate and restores live configuration if registration or pack build fails.
 */
internal class ConfigurationReloader(
    private val registry: CosmeticRegistry,
    private val currentConfig: () -> CosmeticsConfig,
    private val setConfig: (CosmeticsConfig) -> Unit,
    private val registerModels: () -> Unit,
    private val buildPack: () -> Boolean,
) {
    fun reload(candidate: CosmeticsConfig, cosmetics: List<Cosmetic>) {
        val previousConfig = currentConfig()
        candidate.validated().requireReloadCompatible(previousConfig)
        val previousCosmetics = registry.all().toList()
        // Also checks duplicate IDs before any live mutation.
        CosmeticRegistry().replaceAll(cosmetics)
        try {
            setConfig(candidate)
            registry.replaceAll(cosmetics)
            registerModels()
            check(buildPack()) {
                "Resource pack generation failed. The previous configuration was restored; see the server log."
            }
        } catch (e: Exception) {
            setConfig(previousConfig)
            registry.replaceAll(previousCosmetics)
            try {
                registerModels()
            } catch (restore: Exception) {
                e.addSuppressed(restore)
            }
            throw e
        }
    }
}
