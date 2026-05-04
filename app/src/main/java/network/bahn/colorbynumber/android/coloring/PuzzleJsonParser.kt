package network.bahn.colorbynumber.android.coloring

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName

internal object PuzzleJsonParser {
    private val gson: Gson = GsonBuilder().create()

    fun parseDocument(json: String): PuzzleDocument {
        val root = gson.fromJson(json, PuzzleDocumentFile::class.java)
        val metadata = requireNotNull(root.metadata) { "Puzzle document is missing metadata." }
        val imageCurveData = requireNotNull(root.imageCurveData) { "Puzzle document is missing image_curve_data." }
        val bounds = requireNotNull(imageCurveData.bounds?.toPoint()) { "Puzzle document is missing bounds." }
        val topology = requireNotNull(imageCurveData.topology?.toDocumentTopology()) { "Puzzle document is missing topology." }

        return PuzzleDocument(
            version = root.version,
            bounds = bounds,
            regions = imageCurveData.regions.orEmpty().map { it.toPuzzleRegion() },
            embeddedPalette = metadata.palette.orEmpty().map { it.toPaletteColor() },
            paletteLink = metadata.paletteLink?.toPaletteLink(),
            topology = topology,
        )
    }

    fun parsePalette(json: String): List<PaletteColor> {
        val root = gson.fromJson(json, PaletteFile::class.java)
        return root.colors.orEmpty().map { it.toPaletteColor() }
    }
}

private fun PaletteColorEntry.toPaletteColor(): PaletteColor =
    PaletteColor(
        id = id,
        label = label,
        rgba = rgba.toIntArray(),
    )

private fun PaletteLinkEntry.toPaletteLink(): PaletteLink =
    PaletteLink(
        paletteId = paletteId,
        path = path,
    )

private fun RegionEntry.toPuzzleRegion(): PuzzleRegion =
    PuzzleRegion(
        id = id,
        number = number,
        numberPosition = requireNotNull(numberPosition.toPoint()) { "Region $id is missing number_position." },
        targetPaletteId = targetPaletteId,
    )

private fun TopologyEntry.toDocumentTopology(): DocumentTopology =
    DocumentTopology(
        vertices = vertices.orEmpty().map { it.toTopologyVertex() },
        edges = edges.orEmpty().map { it.toTopologyEdge() },
        regions = regions.orEmpty().map { it.toTopologyRegionBoundary() },
    )

private fun VertexEntry.toTopologyVertex(): TopologyVertex =
    TopologyVertex(
        id = id,
        pos = requireNotNull(pos.toPoint()) { "Vertex $id is missing pos." },
    )

private fun EdgeEntry.toTopologyEdge(): TopologyEdge =
    TopologyEdge(
        id = id,
        start = start,
        end = end,
    )

private fun TopologyRegionEntry.toTopologyRegionBoundary(): TopologyRegionBoundary =
    TopologyRegionBoundary(
        regionId = regionId,
        outer = (outer ?: boundary).orEmpty().map { it.toRegionEdgeRef() },
        holes = holes.orEmpty().map { loop -> loop.orEmpty().map { it.toRegionEdgeRef() } },
    )

private fun RegionEdgeRefEntry.toRegionEdgeRef(): RegionEdgeRef =
    RegionEdgeRef(
        edgeId = edgeId,
        reversed = reversed,
    )

private fun List<Float>?.toPoint(): PuzzlePoint? {
    if (this == null || size < 2) {
        return null
    }

    return PuzzlePoint(
        x = this[0],
        y = this[1],
    )
}

private data class PuzzleDocumentFile(
    @SerializedName("version")
    val version: Int = 0,
    @SerializedName("metadata")
    val metadata: MetadataEntry? = null,
    @SerializedName("image_curve_data")
    val imageCurveData: ImageCurveDataEntry? = null,
)

private data class MetadataEntry(
    @SerializedName("palette")
    val palette: List<PaletteColorEntry>? = emptyList(),
    @SerializedName("palette_link")
    val paletteLink: PaletteLinkEntry? = null,
)

private data class ImageCurveDataEntry(
    @SerializedName("bounds")
    val bounds: List<Float>? = null,
    @SerializedName("regions")
    val regions: List<RegionEntry>? = emptyList(),
    @SerializedName("topology")
    val topology: TopologyEntry? = null,
)

private data class PaletteFile(
    @SerializedName("colors")
    val colors: List<PaletteColorEntry>? = emptyList(),
)

private data class PaletteColorEntry(
    @SerializedName("id")
    val id: Int = 0,
    @SerializedName("label")
    val label: String = "",
    @SerializedName("rgba")
    val rgba: List<Int> = emptyList(),
)

private data class PaletteLinkEntry(
    @SerializedName("palette_id")
    val paletteId: String = "",
    @SerializedName("path")
    val path: String = "",
)

private data class RegionEntry(
    @SerializedName("id")
    val id: Int = 0,
    @SerializedName("number")
    val number: Int = 0,
    @SerializedName("number_position")
    val numberPosition: List<Float>? = null,
    @SerializedName("target_palette_id")
    val targetPaletteId: Int? = null,
)

private data class TopologyEntry(
    @SerializedName("vertices")
    val vertices: List<VertexEntry>? = emptyList(),
    @SerializedName("edges")
    val edges: List<EdgeEntry>? = emptyList(),
    @SerializedName("regions")
    val regions: List<TopologyRegionEntry>? = emptyList(),
)

private data class VertexEntry(
    @SerializedName("id")
    val id: Int = 0,
    @SerializedName("pos")
    val pos: List<Float>? = null,
)

private data class EdgeEntry(
    @SerializedName("id")
    val id: Int = 0,
    @SerializedName("start")
    val start: Int = 0,
    @SerializedName("end")
    val end: Int = 0,
)

private data class TopologyRegionEntry(
    @SerializedName("region_id")
    val regionId: Int = 0,
    @SerializedName("outer")
    val outer: List<RegionEdgeRefEntry>? = null,
    @SerializedName("holes")
    val holes: List<List<RegionEdgeRefEntry>?>? = emptyList(),
    @SerializedName("boundary")
    val boundary: List<RegionEdgeRefEntry>? = emptyList(),
)

private data class RegionEdgeRefEntry(
    @SerializedName("edge_id")
    val edgeId: Int = 0,
    @SerializedName("reversed")
    val reversed: Boolean = false,
)
