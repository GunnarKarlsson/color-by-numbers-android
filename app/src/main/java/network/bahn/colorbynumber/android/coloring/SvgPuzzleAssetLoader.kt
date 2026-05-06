package network.bahn.colorbynumber.android.coloring

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.caverock.androidsvg.SVG
import java.util.ArrayDeque
import kotlin.math.ceil

private const val IMAGE_DATA_ROOT = "imagedata"
private const val DEFAULT_MIN_PALETTE_PIXELS = 64
private const val NO_REGION_ID = -1
private const val CHECKER_TILE_SIZE_PX = 16

data class SvgPuzzle(
    val assetPath: String,
    val width: Int,
    val height: Int,
    val lineBitmap: Bitmap,
    val palette: List<PaletteColor>,
    val regionLabels: IntArray,
    val regions: List<SvgRegion>,
) {
    val totalFillTargets: Int
        get() = regions.count { it.isPlayable }

    val worldBounds: PuzzleBounds
        get() = PuzzleBounds(
            minX = 0f,
            minY = 0f,
            maxX = width.toFloat(),
            maxY = height.toFloat(),
        )

    fun composeDisplayBitmap(session: PuzzleSession): Bitmap {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelIndex = y * width + x
                val regionId = regionLabels[pixelIndex]
                pixels[pixelIndex] = when {
                    regionId == NO_REGION_ID -> Color.WHITE
                    else -> {
                        val region = regions[regionId]
                        when {
                            !region.isPlayable -> region.targetColor
                            session.fillsByRegionId.containsKey(region.id) -> region.targetColor
                            region.targetPaletteId == session.selectedPaletteId -> checkerColor(x, y)
                            else -> Color.WHITE
                        }
                    }
                }
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    fun regionAt(x: Int, y: Int): SvgRegion? {
        if (x !in 0 until width || y !in 0 until height) {
            return null
        }
        val regionId = regionLabels[(y * width) + x]
        if (regionId == NO_REGION_ID) {
            return null
        }
        return regions[regionId]
    }

    fun overallProgress(session: PuzzleSession): Pair<Int, Int> {
        val completed = session.fillsByRegionId.keys.count { regionId ->
            regions.getOrNull(regionId)?.isPlayable == true
        }
        return completed to totalFillTargets
    }

    fun selectedColorProgress(session: PuzzleSession): Pair<Int, Int>? {
        val selectedPaletteId = session.selectedPaletteId ?: return null
        val total = regions.count { it.targetPaletteId == selectedPaletteId }
        val completed = session.fillsByRegionId.keys.count { regionId ->
            regions.getOrNull(regionId)?.targetPaletteId == selectedPaletteId
        }
        return completed to total
    }
}

data class SvgRegion(
    val id: Int,
    val targetColor: Int,
    val targetPaletteId: Int?,
    val isPlayable: Boolean,
)

private data class AnalyzedSvgPuzzle(
    val palette: List<PaletteColor>,
    val regionLabels: IntArray,
    val regions: List<SvgRegion>,
)

class SvgPuzzleAssetLoader(
    private val context: Context,
) {
    fun load(assetPath: String): SvgPuzzle {
        val resolvedPaths = resolvePaths(assetPath)
        val colorsSvg = readText(resolvedPaths.colorsAssetPath)
        val linesSvg = readText(resolvedPaths.linesAssetPath)
        val sanitizedColorsSvg = stripGroupById(colorsSvg, "vector-lines") ?: colorsSvg

        val gameplayBitmap = renderSvgBitmap(sanitizedColorsSvg, backgroundColor = null)
        val lineBitmap = renderSvgBitmap(
            linesSvg,
            backgroundColor = null,
            targetWidth = gameplayBitmap.width,
            targetHeight = gameplayBitmap.height,
        )
        val analyzedPuzzle = analyzeSvgPuzzle(
            renderedBitmap = gameplayBitmap,
            paletteCandidates = extractDeclaredPalette(sanitizedColorsSvg),
            minRegionPixels = DEFAULT_MIN_PALETTE_PIXELS,
        )

        return SvgPuzzle(
            assetPath = assetPath,
            width = gameplayBitmap.width,
            height = gameplayBitmap.height,
            lineBitmap = lineBitmap,
            palette = analyzedPuzzle.palette,
            regionLabels = analyzedPuzzle.regionLabels,
            regions = analyzedPuzzle.regions,
        )
    }

    fun loadPreviewBitmap(assetPath: String): Bitmap {
        val resolvedPaths = resolvePaths(assetPath)
        resolvedPaths.previewAssetPath?.let { previewAssetPath ->
            return loadBitmapAsset(previewAssetPath)
        }

        val colorsSvg = readText(resolvedPaths.colorsAssetPath)
        val sanitizedColorsSvg = stripGroupById(colorsSvg, "vector-lines") ?: colorsSvg

        val colorsBitmap = renderSvgBitmap(
            sanitizedColorsSvg,
            backgroundColor = Color.WHITE,
        )
        val linesBitmap = renderSvgBitmap(
            readText(resolvedPaths.linesAssetPath),
            backgroundColor = null,
        )
        return Bitmap.createBitmap(
            colorsBitmap.width,
            colorsBitmap.height,
            Bitmap.Config.ARGB_8888,
        ).apply {
            val canvas = Canvas(this)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(colorsBitmap, 0f, 0f, null)
            canvas.drawBitmap(
                linesBitmap,
                null,
                android.graphics.Rect(0, 0, colorsBitmap.width, colorsBitmap.height),
                null,
            )
        }
    }

    private fun loadBitmapAsset(assetPath: String): Bitmap {
        return if (assetPath.endsWith(".svg", ignoreCase = true)) {
            renderSvgBitmap(readText(assetPath), backgroundColor = Color.WHITE)
        } else {
            context.assets.open(assetPath).use { input ->
                requireNotNull(BitmapFactory.decodeStream(input)) {
                    "Failed to decode bitmap asset $assetPath"
                }
            }
        }
    }

    private fun readText(assetPath: String): String =
        context.assets.open(assetPath).bufferedReader().use { it.readText() }

    private fun resolvePaths(assetPath: String): ResolvedSvgPuzzleAssets {
        val directory = "$IMAGE_DATA_ROOT/$assetPath"
        val fileNames = context.assets.list(directory)?.toList().orEmpty()
        require(fileNames.isNotEmpty()) { "No puzzle assets found in $directory" }

        fun find(prefix: String): String? =
            fileNames.firstOrNull { fileName ->
                fileName.startsWith(prefix, ignoreCase = true)
            }?.let { "$directory/$it" }

        val linesAssetPath = requireNotNull(find("lines")) {
            "Missing lines SVG in $directory"
        }
        val colorsAssetPath = requireNotNull(find("colors")) {
            "Missing colors SVG in $directory"
        }
        val previewAssetPath = find("preview")

        return ResolvedSvgPuzzleAssets(
            directoryAssetPath = directory,
            linesAssetPath = linesAssetPath,
            colorsAssetPath = colorsAssetPath,
            previewAssetPath = previewAssetPath,
        )
    }

    private fun renderSvgBitmap(
        svgText: String,
        backgroundColor: Int?,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
    ): Bitmap {
        val svgSource = parseSvgSource(svgText)
        val svg = svgSource.svg
        val width = targetWidth ?: svgSource.width
        val height = targetHeight ?: svgSource.height
        val picture = svg.renderToPicture(width, height)

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            if (backgroundColor != null) {
                canvas.drawColor(backgroundColor)
            }
            canvas.drawPicture(picture)
        }
    }
}

