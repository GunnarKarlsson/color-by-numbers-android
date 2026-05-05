package network.bahn.colorbynumber.android.coloring

import android.content.Context
import android.util.Log

class PuzzleAssetLoader(
    private val context: Context,
) {
    fun load(assetPath: String): LoadedPuzzle {
        val json = readAsset(assetPath)
        val document = when (PuzzleJsonParser.parseImageType(json)) {
            ImageType.Pixelated -> PuzzleJsonParser.parsePixelatedDocument(json)
            ImageType.Standard -> PuzzleJsonParser.parseDocument(json)
        }
        val resolvedPalette = resolvePalette(
            assetPath = assetPath,
            embeddedPalette = document.embeddedPalette,
            paletteLink = document.paletteLink,
        )
        return PuzzleTopology.buildLoadedPuzzle(document, resolvedPalette)
    }

    private fun resolvePalette(
        assetPath: String,
        embeddedPalette: List<PaletteColor>,
        paletteLink: PaletteLink?,
    ): List<PaletteColor> {
        if (paletteLink == null) {
            return embeddedPalette
        }

        for (candidate in paletteAssetCandidates(assetPath, paletteLink.path)) {
            try {
                return PuzzleJsonParser.parsePalette(readAsset(candidate))
            } catch (_: Exception) {
                // Try the next candidate path before falling back to the embedded palette.
            }
        }

        Log.w(TAG, "Falling back to embedded palette because ${paletteLink.path} could not be loaded.")
        return embeddedPalette
    }

    private fun readAsset(assetPath: String): String =
        context.assets.open(assetPath).bufferedReader().use { it.readText() }

    private fun resolveSiblingAssetPath(assetPath: String, fileName: String): String {
        val parent = assetPath.substringBeforeLast('/', missingDelimiterValue = "")
        return if (parent.isEmpty()) fileName else "$parent/$fileName"
    }

    private fun paletteAssetCandidates(assetPath: String, linkedPath: String): List<String> {
        val normalizedPath = linkedPath.replace('\\', '/').trim()
        val fileName = normalizedPath.substringAfterLast('/')
        return buildList {
            if (normalizedPath.isNotEmpty() && !normalizedPath.startsWith("/")) {
                add(normalizedPath)
            }
            if (fileName.isNotEmpty()) {
                add(resolveSiblingAssetPath(assetPath, fileName))
            }
        }.distinct()
    }

    private companion object {
        const val TAG = "PuzzleAssetLoader"
    }
}
