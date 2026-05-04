package network.bahn.colorbynumber.android

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder

data class PuzzleListItem(
    val id: String,
    val displayName: String,
    val puzzleAssetPath: String,
    val previewAssetPath: String,
)

object PuzzleCatalog {
    private val gson: Gson = GsonBuilder().create()

    @Volatile
    private var cachedItems: List<PuzzleListItem>? = null

    fun initialize(context: Context) {
        if (cachedItems != null) {
            return
        }

        synchronized(this) {
            if (cachedItems == null) {
                cachedItems = loadItems(context.applicationContext)
            }
        }
    }

    val items: List<PuzzleListItem>
        get() = requireNotNull(cachedItems) {
            "PuzzleCatalog has not been initialized. Call PuzzleCatalog.initialize(context) during app startup."
        }

    fun findById(id: String?): PuzzleListItem? =
        items.firstOrNull { it.id == id }

    val defaultItem: PuzzleListItem
        get() = items.first()

    private fun loadItems(context: Context): List<PuzzleListItem> {
        val json = context.assets.open(PUZZLE_LIST_ASSET_PATH).bufferedReader().use { it.readText() }
        val loadedItems = gson.fromJson(json, Array<PuzzleListItem>::class.java)?.toList().orEmpty()
        require(loadedItems.isNotEmpty()) { "Puzzle list asset is empty." }
        return loadedItems
    }

    private const val PUZZLE_LIST_ASSET_PATH = "puzzlelist.json"
}
