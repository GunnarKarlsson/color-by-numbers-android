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
            )

            val progressFile = store.progressFileFor("puzzles/topology_new_3.cbn")
            assertTrue(progressFile.exists())
            assertEquals("topology_new_3.cbn.progress.json", progressFile.name)
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

            store.saveProgress(assetPath, mapOf(2 to 2))
            val progressFile = store.progressFileFor(assetPath)
            assertTrue(progressFile.exists())

            store.saveProgress(assetPath, emptyMap())

            assertFalse(progressFile.exists())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun sampleDocument(): PuzzleDocument =
        PuzzleDocument(
            version = 1,
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
            topology = DocumentTopology(
                vertices = emptyList(),
                edges = emptyList(),
                regions = emptyList(),
            ),
        )
}
