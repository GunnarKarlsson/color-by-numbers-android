package network.bahn.colorbynumber.android.coloring

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleProgressStoreTest {
    @Test
    fun `saveProgress writes mirrored sidecar path`() {
        val tempDir = Files.createTempDirectory("puzzle-progress-store")

        try {
            val store = PuzzleProgressStore(tempDir.toFile())

            store.saveProgress(
                assetPath = "puzzles/topology_new_3.cbn",
                fillsByRegionId = mapOf(9 to 6, 3 to 4),
                totalRegions = 8,
            )

            val progressFile = store.progressFileFor("puzzles/topology_new_3.cbn")
            assertTrue(progressFile.exists())
            assertEquals("topology_new_3.cbn.progress.json", progressFile.name)
            assertTrue(progressFile.readText().contains("\"completed_regions\": 2"))
            assertTrue(progressFile.readText().contains("\"total_regions\": 8"))
            assertTrue(progressFile.readText().contains("\"region_id\": 3"))
            assertTrue(progressFile.readText().contains("\"palette_color_id\": 6"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `loadProgress restores only fills that still match target palette`() {
        val tempDir = Files.createTempDirectory("puzzle-progress-load")

        try {
            val store = PuzzleProgressStore(tempDir.toFile())
            val document = sampleDocument()

            store.saveProgress(
                assetPath = "puzzles/sample.cbn",
                fillsByRegionId = mapOf(
                    2 to 2,
                    3 to 999,
                    999 to 4,
                ),
                totalRegions = 2,
            )

            val restored = store.loadProgress("puzzles/sample.cbn", document)

            assertEquals(mapOf(2 to 2), restored)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `saveProgress deletes file when there are no fills`() {
        val tempDir = Files.createTempDirectory("puzzle-progress-empty")

        try {
            val store = PuzzleProgressStore(tempDir.toFile())
            val assetPath = "puzzles/sample.cbn"

            store.saveProgress(assetPath, mapOf(2 to 2), totalRegions = 2)
            val progressFile = store.progressFileFor(assetPath)
            assertTrue(progressFile.exists())

            store.saveProgress(assetPath, emptyMap(), totalRegions = 2)

            assertFalse(progressFile.exists())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `loadProgress restores matching pixelated cell fills`() {
        val tempDir = Files.createTempDirectory("puzzle-progress-pixel")

        try {
            val store = PuzzleProgressStore(tempDir.toFile())
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
                        PixelCell(id = 2, row = 0, col = 1, targetPaletteId = 2),
                    ),
                ),
                topology = DocumentTopology(
                    vertices = emptyList(),
                    edges = emptyList(),
                    regions = emptyList(),
                ),
            )

            store.saveProgress(
                assetPath = "puzzles/pixel.cbn",
                fillsByRegionId = mapOf(1 to 1, 2 to 999),
                totalRegions = 2,
            )

            val restored = store.loadProgress("puzzles/pixel.cbn", document)

            assertEquals(mapOf(1 to 1), restored)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `loadProgressSummary returns stored completed and total counts`() {
        val tempDir = Files.createTempDirectory("puzzle-progress-summary")

        try {
            val store = PuzzleProgressStore(tempDir.toFile())
            val assetPath = "puzzles/sample.cbn"

            store.saveProgress(
                assetPath = assetPath,
                fillsByRegionId = mapOf(2 to 2, 3 to 4),
                totalRegions = 5,
            )

            val summary = store.loadProgressSummary(assetPath)

            assertEquals(PuzzleProgressSummary(completedRegions = 2, totalRegions = 5), summary)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun sampleDocument(): PuzzleDocument =
        PuzzleDocument(
            version = 1,
            imageType = ImageType.Standard,
            bounds = PuzzlePoint(100f, 100f),
            regions = listOf(
                PuzzleRegion(
                    id = 2,
                    number = 2,
                    numberPosition = PuzzlePoint(10f, 10f),
                    targetPaletteId = 2,
                ),
                PuzzleRegion(
                    id = 3,
                    number = 4,
                    numberPosition = PuzzlePoint(20f, 20f),
                    targetPaletteId = 4,
                ),
            ),
            embeddedPalette = emptyList(),
            paletteLink = null,
            pixelGrid = null,
            topology = DocumentTopology(
                vertices = emptyList(),
                edges = emptyList(),
                regions = emptyList(),
            ),
        )
}
