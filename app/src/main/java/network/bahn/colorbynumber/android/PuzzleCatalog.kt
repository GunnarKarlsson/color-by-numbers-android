package network.bahn.colorbynumber.android

data class PuzzleListItem(
    val id: String,
    val displayName: String,
    val puzzleAssetPath: String,
    val previewAssetPath: String,
)

object PuzzleCatalog {
    val items: List<PuzzleListItem> = listOf(
        PuzzleListItem(
            id = "topology_new_3",
            displayName = "Topology New 3",
            puzzleAssetPath = "puzzles/topology_new_3.cbn",
            previewAssetPath = "previews/topology_new_3_preview.png",
        ),
        PuzzleListItem(
            id = "flower",
            displayName = "Flower",
            puzzleAssetPath = "puzzles/flower.cbn",
            previewAssetPath = "previews/flower_preview.png",
        ),
    )

    fun findById(id: String?): PuzzleListItem? =
        items.firstOrNull { it.id == id }

    val defaultItem: PuzzleListItem
        get() = items.first()
}
