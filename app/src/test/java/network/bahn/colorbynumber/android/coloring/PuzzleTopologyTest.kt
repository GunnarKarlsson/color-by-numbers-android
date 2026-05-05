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
    fun `hitTestCell returns matching pixel cell`() {
        val document = PuzzleDocument(
            version = 1,
            imageType = ImageType.Pixelated,
            bounds = PuzzlePoint(40f, 40f),
            regions = emptyList(),
            embeddedPalette = emptyList(),
            paletteLink = null,
            pixelGrid = PixelGridDocument(
                rows = 2,
                cols = 2,
                cells = listOf(
                    PixelCell(id = 1, row = 0, col = 0, targetPaletteId = 1),
                    PixelCell(id = 2, row = 0, col = 1, targetPaletteId = 1),
                    PixelCell(id = 3, row = 1, col = 0, targetPaletteId = 2),
                    PixelCell(id = 4, row = 1, col = 1, targetPaletteId = 2),
                ),
            ),
            topology = DocumentTopology(
                vertices = emptyList(),
                edges = emptyList(),
                regions = emptyList(),
            ),
        )

        val hit = PuzzleTopology.hitTestCell(document, PuzzlePoint(25f, 5f))
        val miss = PuzzleTopology.hitTestCell(document, PuzzlePoint(50f, 5f))

        assertEquals(2, hit?.id)
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

    @Test
    fun `regionShape drops forward backward edge loop`() {
        val topology = DocumentTopology(
            vertices = listOf(
                TopologyVertex(1, PuzzlePoint(0f, 0f)),
                TopologyVertex(2, PuzzlePoint(10f, 0f)),
            ),
            edges = listOf(
                TopologyEdge(1, start = 1, end = 2),
            ),
            regions = listOf(
                TopologyRegionBoundary(
                    regionId = 8,
                    outer = listOf(
                        RegionEdgeRef(edgeId = 1, reversed = false),
                        RegionEdgeRef(edgeId = 1, reversed = true),
                    ),
                    holes = emptyList(),
                ),
            ),
        )

        val shape = PuzzleTopology.regionShape(topology, regionId = 8)

        assertNull(shape)
    }

    @Test
    fun `regionShape drops tiny sliver polygon`() {
        val topology = DocumentTopology(
            vertices = listOf(
                TopologyVertex(20, PuzzlePoint(430.61777f, 337.41113f)),
                TopologyVertex(21, PuzzlePoint(399.122f, 340.46884f)),
                TopologyVertex(22, PuzzlePoint(465.73868f, 334.00146f)),
            ),
            edges = listOf(
                TopologyEdge(20, start = 20, end = 21),
                TopologyEdge(21, start = 21, end = 22),
                TopologyEdge(22, start = 22, end = 20),
            ),
            regions = listOf(
                TopologyRegionBoundary(
                    regionId = 6,
                    outer = listOf(
                        RegionEdgeRef(edgeId = 20, reversed = false),
                        RegionEdgeRef(edgeId = 21, reversed = false),
                        RegionEdgeRef(edgeId = 22, reversed = false),
                    ),
                    holes = emptyList(),
                ),
            ),
        )

        val shape = PuzzleTopology.regionShape(topology, regionId = 6)

        assertNull(shape)
    }
}
