package network.bahn.colorbynumber.android

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import network.bahn.colorbynumber.android.coloring.PuzzleAssetLoader
import network.bahn.colorbynumber.android.coloring.PuzzleProgressStore
import network.bahn.colorbynumber.android.coloring.PuzzleProgressSummary
import network.bahn.colorbynumber.android.ui.theme.ColorByNumberTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ColorByNumberTheme {
                PuzzleGridRoute(
                    onPuzzleSelected = { puzzleItem ->
                        startActivity(ColoringActivity.createIntent(this, puzzleItem))
                    },
                )
            }
        }
    }
}

@Composable
private fun PuzzleGridRoute(
    onPuzzleSelected: (PuzzleListItem) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val loader = androidx.compose.runtime.remember(context) { PuzzleAssetLoader(context) }
    val progressStore = androidx.compose.runtime.remember(context) { PuzzleProgressStore(context.filesDir) }
    var refreshKey by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val progressByPuzzleId by produceState<Map<String, PuzzleProgressSummary>>(
        initialValue = emptyMap(),
        context,
        loader,
        progressStore,
        refreshKey,
    ) {
        value = withContext(Dispatchers.IO) {
            PuzzleCatalog.items.associate { puzzle ->
                val totalRegions = loader.load(puzzle.puzzleAssetPath).document.regions.size
                val summary = progressStore.loadProgressSummary(puzzle.puzzleAssetPath)
                    ?: PuzzleProgressSummary(
                        completedRegions = 0,
                        totalRegions = totalRegions,
                    )
                puzzle.id to summary
            }
        }
    }

    PuzzleGridScreen(
        puzzles = PuzzleCatalog.items,
        progressByPuzzleId = progressByPuzzleId,
        onPuzzleSelected = onPuzzleSelected,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PuzzleGridScreen(
    puzzles: List<PuzzleListItem>,
    progressByPuzzleId: Map<String, PuzzleProgressSummary>,
    onPuzzleSelected: (PuzzleListItem) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(text = stringResource(R.string.image_grid_title))
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(puzzles, key = { it.id }) { puzzle ->
                    PuzzleGridCard(
                        puzzle = puzzle,
                        progressSummary = progressByPuzzleId[puzzle.id],
                        onClick = { onPuzzleSelected(puzzle) },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(56.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.small,
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.small,
                    ),
            ) {
            }
        }
    }
}

@Composable
private fun PuzzleGridCard(
    puzzle: PuzzleListItem,
    progressSummary: PuzzleProgressSummary?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            PuzzlePreviewImage(
                assetPath = puzzle.previewAssetPath,
                contentDescription = puzzle.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
            Text(
                text = puzzle.displayName,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = progressSummary?.let {
                    stringResource(
                        R.string.image_grid_progress,
                        it.completedRegions,
                        it.totalRegions,
                    )
                } ?: stringResource(R.string.image_grid_progress_loading),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PuzzlePreviewImage(
    assetPath: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, context, assetPath) {
        value = withContext(Dispatchers.IO) {
            context.assets.open(assetPath).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}