private data class ParsedSvgSource(
    val svg: SVG,
    val width: Int,
    val height: Int,
)

private fun parseSvgSource(svgText: String): ParsedSvgSource {
    val svg = SVG.getFromString(svgText)
    val viewBox = svg.documentViewBox ?: RectF(
        0f,
        0f,
        svg.documentWidth.takeIf { it > 0f } ?: 1f,
        svg.documentHeight.takeIf { it > 0f } ?: 1f,
    )
    val width = ceil(viewBox.width().coerceAtLeast(1f).toDouble()).toInt()
    val height = ceil(viewBox.height().coerceAtLeast(1f).toDouble()).toInt()
    return ParsedSvgSource(
        svg = svg,
        width = width,
        height = height,
    )
}

private data class ResolvedSvgPuzzleAssets(
    val directoryAssetPath: String,
    val linesAssetPath: String,
    val colorsAssetPath: String,
    val previewAssetPath: String?,
)

private fun maybeQueueNeighbor(
    neighborIndex: Int,
    expectedColor: Int,
    pixels: IntArray,
    regionLabels: IntArray,
    regionId: Int,
    stack: ArrayDeque<Int>,
) {
    if (regionLabels[neighborIndex] != NO_REGION_ID || pixels[neighborIndex] != expectedColor) {
        return
    }
    regionLabels[neighborIndex] = regionId
    stack.addLast(neighborIndex)
}

