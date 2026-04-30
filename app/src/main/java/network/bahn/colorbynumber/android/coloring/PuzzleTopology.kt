package network.bahn.colorbynumber.android.coloring

import android.util.Log

object PuzzleTopology {
    private const val TAG = "PuzzleTopology"

    fun buildLoadedPuzzle(document: PuzzleDocument, palette: List<PaletteColor>): LoadedPuzzle {
        val renderRegions = document.regions.mapNotNull { region ->
            val polygon = regionPolygon(document.topology, region.id)
            if (polygon == null || polygon.size < 3) {
                Log.w(TAG, "Skipping region ${region.id} because its polygon is invalid.")
                null
            } else {
                RenderRegion(region = region, polygon = polygon)
            }
        }

        val worldPoints = buildList {
            addAll(document.topology.vertices.map { it.pos })
            addAll(renderRegions.flatMap { it.polygon })
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

    fun regionPolygon(topology: DocumentTopology, regionId: Int): List<PuzzlePoint>? {
        val region = topology.regions.firstOrNull { it.regionId == regionId } ?: return null
        if (region.boundary.isEmpty()) {
            return null
        }

        val edgeLookup = topology.edges.associateBy { it.id }
        val vertexLookup = topology.vertices.associateBy { it.id }
        val polygon = mutableListOf<PuzzlePoint>()

        for ((index, edgeRef) in region.boundary.withIndex()) {
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

        if (polygon.firstOrNull() == polygon.lastOrNull()) {
            polygon.removeLast()
        }

        return polygon
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
        renderRegions.firstOrNull { region -> pointInPolygon(point, region.polygon) }

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

    private const val EPSILON = 0.00001f
}
