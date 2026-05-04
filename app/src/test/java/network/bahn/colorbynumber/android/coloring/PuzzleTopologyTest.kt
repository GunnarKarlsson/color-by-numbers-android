package network.bahn.colorbynumber.android.coloring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleTopologyTest {
    @Test
    fun `regionShape respects reversed edges`() {
        val topology = DocumentTopology(
            vertices = listOf(
                TopologyVertex(1, PuzzlePoint(0f, 0f)),
                TopologyVertex(2, PuzzlePoint(10f, 0f)),
                TopologyVertex(3, PuzzlePoint(10f, 10f)),
                TopologyVertex(4, PuzzlePoint(0f, 10f)),
            ),
            edges = listOf(
                TopologyEdge(1, start = 1, end = 2),
                TopologyEdge(2, start = 3, end = 2),
                TopologyEdge(3, start = 4, end = 3),
                TopologyEdge(4, start = 1, end = 4),
            ),
            regions = listOf(
                TopologyRegionBoundary(
                    regionId = 99,
                    outer = listOf(
                        RegionEdgeRef(edgeId = 1, reversed = false),
                        RegionEdgeRef(edgeId = 2, reversed = true),
                        RegionEdgeRef(edgeId = 3, reversed = true),
                        RegionEdgeRef(edgeId = 4, reversed = true),
                    ),
                    holes = emptyList(),
                ),
            ),
        )

        val shape = PuzzleTopology.regionShape(topology, regionId = 99)

        assertNotNull(shape)
        assertEquals(
            listOf(
                PuzzlePoint(0f, 0f),
                PuzzlePoint(10f, 0f),
                PuzzlePoint(10f, 10f),
                PuzzlePoint(0f, 10f),
            ),
            shape?.outer,
        )
    }

    @Test
    fun `uniqueSegments returns each stored edge once`() {
        val topology = DocumentTopology(
            vertices = listOf(
                TopologyVertex(1, PuzzlePoint(0f, 0f)),
                TopologyVertex(2, PuzzlePoint(10f, 0f)),
            ),
            edges = listOf(
                TopologyEdge(1, start = 1, end = 2),
            ),
            regions = emptyList(),
        )

        val segments = PuzzleTopology.uniqueSegments(topology)

        assertEquals(1, segments.size)
        assertEquals(OutlineSegment(PuzzlePoint(0f, 0f), PuzzlePoint(10f, 0f)), segments.single())
    }

    @Test
    fun `pointInPolygon identifies points inside the region`() {
        val polygon = listOf(
            PuzzlePoint(0f, 0f),
            PuzzlePoint(10f, 0f),
            PuzzlePoint(10f, 10f),
            PuzzlePoint(0f, 10f),
        )

        assertTrue(PuzzleTopology.pointInPolygon(PuzzlePoint(5f, 5f), polygon))
        assertEquals(false, PuzzleTopology.pointInPolygon(PuzzlePoint(15f, 5f), polygon))
    }

    @Test
    fun `hitTestRegion returns matching render region`() {
        val region = RenderRegion(
            region = PuzzleRegion(
                id = 7,
                number = 3,
                numberPosition = PuzzlePoint(5f, 5f),
                targetPaletteId = 2,
            ),
            shape = RenderShape(
                outer = listOf(
                    PuzzlePoint(0f, 0f),
                    PuzzlePoint(10f, 0f),
                    PuzzlePoint(10f, 10f),
                    PuzzlePoint(0f, 10f),
                ),
                holes = emptyList(),
            ),
        )

        val hit = PuzzleTopology.hitTestRegion(
            renderRegions = listOf(region),
            point = PuzzlePoint(4f, 4f),
        )
        val miss = PuzzleTopology.hitTestRegion(
            renderRegions = listOf(region),
            point = PuzzlePoint(20f, 20f),
        )

        assertEquals(region, hit)
        assertNull(miss)
    }

    @Test
    fun `pointInShape excludes holes`() {
        val shape = RenderShape(
            outer = listOf(
                PuzzlePoint(0f, 0f),
                PuzzlePoint(10f, 0f),
                PuzzlePoint(10f, 10f),
                PuzzlePoint(0f, 10f),
            ),
            holes = listOf(
                listOf(
                    PuzzlePoint(3f, 3f),
                    PuzzlePoint(7f, 3f),
                    PuzzlePoint(7f, 7f),
                    PuzzlePoint(3f, 7f),
                ),
            ),
        )

        assertTrue(PuzzleTopology.pointInShape(PuzzlePoint(1f, 1f), shape))
        assertEquals(false, PuzzleTopology.pointInShape(PuzzlePoint(5f, 5f), shape))
    }
}
