package network.bahn.colorbynumber.android.coloring

import android.content.Context
import android.util.Log

class PuzzleAssetLoader(
    private val context: Context,
) {
    fun load(assetPath: String): LoadedPuzzle {
        val document = PuzzleJsonParser.parseDocument(readAsset(assetPath))
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

        val linkedPath = resolveSiblingAssetPath(assetPath, paletteLink.path)
        return try {
            PuzzleJsonParser.parsePalette(readAsset(linkedPath))
        } catch (error: Exception) {
            Log.w(TAG, "Falling back to embedded palette because $linkedPath could not be loaded.", error)
            embeddedPalette
        }
    }

    private fun readAsset(assetPath: String): String =
        context.assets.open(assetPath).bufferedReader().use { it.readText() }

    private fun resolveSiblingAssetPath(assetPath: String, fileName: String): String {
        val parent = assetPath.substringBeforeLast('/', missingDelimiterValue = "")
        return if (parent.isEmpty()) fileName else "$parent/$fileName"
    }

    private companion object {
        const val TAG = "PuzzleAssetLoader"
    }
}
