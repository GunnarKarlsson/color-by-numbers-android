package network.bahn.colorbynumber.android.coloring

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class PuzzleAssetLoader(
    private val context: Context,
) {
    fun load(assetPath: String): LoadedPuzzle {
        val document = parseDocument(readAsset(assetPath))
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
            parsePalette(readAsset(linkedPath))
        } catch (error: Exception) {
            Log.w(TAG, "Falling back to embedded palette because $linkedPath could not be loaded.", error)
            embeddedPalette
        }
    }

    private fun readAsset(assetPath: String): String =
        context.assets.open(assetPath).bufferedReader().use { it.readText() }

    private fun parseDocument(json: String): PuzzleDocument {
        val root = JSONObject(json)
        val metadata = root.getJSONObject("metadata")
        val imageCurveData = root.getJSONObject("image_curve_data")

        return PuzzleDocument(
            version = root.getInt("version"),
            bounds = imageCurveData.getJSONArray("bounds").toPoint(),
            regions = imageCurveData.getJSONArray("regions").toRegions(),
            embeddedPalette = metadata.getJSONArray("palette").toPaletteColors(),
            paletteLink = metadata.optJSONObject("palette_link")?.toPaletteLink(),
            topology = imageCurveData.getJSONObject("topology").toDocumentTopology(),
        )
    }

    private fun parsePalette(json: String): List<PaletteColor> {
        val root = JSONObject(json)
        return root.getJSONArray("colors").toPaletteColors()
    }

    private fun JSONObject.toPaletteLink(): PaletteLink =
        PaletteLink(
            paletteId = getString("palette_id"),
            path = getString("path"),
        )

    private fun JSONObject.toDocumentTopology(): DocumentTopology =
        DocumentTopology(
            vertices = getJSONArray("vertices").toTopologyVertices(),
            edges = getJSONArray("edges").toTopologyEdges(),
            regions = getJSONArray("regions").toTopologyRegionBoundaries(),
        )

    private fun JSONArray.toPaletteColors(): List<PaletteColor> =
        List(length()) { index ->
            val jsonColor = getJSONObject(index)
            PaletteColor(
                id = jsonColor.getInt("id"),
                label = jsonColor.getString("label"),
                rgba = jsonColor.getJSONArray("rgba").toIntArray(),
            )
        }

    private fun JSONArray.toRegions(): List<PuzzleRegion> =
        List(length()) { index ->
            val jsonRegion = getJSONObject(index)
            PuzzleRegion(
                id = jsonRegion.getInt("id"),
                number = jsonRegion.getInt("number"),
                numberPosition = jsonRegion.getJSONArray("number_position").toPoint(),
                targetPaletteId = jsonRegion.optInt("target_palette_id").takeIf { jsonRegion.has("target_palette_id") },
            )
        }

    private fun JSONArray.toTopologyVertices(): List<TopologyVertex> =
        List(length()) { index ->
            val jsonVertex = getJSONObject(index)
            TopologyVertex(
                id = jsonVertex.getInt("id"),
                pos = jsonVertex.getJSONArray("pos").toPoint(),
            )
        }

    private fun JSONArray.toTopologyEdges(): List<TopologyEdge> =
        List(length()) { index ->
            val jsonEdge = getJSONObject(index)
            TopologyEdge(
                id = jsonEdge.getInt("id"),
                start = jsonEdge.getInt("start"),
                end = jsonEdge.getInt("end"),
            )
        }

    private fun JSONArray.toTopologyRegionBoundaries(): List<TopologyRegionBoundary> =
        List(length()) { index ->
            val jsonRegion = getJSONObject(index)
            TopologyRegionBoundary(
                regionId = jsonRegion.getInt("region_id"),
                boundary = jsonRegion.getJSONArray("boundary").toEdgeRefs(),
            )
        }

    private fun JSONArray.toEdgeRefs(): List<RegionEdgeRef> =
        List(length()) { index ->
            val jsonEdgeRef = getJSONObject(index)
            RegionEdgeRef(
                edgeId = jsonEdgeRef.getInt("edge_id"),
                reversed = jsonEdgeRef.getBoolean("reversed"),
            )
        }

    private fun JSONArray.toPoint(): PuzzlePoint =
        PuzzlePoint(
            x = getDouble(0).toFloat(),
            y = getDouble(1).toFloat(),
        )

    private fun JSONArray.toIntArray(): IntArray =
        IntArray(length()) { index -> getInt(index) }

    private fun resolveSiblingAssetPath(assetPath: String, fileName: String): String {
        val parent = assetPath.substringBeforeLast('/', missingDelimiterValue = "")
        return if (parent.isEmpty()) fileName else "$parent/$fileName"
    }

    private companion object {
        const val TAG = "PuzzleAssetLoader"
    }
}
