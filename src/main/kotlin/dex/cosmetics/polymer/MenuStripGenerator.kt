package dex.cosmetics.polymer

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import dex.cosmetics.DexCosmetics
import dex.cosmetics.config.ConfigPaths
import dex.cosmetics.cosmetic.CosmeticType
import dex.cosmetics.gui.MenuGlyphs
import dex.cosmetics.gui.MenuRing
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Bakes the carousel-menu glyphs into the Polymer pack at build time, mirroring the validated
 * compositing prototype. For each focused category it generates a rest image + an 8-frame
 * cycle-right strip + an 8-frame cycle-left strip (cycle-left = cycle-right with the geometry
 * mirrored about the menu's centre), then writes a self-contained `font/menu.json` that maps the
 * [MenuGlyphs] codepoints to those images. A dedicated `dex_cosmetics:menu` font keeps this
 * isolated from the hand-authored `gui.json`.
 *
 * Layer model (see the template PNGs): card faces are composited at their native pixel size (never
 * resized), positioned by the placeholder layers — only translation + horizontal edge-clip: z-order
 * bottom→top: background → wayleft → right → [f<4: left1, top1 | f≥4: top2, left2] → arrows → frame
 * Editable assets (`config/dex_cosmetics/gui/`): per-category `cards/<dir>.png` faces + the
 * `background.png` / `frame.png` chrome. The motion layers stay bundled in the jar.
 */
object MenuStripGenerator {

    private const val FH = 256
    private const val FRAMES = MenuGlyphs.FRAMES
    private const val MENU_ASCENT = 13
    /**
     * Opaque span (px) above which a row/column is treated as a stray export line, not card
     * content.
     */
    private const val ARTIFACT_SPAN = 150

    private const val TEMPLATE_RES = "/assets/dex_cosmetics/textures/gui/template"
    private const val CARD_RES = "/assets/dex_cosmetics/textures/gui/cards"
    private const val PACK_MENU_DIR = "assets/dex_cosmetics/textures/gui/menu"
    private const val FONT_PATH = "assets/dex_cosmetics/font/menu.json"

    private enum class Dir {
        RIGHT,
        LEFT,
    }

    fun writeAll(builder: ResourcePackBuilder) {
        // Reject a broken candidate pack so reload can retain the previous working state.
        try {
            val layers = checkNotNull(loadLayers()) { "Menu template layers missing" }
            val ring = MenuRing.fromConfig()
            val glyphs = MenuGlyphs(ring)
            val faces = ring.categories.associateWith { loadFace(it) }
            require(faces.values.all { it != null }) {
                "Menu card faces are missing for ${faces.filterValues { it == null }.keys}"
            }

            val compositor = Compositor(layers, ring, faces)
            val providers = JsonArray().apply { add(spaceProvider()) }

            for ((i, cat) in ring.categories.withIndex()) {
                writePng(builder, "rest_${cat.dirName}", compositor.restFrame(i))
                providers.add(bitmapProvider("rest_${cat.dirName}", listOf(glyphs.rest(i))))

                writePng(builder, "cycleright_${cat.dirName}", compositor.strip(i, Dir.RIGHT))
                providers.add(
                    bitmapProvider("cycleright_${cat.dirName}", glyphs.cycleRightFrames(i))
                )

                writePng(builder, "cycleleft_${cat.dirName}", compositor.strip(i, Dir.LEFT))
                providers.add(bitmapProvider("cycleleft_${cat.dirName}", glyphs.cycleLeftFrames(i)))
            }

            val font = JsonObject().apply { add("providers", providers) }
            builder.addData(FONT_PATH, font.toString().toByteArray(Charsets.UTF_8))
            DexCosmetics.LOGGER.info("Generated carousel menu for {} categories", ring.size)
        } catch (e: Exception) {
            throw IllegalStateException("Carousel menu generation failed", e)
        }
    }

    // ---- compositing ------------------------------------------------------------------------

    private class Layers(
        val background: BufferedImage,
        val frame: BufferedImage,
        val arrows: BufferedImage?,
        // Resting positions (shown when not animating): center / left-peek / right-peek.
        val topStatic: BufferedImage,
        val leftStatic: BufferedImage,
        val rightStatic: BufferedImage,
        // Animation trajectories.
        val top1: BufferedImage,
        val top2: BufferedImage,
        val left1: BufferedImage,
        val left2: BufferedImage,
        val right: BufferedImage,
        val wayleft: BufferedImage,
    )

    private data class Box(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
        val empty: Boolean,
    )

    private class Compositor(
        val layers: Layers,
        val ring: MenuRing,
        val faces: Map<CosmeticType, BufferedImage?>,
    ) {
        // Declared before `reflect`, which calls box(): Kotlin inits properties in order, so the
        // cache must exist first.
        private val bboxCache = HashMap<Long, Box>()

        /**
         * Reflection axis for mirroring: the menu's right edge (left edge is 0). x' = reflect - x.
         */
        private val reflect = box(layers.frame, 0).maxX

        fun strip(focus: Int, dir: Dir): BufferedImage {
            val out = BufferedImage(FH, FH * FRAMES, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            for (f in 0 until FRAMES) g.drawImage(frame(focus, dir, f), 0, f * FH, null)
            g.dispose()
            return out
        }

        /**
         * The idle (non-animating) menu: center + left-peek + right-peek from the static layers.
         */
        fun restFrame(focus: Int): BufferedImage {
            val img = BufferedImage(FH, FH, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.drawImage(layers.background, 0, 0, null)
            // Side cards first, focused card last so it overlaps them (matches the reference).
            drawCard(
                g,
                layers.rightStatic,
                0,
                face(ring.right(focus)),
                anchorRight = false,
                mirror = false,
            )
            drawCard(
                g,
                layers.leftStatic,
                0,
                face(ring.left(focus)),
                anchorRight = false,
                mirror = false,
            )
            drawCard(g, layers.topStatic, 0, face(ring[focus]), anchorRight = false, mirror = false)
            g.drawImage(layers.frame, 0, 0, null)
            g.dispose()
            return img
        }

        fun frame(focus: Int, dir: Dir, f: Int): BufferedImage {
            val img = BufferedImage(FH, FH, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
            )
            g.drawImage(layers.background, 0, 0, null)

            val mirror = dir == Dir.LEFT
            val topFace = face(ring[focus])
            // cycle-right: left neighbour becomes focus, right neighbour pushed out, two-left
            // cycles in.
            // cycle-left mirrors the geometry, so the same template slots carry the opposite
            // neighbours.
            val leftFace = face(if (mirror) ring.right(focus) else ring.left(focus))
            val rightFace = face(if (mirror) ring.left(focus) else ring.right(focus))
            val wayFace = face(if (mirror) ring.twoRight(focus) else ring.twoLeft(focus))

            drawCard(g, layers.wayleft, f, wayFace, anchorRight = true, mirror)
            drawCard(g, layers.right, f, rightFace, anchorRight = false, mirror)
            if (f < 4) {
                drawCard(g, layers.left1, f, leftFace, anchorRight = false, mirror)
                drawCard(g, layers.top1, f, topFace, anchorRight = false, mirror)
            } else {
                drawCard(g, layers.top2, f - 4, topFace, anchorRight = false, mirror)
                drawCard(g, layers.left2, f - 4, leftFace, anchorRight = false, mirror)
            }

            layers.arrows?.let { ar ->
                val rows = ar.height / FH
                if (rows > 0) g.drawImage(ar.getSubimage(0, (f % rows) * FH, FH, FH), 0, 0, null)
            }
            g.drawImage(layers.frame, 0, 0, null)
            g.dispose()
            return img
        }

        private fun face(type: CosmeticType): BufferedImage? = faces[type]

        /**
         * Draw [face] clipped to the template silhouette's horizontal span and anchored to one edge
         * (so a partly-clipped card shows a full-size face sliding behind an edge, not a squashed
         * one). When [mirror], the silhouette is reflected about [reflect] and the anchor side
         * flips — turning cycle-right geometry into cycle-left.
         */
        private fun drawCard(
            g: Graphics2D,
            layer: BufferedImage,
            frame: Int,
            face: BufferedImage?,
            anchorRight: Boolean,
            mirror: Boolean,
        ) {
            if (face == null) return
            val bb = box(layer, frame)
            if (bb.empty) return
            var minX = bb.minX
            var maxX = bb.maxX
            var right = anchorRight
            if (mirror) {
                val nMin = reflect - bb.maxX
                val nMax = reflect - bb.minX
                minX = nMin
                maxX = nMax
                right = !anchorRight
            }
            // Draw the face at its native pixel size (never resize) anchored to one edge; clip to
            // the
            // template silhouette's horizontal span so a partly-clipped card shows a full-size face
            // sliding behind an edge.
            val faceX = if (right) maxX - (face.width - 1) else minX
            g.clip = Rectangle(minX, 0, maxX - minX + 1, FH)
            g.drawImage(face, faceX, bb.minY, null)
            g.clip = null
        }

        /**
         * Opaque bounding box of one 256×256 frame slice. Rows/columns whose opaque span exceeds
         * [ARTIFACT_SPAN] are ignored — a single card is ≤78px, so a full-width/height run is a
         * stray export line, not card content, and must not inflate the box.
         */
        private fun box(img: BufferedImage, frame: Int): Box {
            val key = (System.identityHashCode(img).toLong() shl 8) or frame.toLong()
            bboxCache[key]?.let {
                return it
            }
            val y0 = frame * FH
            val px = img.getRGB(0, y0, FH, FH, null, 0, FH)
            val rowCount = IntArray(FH)
            val colCount = IntArray(FH)
            for (y in 0 until FH) for (x in 0 until FH) {
                if (((px[y * FH + x] ushr 24) and 0xFF) > 16) {
                    rowCount[y]++
                    colCount[x]++
                }
            }
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = -1
            var maxY = -1
            for (y in 0 until FH) {
                if (rowCount[y] > ARTIFACT_SPAN) continue
                for (x in 0 until FH) {
                    if (colCount[x] > ARTIFACT_SPAN) continue
                    if (((px[y * FH + x] ushr 24) and 0xFF) > 16) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            val b = if (maxX < 0) Box(0, 0, 0, 0, true) else Box(minX, minY, maxX, maxY, false)
            bboxCache[key] = b
            return b
        }
    }

    // ---- asset loading ----------------------------------------------------------------------

    private fun loadLayers(): Layers? {
        // Chrome is user-editable (config gui dir, fallback bundled); motion layers are bundled
        // only.
        val background = loadEditable("background.png", "$CARD_RES/background.png") ?: return null
        val frame = loadEditable("frame.png", "$CARD_RES/frame.png") ?: return null
        // Arrows are optional — they may instead be baked into frame.png.
        val arrows =
            MenuStripGenerator::class.java.getResourceAsStream("$TEMPLATE_RES/arrows.png")?.use {
                ImageIO.read(it)
            }
        val topStatic = loadResource("$TEMPLATE_RES/top_static.png") ?: return null
        val leftStatic = loadResource("$TEMPLATE_RES/left_static.png") ?: return null
        val rightStatic = loadResource("$TEMPLATE_RES/right_static.png") ?: return null
        val top1 = loadResource("$TEMPLATE_RES/top1.png") ?: return null
        val top2 = loadResource("$TEMPLATE_RES/top2.png") ?: return null
        val left1 = loadResource("$TEMPLATE_RES/left1.png") ?: return null
        val left2 = loadResource("$TEMPLATE_RES/left2.png") ?: return null
        val right = loadResource("$TEMPLATE_RES/right.png") ?: return null
        val wayleft = loadResource("$TEMPLATE_RES/wayleft.png") ?: return null
        return Layers(
            background,
            frame,
            arrows,
            topStatic,
            leftStatic,
            rightStatic,
            top1,
            top2,
            left1,
            left2,
            right,
            wayleft,
        )
    }

    private fun loadFace(type: CosmeticType): BufferedImage? =
        loadEditable("cards/${type.dirName}.png", "$CARD_RES/${type.dirName}.png")

    /** Load from `config/dex_cosmetics/gui/<rel>`, falling back to a bundled classpath resource. */
    private fun loadEditable(rel: String, resourceFallback: String): BufferedImage? {
        val path: Path = ConfigPaths.guiDir.resolve(rel)
        if (Files.exists(path)) {
            runCatching { Files.newInputStream(path).use { ImageIO.read(it) } }
                .getOrNull()
                ?.let {
                    return it
                }
            DexCosmetics.LOGGER.warn("Failed to read GUI asset {}, falling back to bundled", path)
        }
        return loadResource(resourceFallback)
    }

    private fun loadResource(path: String): BufferedImage? =
        MenuStripGenerator::class
            .java
            .getResourceAsStream(path)
            ?.use { ImageIO.read(it) }
            .also {
                if (it == null) DexCosmetics.LOGGER.warn("Bundled menu asset missing: {}", path)
            }

    // ---- pack output ------------------------------------------------------------------------

    private fun writePng(builder: ResourcePackBuilder, name: String, img: BufferedImage) {
        val bytes =
            ByteArrayOutputStream().use {
                ImageIO.write(img, "png", it)
                it.toByteArray()
            }
        builder.addData("$PACK_MENU_DIR/$name.png", bytes)
    }

    private fun bitmapProvider(name: String, chars: List<String>): JsonObject =
        JsonObject().apply {
            addProperty("type", "bitmap")
            addProperty("file", "dex_cosmetics:gui/menu/$name.png")
            addProperty("ascent", MENU_ASCENT)
            addProperty("height", FH)
            val arr = JsonArray()
            chars.forEach { arr.add(JsonPrimitive(it)) }
            add("chars", arr)
        }

    /** Powers-of-two space advances matching dex.cosmetics.gui.GuiBackground's space chars. */
    private fun spaceProvider(): JsonObject =
        JsonObject().apply {
            addProperty("type", "space")
            val adv = JsonObject()
            for (n in 0..8) {
                adv.addProperty(String(Character.toChars(0xE100 + n)), -(1 shl n))
                adv.addProperty(String(Character.toChars(0xE200 + n)), (1 shl n))
            }
            add("advances", adv)
        }
}
