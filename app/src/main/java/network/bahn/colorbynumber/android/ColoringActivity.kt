package network.bahn.colorbynumber.android

import android.content.Context
import android.content.Intent
import android.graphics.Paint
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.bahn.colorbynumber.android.coloring.LoadedPuzzle
import network.bahn.colorbynumber.android.coloring.OutlineSegment
import network.bahn.colorbynumber.android.coloring.PaletteColor
import network.bahn.colorbynumber.android.coloring.PuzzleAssetLoader
import network.bahn.colorbynumber.android.coloring.PuzzleBounds
import network.bahn.colorbynumber.android.coloring.PuzzlePoint
import network.bahn.colorbynumber.android.coloring.PuzzleProgressStore
import network.bahn.colorbynumber.android.coloring.PuzzleSession
import network.bahn.colorbynumber.android.coloring.PuzzleTopology
import network.bahn.colorbynumber.android.coloring.RenderShape
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
        val puzzle: LoadedPuzzle,
        val initialSession: PuzzleSession,
    ) : ColoringUiState

    data class Error(val message: String) : ColoringUiState
}

@Composable
private fun ColoringRoute(
    puzzleItem: PuzzleListItem,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val loader = remember(context) { PuzzleAssetLoader(context) }
    val progressStore = remember(context) { PuzzleProgressStore(context.filesDir) }
    val coroutineScope = rememberCoroutineScope()
    val state by produceState<ColoringUiState>(initialValue = ColoringUiState.Loading, loader, progressStore, puzzleItem) {
        value = try {
            val result = withContext(Dispatchers.IO) {
                val puzzle = loader.load(puzzleItem.puzzleAssetPath)
                val restoredFills = progressStore.loadProgress(puzzleItem.puzzleAssetPath, puzzle.document)
                ColoringUiState.Success(
                    puzzleItem = puzzleItem,
                    puzzle = puzzle,
                    initialSession = PuzzleSession(
                        fillsByRegionId = restoredFills,
                        fillHistory = restoredFills.keys.toList(),
                    ),
                )
            }
            result
        } catch (error: Exception) {
            ColoringUiState.Error(
                error.message ?: context.getString(R.string.coloring_load_failed),
            )
        }
    }

    ColoringScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onSessionPersisted = { assetPath, totalRegions, session ->
            coroutineScope.launch(Dispatchers.IO) {
                progressStore.saveProgress(
                    assetPath = assetPath,
                    fillsByRegionId = session.fillsByRegionId,
                    totalRegions = totalRegions,
                )
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ColoringScreen(
    state: ColoringUiState,
    onNavigateBack: () -> Unit,
    onSessionPersisted: (String, Int, PuzzleSession) -> Unit,
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
            var session by remember(state.puzzleItem.id, state.initialSession) {
                mutableStateOf(state.initialSession)
            }
            val totalRegions = state.puzzle.document.regions.size
            val persistSession = remember(state.puzzleItem.puzzleAssetPath, totalRegions, onSessionPersisted) {
                { updatedSession: PuzzleSession ->
                    onSessionPersisted(
                        state.puzzleItem.puzzleAssetPath,
                        totalRegions,
                        updatedSession,
                    )
                }
            }

            Scaffold(
                topBar = {
                    ColoringTopBar(
                        title = state.puzzleItem.displayName,
                        onNavigateBack = onNavigateBack,
                        clearEnabled = session.fillHistory.isNotEmpty(),
                        clearAllEnabled = session.fillsByRegionId.isNotEmpty(),
                        onClear = {
                            val updatedSession = session.undoLastFill()
                            session = updatedSession
                            persistSession(updatedSession)
                        },
                        onClearAll = {
                            val clearedSession = PuzzleSession(selectedPaletteId = session.selectedPaletteId)
                            session = clearedSession
                            persistSession(clearedSession)
                        },
                    )
                },
            ) { innerPadding ->
                PuzzleContent(
                    puzzle = state.puzzle,
                    session = session,
                    onSessionChanged = { session = it },
                    onSessionPersisted = persistSession,
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
    puzzle: LoadedPuzzle,
    session: PuzzleSession,
    onSessionChanged: (PuzzleSession) -> Unit,
    onSessionPersisted: (PuzzleSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paletteById = remember(puzzle.palette) { puzzle.palette.associateBy { it.id } }
    val totalRegions = puzzle.document.regions.size
    val puzzleAspectRatio = remember(puzzle.worldBounds) {
        (puzzle.worldBounds.width / puzzle.worldBounds.height).takeIf { it > 0f } ?: 1f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProgressBadge(
            filledCount = session.filledCount,
            totalRegions = totalRegions,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            PuzzleCanvas(
                puzzle = puzzle,
                session = session,
                paletteById = paletteById,
                onPuzzleTapped = { worldPoint ->
                    val selectedPaletteId = session.selectedPaletteId ?: return@PuzzleCanvas
                    val hitRegion = PuzzleTopology.hitTestRegion(puzzle.renderRegions, worldPoint) ?: return@PuzzleCanvas
                    if (session.fillsByRegionId.containsKey(hitRegion.region.id)) return@PuzzleCanvas
                    val targetPaletteId = hitRegion.region.targetPaletteId ?: return@PuzzleCanvas

                    if (targetPaletteId == selectedPaletteId) {
                        val updatedSession = session.copy(
                            fillsByRegionId = session.fillsByRegionId + (hitRegion.region.id to selectedPaletteId),
                            fillHistory = session.fillHistory + hitRegion.region.id,
                        )
                        onSessionChanged(updatedSession)
                        onSessionPersisted(updatedSession)
                    }
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
                onSessionChanged(session.copy(selectedPaletteId = paletteId))
            },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
private fun PuzzleCanvas(
    puzzle: LoadedPuzzle,
    session: PuzzleSession,
    paletteById: Map<Int, PaletteColor>,
    onPuzzleTapped: (PuzzlePoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val numberTextSize = 14.sp
    val canvasPaddingPx = with(density) { 2.dp.toPx() }
    val currentOnPuzzleTapped by rememberUpdatedState(onPuzzleTapped)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var viewport by remember(puzzle) { mutableStateOf(PuzzleViewport()) }
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
    val textPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = with(density) { numberTextSize.toPx() }
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
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
            .pointerInput(transform, puzzle.renderRegions) {
                detectTapGestures { offset ->
                    val currentTransform = transform ?: return@detectTapGestures
                    currentOnPuzzleTapped(currentTransform.toWorld(offset))
                }
            },
    ) {
        drawRect(color = Color.White)

        val currentTransform = transform ?: return@Canvas

        puzzle.renderRegions.forEach { renderRegion ->
            val fillColor = session.fillsByRegionId[renderRegion.region.id]
                ?.let { paletteById[it]?.composeColor }
                ?: Color(0xFFF7F7F7)
            val path = shapePath(renderRegion.shape, currentTransform)
            drawPath(
                path = path,
                color = fillColor,
            )
        }

        puzzle.renderRegions.forEach { renderRegion ->
            drawCenteredText(
                text = renderRegion.region.number.toString(),
                point = currentTransform.toScreen(renderRegion.region.numberPosition),
                paint = textPaint,
                textSize = numberTextSize,
            )
        }

        puzzle.outlineSegments.forEach { segment ->
            drawOutline(segment, currentTransform)
        }
    }
}

private fun DrawScope.drawCenteredText(
    text: String,
    point: Offset,
    paint: Paint,
    textSize: TextUnit,
) {
    paint.textSize = textSize.toPx()
    val baseline = point.y - (paint.descent() + paint.ascent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(text, point.x, baseline, paint)
}

private fun DrawScope.drawOutline(segment: OutlineSegment, transform: ScreenTransform) {
    drawLine(
        color = Color.Black,
        start = transform.toScreen(segment.start),
        end = transform.toScreen(segment.end),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

private fun shapePath(shape: RenderShape, transform: ScreenTransform): Path {
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
    }

    addContour(path, shape.outer, transform)
    shape.holes.forEach { hole ->
        addContour(path, hole, transform)
    }
    return path
}

private fun addContour(path: Path, points: List<PuzzlePoint>, transform: ScreenTransform) {
    points.forEachIndexed { index, point ->
        val mapped = transform.toScreen(point)
        if (index == 0) {
            path.moveTo(mapped.x, mapped.y)
        } else {
            path.lineTo(mapped.x, mapped.y)
        }
    }
    path.close()
}

@Composable
private fun PaletteStrip(
    colors: List<PaletteColor>,
    selectedPaletteId: Int?,
    onPaletteSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                width = if (isSelected) 5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = MaterialTheme.shapes.small,
                            )
                            .background(
                                color = paletteColor.composeColor,
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                    Text(
                        text = paletteColor.id.toString(),
                        style = MaterialTheme.typography.labelMedium,
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
