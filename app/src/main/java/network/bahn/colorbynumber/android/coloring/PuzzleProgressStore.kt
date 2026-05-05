package network.bahn.colorbynumber.android.coloring

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import java.io.File

class PuzzleProgressStore(
    private val baseDirectory: File,
) {
    fun loadProgressSummary(assetPath: String): PuzzleProgressSummary? {
        val progressFile = progressFileFor(assetPath)
        if (!progressFile.exists()) {
            return null
        }

        val progress = try {
            gson.fromJson(progressFile.readText(), ProgressFile::class.java)
        } catch (_: JsonSyntaxException) {
            return null
        }

        val completedRegions = progress.completedRegions ?: return null
        val totalRegions = progress.totalRegions ?: return null
        return PuzzleProgressSummary(
            completedRegions = completedRegions,
            totalRegions = totalRegions,
        )
    }

    fun loadProgress(assetPath: String, document: PuzzleDocument): Map<Int, Int> {
        val progressFile = progressFileFor(assetPath)
        if (!progressFile.exists()) {
            return emptyMap()
        }

        val progress = try {
            gson.fromJson(progressFile.readText(), ProgressFile::class.java)
        } catch (_: JsonSyntaxException) {
            return emptyMap()
        }

        val targetPaletteByFillId = when (document.imageType) {
            ImageType.Pixelated -> document.pixelGrid
                ?.cells
                ?.associate { cell -> cell.id to cell.targetPaletteId }
                .orEmpty()
            ImageType.Standard -> document.regions.associate { region -> region.id to region.targetPaletteId }
        }
        return buildMap {
            progress.filledRegions.orEmpty().forEach { entry ->
                val regionId = entry.regionId ?: return@forEach
                val paletteColorId = entry.paletteColorId ?: return@forEach
                if (targetPaletteByFillId[regionId] == paletteColorId) {
                    put(regionId, paletteColorId)
                }
            }
        }
    }

    fun saveProgress(assetPath: String, fillsByRegionId: Map<Int, Int>, totalRegions: Int) {
        val progressFile = progressFileFor(assetPath)
        if (fillsByRegionId.isEmpty()) {
            progressFile.delete()
            return
        }

        progressFile.parentFile?.mkdirs()
        val progress = ProgressFile(
            version = CURRENT_VERSION,
            completedRegions = fillsByRegionId.size,
            totalRegions = totalRegions,
            filledRegions = fillsByRegionId.toSortedMap().map { (regionId, paletteColorId) ->
                RegionFillEntry(
                    regionId = regionId,
                    paletteColorId = paletteColorId,
                )
            },
        )

        progressFile.writeText(gson.toJson(progress))
    }

    internal fun progressFileFor(assetPath: String): File {
        val relativePath = "$assetPath.progress.json"
        return File(baseDirectory, relativePath)
    }

    private companion object {
        const val CURRENT_VERSION = 1
        val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .create()
    }
}

private data class ProgressFile(
    @SerializedName("version")
    val version: Int = 1,
    @SerializedName("completed_regions")
    val completedRegions: Int? = null,
    @SerializedName("total_regions")
    val totalRegions: Int? = null,
    @SerializedName("filled_regions")
    val filledRegions: List<RegionFillEntry>? = emptyList(),
)

private data class RegionFillEntry(
    @SerializedName("region_id")
    val regionId: Int? = null,
    @SerializedName("palette_color_id")
    val paletteColorId: Int? = null,
)

data class PuzzleProgressSummary(
    val completedRegions: Int,
    val totalRegions: Int,
)
