package network.bahn.colorbynumber.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import network.bahn.colorbynumber.android.coloring.PaletteColor
import network.bahn.colorbynumber.android.coloring.PuzzleBounds
import network.bahn.colorbynumber.android.coloring.PuzzlePoint
import network.bahn.colorbynumber.android.coloring.PuzzleSession
import network.bahn.colorbynumber.android.coloring.SvgPuzzle
import network.bahn.colorbynumber.android.coloring.SvgPuzzleAssetLoader
import network.bahn.colorbynumber.android.ui.theme.ColorByNumberTheme

class ColoringActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val puzzleItem = PuzzleCatalog.findById(intent.getStringExtra(EXTRA_PUZZLE_ID)) ?: PuzzleCatalog.defaultItem
        enableEdgeToEdge()
        setContent {
            ColorByNumberTheme {
                ColoringRoute(
                    puzzleItem = puzzleItem,
                    onNavigateBack = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PUZZLE_ID = "puzzle_id"

        fun createIntent(context: Context, puzzleItem: PuzzleListItem): Intent =
            Intent(context, ColoringActivity::class.java).putExtra(EXTRA_PUZZLE_ID, puzzleItem.id)
    }
}

private sealed interface ColoringUiState {
    data object Loading : ColoringUiState
    data class Success(
        val puzzleItem: PuzzleListItem,
        val puzzle: SvgPuzzle,
    ) : ColoringUiState

    data class Error(val message: String) : ColoringUiState
}

@Composable
private fun ColoringRoute(
    puzzleItem: PuzzleListItem,
    onNavigateBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val loader = remember(context) { SvgPuzzleAssetLoader(context) }
    val state by produceState<ColoringUiState>(initialValue = ColoringUiState.Loading, loader, puzzleItem) {
        value = try {
            val puzzle = withContext(Dispatchers.IO) {
                loader.load(puzzleItem.assetPath)
            }
            ColoringUiState.Success(
                puzzleItem = puzzleItem,
                puzzle = puzzle,
            )
        } catch (error: Exception) {
            ColoringUiState.Error(
                error.message ?: context.getString(R.string.coloring_load_failed),
            )
        }
    }

    ColoringScreen(
        state = state,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ColoringScreen(
    state: ColoringUiState,
    onNavigateBack: () -> Unit,
) {
    when (state) {
        ColoringUiState.Loading -> Scaffold(
            topBar = {
                ColoringTopBar(
                    title = stringResource(R.string.coloring_title),
                    onNavigateBack = onNavigateBack,
                )
            },
        ) { innerPadding ->
            LoadingState(modifier = Modifier.padding(innerPadding))
        }

        is ColoringUiState.Error -> Scaffold(
            topBar = {
                ColoringTopBar(
                    title = stringResource(R.string.coloring_title),
                    onNavigateBack = onNavigateBack,
                )
            },
        ) { innerPadding ->
            ErrorState(
                message = state.message,
                modifier = Modifier.padding(innerPadding),
            )
        }

        is ColoringUiState.Success -> {
            var session by remember(state.puzzleItem.id) {
                mutableStateOf(PuzzleSession())
            }

            Scaffold(
                topBar = {
                    ColoringTopBar(
                        title = state.puzzleItem.displayName,
                        onNavigateBack = onNavigateBack,
                        clearEnabled = session.fillHistory.isNotEmpty(),
                        clearAllEnabled = session.fillsByRegionId.isNotEmpty(),
                        onClear = { session = session.undoLastFill() },
                        onClearAll = {
                            session = PuzzleSession(selectedPaletteId = session.selectedPaletteId)
                        },
                    )
                },
            ) { innerPadding ->
                PuzzleContent(
                    puzzle = state.puzzle,
                    session = session,
                    onSessionChanged = { session = it },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ColoringTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    clearEnabled: Boolean = false,
    clearAllEnabled: Boolean = false,
    onClear: (() -> Unit)? = null,
    onClearAll: (() -> Unit)? = null,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.coloring_back),
                )
            }
        },
        title = { Text(text = title) },
        actions = {
            if (onClear != null) {
                IconButton(
                    enabled = clearEnabled,
                    onClick = onClear,
                ) {
                    Icon(
                        imageVector = Icons.Filled.FastRewind,
                        contentDescription = stringResource(R.string.coloring_clear_last),
                    )
                }
            }
            if (onClearAll != null) {
                IconButton(
                    enabled = clearAllEnabled,
                    onClick = onClearAll,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(R.string.coloring_clear_all),
                    )
                }
            }
        },
    )
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun PuzzleContent(
    puzzle: SvgPuzzle,
    session: PuzzleSession,
    onSessionChanged: (PuzzleSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    val puzzleAspectRatio = remember(puzzle.worldBounds) {
        (puzzle.worldBounds.width / puzzle.worldBounds.height).takeIf { it > 0f } ?: 1f
    }
    val overallProgress = remember(session, puzzle) {
        puzzle.overallProgress(session)
    }
    val selectedProgress = remember(session, puzzle) {
        puzzle.selectedColorProgress(session)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProgressBadge(
                filledCount = overallProgress.first,
                totalRegions = overallProgress.second,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(
                        R.string.coloring_progress,
                        overallProgress.first,
                        overallProgress.second,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = selectedProgress?.let { (completed, total) ->
                        stringResource(R.string.coloring_selected_color_progress, completed, total)
                    } ?: stringResource(R.string.coloring_no_palette_selected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            SvgPuzzleCanvas(
                puzzle = puzzle,
                session = session,
                onPuzzleTapped = { worldPoint ->
                    val selectedPaletteId = session.selectedPaletteId ?: return@SvgPuzzleCanvas
                    val x = worldPoint.x.toInt()
                    val y = worldPoint.y.toInt()
                    val region = puzzle.regionAt(x, y) ?: return@SvgPuzzleCanvas
                    if (!region.isPlayable || region.targetPaletteId != selectedPaletteId) {
                        return@SvgPuzzleCanvas
                    }
                    if (session.fillsByRegionId.containsKey(region.id)) {
                        return@SvgPuzzleCanvas
                    }

                    onSessionChanged(
                        session.copy(
                            fillsByRegionId = session.fillsByRegionId + (region.id to selectedPaletteId),
                            fillHistory = session.fillHistory + region.id,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(puzzleAspectRatio),
            )
        }

        PaletteStrip(
            colors = puzzle.palette,
            selectedPaletteId = session.selectedPaletteId,
            onPaletteSelected = { paletteId ->
                onSessionChanged(
                    session.copy(
                        selectedPaletteId = if (session.selectedPaletteId == paletteId) null else paletteId,
                    ),
                )
            },
        )
    }
}

@Composable
private fun ProgressBadge(
    filledCount: Int,
    totalRegions: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$filledCount/$totalRegions",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SvgPuzzleCanvas(
    puzzle: SvgPuzzle,
    session: PuzzleSession,
    onPuzzleTapped: (PuzzlePoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnPuzzleTapped by rememberUpdatedState(onPuzzleTapped)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var viewport by remember(puzzle) { mutableStateOf(PuzzleViewport()) }
    val displayBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, puzzle, session) {
        value = withContext(Dispatchers.Default) {
            puzzle.composeDisplayBitmap(session)
        }
    }

    val canvasPaddingPx = with(androidx.compose.ui.platform.LocalDensity.current) { 2.dp.toPx() }
    val baseTransform = remember(canvasSize, puzzle.worldBounds, canvasPaddingPx) {
        if (canvasSize == IntSize.Zero) {
            null
        } else {
            ScreenTransform.fit(
                canvasSize = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
                bounds = puzzle.worldBounds,
                padding = canvasPaddingPx,
            )
        }
    }
    val transform = remember(baseTransform, viewport) {
        baseTransform?.applyViewport(viewport)
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(baseTransform, viewport) {
                detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                    val currentBaseTransform = baseTransform ?: return@detectTransformGestures
                    val oldZoom = viewport.zoom
                    val newZoom = (oldZoom * gestureZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    val scaleFactor = newZoom / oldZoom
                    val currentTransform = currentBaseTransform.applyViewport(viewport)
                    val currentOffset = Offset(currentTransform.offsetX, currentTransform.offsetY)
                    val baseOffset = Offset(currentBaseTransform.offsetX, currentBaseTransform.offsetY)
                    val newEffectiveOffset = Offset(
                        x = centroid.x - ((centroid.x - currentOffset.x) * scaleFactor) + gesturePan.x,
                        y = centroid.y - ((centroid.y - currentOffset.y) * scaleFactor) + gesturePan.y,
                    )

                    viewport = PuzzleViewport(
                        zoom = newZoom,
                        pan = Offset(
                            x = newEffectiveOffset.x - baseOffset.x,
                            y = newEffectiveOffset.y - baseOffset.y,
                        ),
                    )
                }
            }
            .pointerInput(transform, puzzle) {
                detectTapGestures { offset ->
                    val currentTransform = transform ?: return@detectTapGestures
                    currentOnPuzzleTapped(currentTransform.toWorld(offset))
                }
            },
    ) {
        drawRect(color = Color.White)
        val currentTransform = transform ?: return@Canvas
        val imageBitmap = displayBitmap ?: return@Canvas

        drawBitmapToBounds(
            bitmap = imageBitmap,
            transform = currentTransform,
            puzzleWidth = puzzle.width,
            puzzleHeight = puzzle.height,
        )
        drawBitmapToBounds(
            bitmap = puzzle.lineBitmap,
            transform = currentTransform,
            puzzleWidth = puzzle.width,
            puzzleHeight = puzzle.height,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBitmapToBounds(
    bitmap: android.graphics.Bitmap,
    transform: ScreenTransform,
    puzzleWidth: Int,
    puzzleHeight: Int,
) {
    val topLeft = transform.toScreen(PuzzlePoint(0f, 0f))
    val bottomRight = transform.toScreen(PuzzlePoint(puzzleWidth.toFloat(), puzzleHeight.toFloat()))
    val destinationWidth = (bottomRight.x - topLeft.x).coerceAtLeast(1f).roundToInt()
    val destinationHeight = (bottomRight.y - topLeft.y).coerceAtLeast(1f).roundToInt()
    drawImage(
        image = bitmap.asImageBitmap(),
        dstOffset = IntOffset(
            x = topLeft.x.roundToInt(),
            y = topLeft.y.roundToInt(),
        ),
        dstSize = IntSize(
            width = destinationWidth,
            height = destinationHeight,
        ),
    )
}

@Composable
private fun PaletteStrip(
    colors: List<PaletteColor>,
    selectedPaletteId: Int?,
    onPaletteSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.palette_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            colors.forEach { paletteColor ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val isSelected = selectedPaletteId == paletteColor.id
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onPaletteSelected(paletteColor.id) }
                            .size(40.dp)
                            .border(
                                width = if (isSelected) 4.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = MaterialTheme.shapes.small,
                            )
                            .background(
                                color = paletteColor.composeColor,
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                    Text(
                        text = paletteColor.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun PuzzleSession.undoLastFill(): PuzzleSession {
    val regionId = fillHistory.lastOrNull() ?: return this
    return copy(
        fillsByRegionId = fillsByRegionId - regionId,
        fillHistory = fillHistory.dropLast(1),
    )
}

private data class ScreenTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    fun toScreen(point: PuzzlePoint): Offset =
        Offset(
            x = offsetX + (point.x * scale),
            y = offsetY + (point.y * scale),
        )

    fun toWorld(point: Offset): PuzzlePoint =
        PuzzlePoint(
            x = (point.x - offsetX) / scale,
            y = (point.y - offsetY) / scale,
        )

    fun applyViewport(viewport: PuzzleViewport): ScreenTransform =
        copy(
            scale = scale * viewport.zoom,
            offsetX = offsetX + viewport.pan.x,
            offsetY = offsetY + viewport.pan.y,
        )

    companion object {
        fun fit(canvasSize: Size, bounds: PuzzleBounds, padding: Float): ScreenTransform {
            val availableWidth = (canvasSize.width - (padding * 2f)).coerceAtLeast(1f)
            val availableHeight = (canvasSize.height - (padding * 2f)).coerceAtLeast(1f)
            val scale = minOf(availableWidth / bounds.width, availableHeight / bounds.height)

            val contentWidth = bounds.width * scale
            val contentHeight = bounds.height * scale
            val offsetX = ((canvasSize.width - contentWidth) / 2f) - (bounds.minX * scale)
            val offsetY = ((canvasSize.height - contentHeight) / 2f) - (bounds.minY * scale)

            return ScreenTransform(
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
            )
        }
    }
}

private data class PuzzleViewport(
    val zoom: Float = 1f,
    val pan: Offset = Offset.Zero,
)

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 6f
