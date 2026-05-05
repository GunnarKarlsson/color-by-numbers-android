package network.bahn.colorbynumber.android.coloring

import androidx.compose.ui.graphics.Color

data class PuzzlePoint(
    val x: Float,
    val y: Float,
)

data class PuzzleBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    val width: Float = (maxX - minX).coerceAtLeast(1f)
    val height: Float = (maxY - minY).coerceAtLeast(1f)

    companion object {
        fun fromPoints(points: Collection<PuzzlePoint>, fallbackWidth: Float, fallbackHeight: Float): PuzzleBounds {
            if (points.isEmpty()) {
                return centered(fallbackWidth, fallbackHeight)
            }

            return PuzzleBounds(
                minX = points.minOf { it.x },
                minY = points.minOf { it.y },
                maxX = points.maxOf { it.x },
                maxY = points.maxOf { it.y },
            )
        }

        fun centered(width: Float, height: Float): PuzzleBounds {
            val halfWidth = width / 2f
            val halfHeight = height / 2f
            return PuzzleBounds(
                minX = -halfWidth,
                minY = -halfHeight,
                maxX = halfWidth,
                maxY = halfHeight,
            )
        }
    }
}

data class PaletteColor(
    val id: Int,
    val label: String,
    val rgba: IntArray,
) {
    init {
        require(rgba.size == 4) { "Palette colors must contain 4 RGBA values." }
    }

    val composeColor: Color
        get() = Color(
            red = rgba[0],
            green = rgba[1],
            blue = rgba[2],
            alpha = rgba[3],
        )
}

data class PaletteLink(
    val paletteId: String,
    val path: String,
)

enum class ImageType {
    Standard,
    Pixelated,
}

data class PuzzleRegion(
    val id: Int,
    val number: Int,
    val numberPosition: PuzzlePoint,
    val targetPaletteId: Int?,
)

data class PixelCell(
    val id: Int,
    val row: Int,
    val col: Int,
    val targetPaletteId: Int?,
)

data class PixelGridDocument(
    val rows: Int,
    val cols: Int,
    val cells: List<PixelCell>,
)

data class TopologyVertex(
    val id: Int,
    val pos: PuzzlePoint,
)

data class TopologyEdge(
    val id: Int,
    val start: Int,
    val end: Int,
)

data class RegionEdgeRef(
    val edgeId: Int,
    val reversed: Boolean,
)

data class TopologyRegionBoundary(
    val regionId: Int,
    val outer: List<RegionEdgeRef>,
    val holes: List<List<RegionEdgeRef>>,
)

data class DocumentTopology(
    val vertices: List<TopologyVertex>,
    val edges: List<TopologyEdge>,
    val regions: List<TopologyRegionBoundary>,
)

data class PuzzleDocument(
    val version: Int,
    val imageType: ImageType,
    val bounds: PuzzlePoint,
    val regions: List<PuzzleRegion>,
    val embeddedPalette: List<PaletteColor>,
    val paletteLink: PaletteLink?,
    val pixelGrid: PixelGridDocument?,
    val topology: DocumentTopology,
)

data class OutlineSegment(
    val start: PuzzlePoint,
    val end: PuzzlePoint,
)

data class RenderShape(
    val outer: List<PuzzlePoint>,
    val holes: List<List<PuzzlePoint>>,
)

data class RenderRegion(
    val region: PuzzleRegion,
    val shape: RenderShape,
)

data class LoadedPuzzle(
    val document: PuzzleDocument,
    val palette: List<PaletteColor>,
    val renderRegions: List<RenderRegion>,
    val outlineSegments: List<OutlineSegment>,
    val worldBounds: PuzzleBounds,
) {
    val isPixelated: Boolean
        get() = document.imageType == ImageType.Pixelated

    val totalFillTargets: Int
        get() = document.pixelGrid?.cells?.size ?: document.regions.size
}

data class PuzzleSession(
    val selectedPaletteId: Int? = null,
    val fillsByRegionId: Map<Int, Int> = emptyMap(),
    val fillHistory: List<Int> = emptyList(),
) {
    val filledCount: Int
        get() = fillsByRegionId.size
}
