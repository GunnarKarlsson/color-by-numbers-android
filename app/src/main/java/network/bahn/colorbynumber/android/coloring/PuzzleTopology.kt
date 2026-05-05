package network.bahn.colorbynumber.android.coloring

import android.util.Log

object PuzzleTopology {
    private const val TAG = "PuzzleTopology"
    private const val EPSILON = 0.00001f
    private const val MIN_REGION_AREA = 1f

    fun buildLoadedPuzzle(document: PuzzleDocument, palette: List<PaletteColor>): LoadedPuzzle {
        if (document.imageType == ImageType.Pixelated) {
            return buildLoadedPixelPuzzle(document, palette)
        }

        val renderRegions = document.regions.mapNotNull { region ->
            val shape = regionShape(document.topology, region.id)
            if (shape == null || shape.outer.size < 3) {
                Log.w(TAG, "Skipping region ${region.id} because its shape is invalid.")
                null
            } else {
                RenderRegion(region = region, shape = shape)
            }
        }

        val worldPoints = buildList {
            addAll(document.topology.vertices.map { it.pos })
            renderRegions.forEach { region ->
                addAll(region.shape.outer)
                region.shape.holes.forEach { addAll(it) }
            }
            addAll(document.regions.map { it.numberPosition })
        }

        val worldBounds = PuzzleBounds.fromPoints(
            points = worldPoints,
            fallbackWidth = document.bounds.x,
            fallbackHeight = document.bounds.y,
        )

        return LoadedPuzzle(
            document = document,
            palette = palette,
            renderRegions = renderRegions,
            outlineSegments = uniqueSegments(document.topology),
            worldBounds = worldBounds,
        )
    }

    private fun buildLoadedPixelPuzzle(document: PuzzleDocument, palette: List<PaletteColor>): LoadedPuzzle {
        val bounds = PuzzleBounds(
            minX = 0f,
            minY = 0f,
            maxX = document.bounds.x,
            maxY = document.bounds.y,
        )
        return LoadedPuzzle(
            document = document,
            palette = palette,
            renderRegions = emptyList(),
            outlineSegments = emptyList(),
            worldBounds = bounds,
        )
    }

    fun regionShape(topology: DocumentTopology, regionId: Int): RenderShape? {
        val region = topology.regions.firstOrNull { it.regionId == regionId } ?: return null
        if (region.outer.isEmpty()) {
            return null
        }

        val edgeLookup = topology.edges.associateBy { it.id }
        val vertexLookup = topology.vertices.associateBy { it.id }
        val outer = reconstructLoop(region.outer, edgeLookup, vertexLookup) ?: return null
        val holes = mutableListOf<List<PuzzlePoint>>()
        region.holes.forEach { loop ->
            val hole = reconstructLoop(loop, edgeLookup, vertexLookup) ?: return null
            holes += hole
        }

        return RenderShape(
            outer = outer,
            holes = holes,
        )
    }

    fun uniqueSegments(topology: DocumentTopology): List<OutlineSegment> {
        val vertexLookup = topology.vertices.associateBy { it.id }
        return topology.edges.mapNotNull { edge ->
            val start = vertexLookup[edge.start]?.pos ?: return@mapNotNull null
            val end = vertexLookup[edge.end]?.pos ?: return@mapNotNull null
            OutlineSegment(start = start, end = end)
        }
    }

    fun hitTestRegion(renderRegions: List<RenderRegion>, point: PuzzlePoint): RenderRegion? =
        renderRegions.firstOrNull { region -> pointInShape(point, region.shape) }

    fun hitTestCell(document: PuzzleDocument, point: PuzzlePoint): PixelCell? {
        val grid = document.pixelGrid ?: return null
        if (grid.rows <= 0 || grid.cols <= 0) {
            return null
        }
        if (point.x < 0f || point.y < 0f || point.x >= document.bounds.x || point.y >= document.bounds.y) {
            return null
        }

        val cellWidth = document.bounds.x / grid.cols.toFloat()
        val cellHeight = document.bounds.y / grid.rows.toFloat()
        val col = (point.x / cellWidth).toInt()
        val row = (point.y / cellHeight).toInt()
        return grid.cells.firstOrNull { it.row == row && it.col == col }
    }

    fun pointInShape(point: PuzzlePoint, shape: RenderShape): Boolean =
        pointInPolygon(point, shape.outer) && shape.holes.none { hole -> pointInPolygon(point, hole) }

    fun pointInPolygon(point: PuzzlePoint, polygon: List<PuzzlePoint>): Boolean {
        if (polygon.size < 3) {
            return false
        }

        var inside = false
        var previousIndex = polygon.lastIndex

        for (currentIndex in polygon.indices) {
            val current = polygon[currentIndex]
            val previous = polygon[previousIndex]

            val intersects = ((current.y > point.y) != (previous.y > point.y)) &&
                (
                    point.x <
                        ((previous.x - current.x) * (point.y - current.y) / ((previous.y - current.y) + EPSILON)) +
                        current.x
                    )

            if (intersects) {
                inside = !inside
            }

            previousIndex = currentIndex
        }

        return inside
    }

    private fun reconstructLoop(
        edgeRefs: List<RegionEdgeRef>,
        edgeLookup: Map<Int, TopologyEdge>,
        vertexLookup: Map<Int, TopologyVertex>,
    ): List<PuzzlePoint>? {
        if (edgeRefs.isEmpty()) {
            return null
        }

        val polygon = mutableListOf<PuzzlePoint>()

        for ((index, edgeRef) in edgeRefs.withIndex()) {
            val edge = edgeLookup[edgeRef.edgeId] ?: return null
            val startVertexId = if (edgeRef.reversed) edge.end else edge.start
            val endVertexId = if (edgeRef.reversed) edge.start else edge.end
            val start = vertexLookup[startVertexId]?.pos ?: return null
            val end = vertexLookup[endVertexId]?.pos ?: return null

            if (index == 0) {
                polygon += start
            } else if (polygon.last() != start) {
                return null
            }

            polygon += end
        }

        if (polygon.firstOrNull() != polygon.lastOrNull()) {
            return null
        }

        polygon.removeLast()
        return sanitizeLoop(polygon)
    }

    private fun sanitizeLoop(points: List<PuzzlePoint>): List<PuzzlePoint>? {
        val normalized = mutableListOf<PuzzlePoint>()
        points.forEach { point ->
            if (normalized.lastOrNull() != point) {
                normalized += point
            }
        }

        if (normalized.size >= 2 && normalized.first() == normalized.last()) {
            normalized.removeLast()
        }

        if (normalized.distinct().size < 3) {
            return null
        }

        return normalized.takeIf { polygonArea(it) > MIN_REGION_AREA }
    }

    private fun polygonArea(points: List<PuzzlePoint>): Float {
        if (points.size < 3) {
            return 0f
        }

        var area = 0f
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            area += current.x * next.y - next.x * current.y
        }
        return kotlin.math.abs(area) * 0.5f
    }
}