private fun stripGroupById(svg: String, groupId: String): String? {
    val marker = "id=\"$groupId\""
    val idIndex = svg.indexOf(marker)
    if (idIndex == -1) {
        return null
    }

    val groupStart = svg.lastIndexOf("<g", startIndex = idIndex)
    if (groupStart == -1) {
        return null
    }

    val startTagRelativeEnd = svg.substring(groupStart).indexOf('>')
    if (startTagRelativeEnd == -1) {
        return null
    }

    val startTagEnd = groupStart + startTagRelativeEnd + 1
    var depth = 1
    var cursor = startTagEnd

    while (cursor < svg.length) {
        val nextOpen = svg.indexOf("<g", startIndex = cursor).takeIf { it >= 0 }
        val nextClose = svg.indexOf("</g>", startIndex = cursor).takeIf { it >= 0 }

        when {
            nextOpen != null && nextClose != null && nextOpen < nextClose -> {
                depth += 1
                cursor = nextOpen + 2
            }
            nextClose != null -> {
                depth -= 1
                cursor = nextClose + 4
                if (depth == 0) {
                    return buildString(svg.length) {
                        append(svg, 0, groupStart)
                        append(svg, cursor, svg.length)
                    }
                }
            }
            else -> return null
        }
    }

    return null
}

private fun extractDeclaredPalette(svg: String): List<Int> {
    val colors = sortedSetOf<Int>()
    extractPaintColorStrings(svg).forEach { colorString ->
        parseColorString(colorString)?.let(colors::add)
    }
    return colors.toList()
}

private fun analyzeSvgPuzzle(
    renderedBitmap: Bitmap,
    paletteCandidates: List<Int>,
    minRegionPixels: Int,
): AnalyzedSvgPuzzle {
    val pixels = IntArray(renderedBitmap.width * renderedBitmap.height).also {
        renderedBitmap.getPixels(
            it,
            0,
            renderedBitmap.width,
            0,
            0,
            renderedBitmap.width,
            renderedBitmap.height,
        )
    }
    val regionLabels = IntArray(pixels.size) { NO_REGION_ID }
    val declaredPalette = paletteCandidates.toSet()
    val playableRegionCounts = linkedMapOf<Int, Int>()
    val regions = mutableListOf<SvgRegion>()
    val stack = ArrayDeque<Int>()

    for (index in pixels.indices) {
        if (regionLabels[index] != NO_REGION_ID || Color.alpha(pixels[index]) == 0) {
            continue
        }

        val targetColor = pixels[index]
        val regionId = regions.size
        regionLabels[index] = regionId
        stack.addLast(index)
        var regionPixelCount = 0

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            regionPixelCount += 1
            val x = current % renderedBitmap.width
            val y = current / renderedBitmap.width

            if (x > 0) {
                maybeQueueNeighbor(
                    neighborIndex = current - 1,
                    expectedColor = targetColor,
                    pixels = pixels,
                    regionLabels = regionLabels,
                    regionId = regionId,
                    stack = stack,
                )
            }
            if (x + 1 < renderedBitmap.width) {
                maybeQueueNeighbor(
                    neighborIndex = current + 1,
                    expectedColor = targetColor,
                    pixels = pixels,
                    regionLabels = regionLabels,
                    regionId = regionId,
                    stack = stack,
                )
            }
            if (y > 0) {
                maybeQueueNeighbor(
                    neighborIndex = current - renderedBitmap.width,
                    expectedColor = targetColor,
                    pixels = pixels,
                    regionLabels = regionLabels,
                    regionId = regionId,
                    stack = stack,
                )
            }
            if (y + 1 < renderedBitmap.height) {
                maybeQueueNeighbor(
                    neighborIndex = current + renderedBitmap.width,
                    expectedColor = targetColor,
                    pixels = pixels,
                    regionLabels = regionLabels,
                    regionId = regionId,
                    stack = stack,
                )
            }
        }

        val isPlayable = declaredPalette.contains(targetColor) && regionPixelCount >= minRegionPixels
        if (isPlayable) {
            playableRegionCounts[targetColor] = (playableRegionCounts[targetColor] ?: 0) + 1
        }
        regions += SvgRegion(
            id = regionId,
            targetColor = targetColor,
            targetPaletteId = null,
            isPlayable = isPlayable,
        )
    }

    val palette = paletteCandidates
        .filter { playableRegionCounts.containsKey(it) }
        .mapIndexed { index, argb ->
            PaletteColor(
                id = index + 1,
                label = toHex(argb),
                rgba = intArrayOf(
                    Color.red(argb),
                    Color.green(argb),
                    Color.blue(argb),
                    Color.alpha(argb),
                ),
            )
        }
    val paletteIdByColor = palette.associate { paletteColor ->
        rgbaToColorInt(paletteColor.rgba) to paletteColor.id
    }
    val updatedRegions = regions.map { region ->
        if (!region.isPlayable) {
            region
        } else {
            region.copy(targetPaletteId = paletteIdByColor[region.targetColor])
        }
    }

    return AnalyzedSvgPuzzle(
        palette = palette,
        regionLabels = regionLabels,
        regions = updatedRegions,
    )
}

private fun extractPaintColorStrings(svg: String): List<String> {
    val colors = mutableListOf<String>()
    for (property in listOf("fill", "stroke")) {
        val stylePrefix = "$property:"
        val attrPrefix = "$property=\""

        var start = 0
        while (true) {
            val index = svg.indexOf(stylePrefix, start)
            if (index == -1) {
                break
            }
            val valueStart = index + stylePrefix.length
            val value = readStyleValue(svg.substring(valueStart))
            normalizeColorToken(value)?.let(colors::add)
            start = valueStart
        }

        start = 0
        while (true) {
            val index = svg.indexOf(attrPrefix, start)
            if (index == -1) {
                break
            }
            val valueStart = index + attrPrefix.length
            val end = svg.indexOf('"', valueStart)
            if (end == -1) {
                break
            }
            val value = svg.substring(valueStart, end)
            normalizeColorToken(value)?.let(colors::add)
            start = end + 1
        }
    }
    return colors
}

private fun readStyleValue(input: String): String {
    val quoteIndex = input.indexOf('"').takeIf { it >= 0 } ?: input.length
    val semicolonIndex = input.indexOf(';').takeIf { it >= 0 } ?: input.length
    return input.substring(0, minOf(quoteIndex, semicolonIndex)).trim()
}

private fun normalizeColorToken(value: String): String? {
    val trimmed = value.trim()
    return if (
        trimmed.isEmpty() ||
        trimmed.equals("none", ignoreCase = true) ||
        trimmed.equals("transparent", ignoreCase = true)
    ) {
        null
    } else {
        trimmed
    }
}

private fun parseColorString(value: String): Int? {
    val trimmed = value.trim()
    if (trimmed.startsWith("#")) {
        return parseHexColor(trimmed.removePrefix("#"))
    }
    if (trimmed.startsWith("rgb(") && trimmed.endsWith(")")) {
        val parts = trimmed.removePrefix("rgb(").removeSuffix(")").split(',').map { it.trim() }
        if (parts.size == 3) {
            val r = parts[0].toIntOrNull() ?: return null
            val g = parts[1].toIntOrNull() ?: return null
            val b = parts[2].toIntOrNull() ?: return null
            return Color.argb(255, r, g, b)
        }
    }
    if (trimmed.startsWith("rgba(") && trimmed.endsWith(")")) {
        val parts = trimmed.removePrefix("rgba(").removeSuffix(")").split(',').map { it.trim() }
        if (parts.size == 4) {
            val r = parts[0].toIntOrNull() ?: return null
            val g = parts[1].toIntOrNull() ?: return null
            val b = parts[2].toIntOrNull() ?: return null
            val alpha = parseAlpha(parts[3]) ?: return null
            return Color.argb(alpha, r, g, b)
        }
    }
    return null
}

private fun parseHexColor(value: String): Int? =
    when (value.length) {
        3 -> {
            val r = value.substring(0, 1).repeat(2).toIntOrNull(16) ?: return null
            val g = value.substring(1, 2).repeat(2).toIntOrNull(16) ?: return null
            val b = value.substring(2, 3).repeat(2).toIntOrNull(16) ?: return null
            Color.argb(255, r, g, b)
        }
        6 -> {
            val r = value.substring(0, 2).toIntOrNull(16) ?: return null
            val g = value.substring(2, 4).toIntOrNull(16) ?: return null
            val b = value.substring(4, 6).toIntOrNull(16) ?: return null
            Color.argb(255, r, g, b)
        }
        8 -> {
            val r = value.substring(0, 2).toIntOrNull(16) ?: return null
            val g = value.substring(2, 4).toIntOrNull(16) ?: return null
            val b = value.substring(4, 6).toIntOrNull(16) ?: return null
            val a = value.substring(6, 8).toIntOrNull(16) ?: return null
            Color.argb(a, r, g, b)
        }
        else -> null
    }

private fun parseAlpha(value: String): Int? {
    value.toIntOrNull()?.let { return it.coerceIn(0, 255) }
    val floatValue = value.toFloatOrNull() ?: return null
    return (floatValue.coerceIn(0f, 1f) * 255f).toInt()
}

private fun rgbaToColorInt(rgba: IntArray): Int =
    Color.argb(rgba[3], rgba[0], rgba[1], rgba[2])

private fun checkerColor(x: Int, y: Int): Int {
    val checkerX = (x / CHECKER_TILE_SIZE_PX) % 2
    val checkerY = (y / CHECKER_TILE_SIZE_PX) % 2
    return if (checkerX == checkerY) {
        Color.argb(255, 242, 242, 242)
    } else {
        Color.argb(255, 204, 204, 204)
    }
}

private fun toHex(argb: Int): String {
    val alpha = Color.alpha(argb)
    return if (alpha == 255) {
        String.format("#%02X%02X%02X", Color.red(argb), Color.green(argb), Color.blue(argb))
    } else {
        String.format(
            "#%02X%02X%02X%02X",
            Color.red(argb),
            Color.green(argb),
            Color.blue(argb),
            alpha,
        )
    }
}
