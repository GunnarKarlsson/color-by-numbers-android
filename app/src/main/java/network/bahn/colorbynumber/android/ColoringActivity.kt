package network.bahn.colorbynumber.android

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import kotlinx.coroutines.withContext
import network.bahn.colorbynumber.android.coloring.LoadedPuzzle
import network.bahn.colorbynumber.android.coloring.OutlineSegment
import network.bahn.colorbynumber.android.coloring.PaletteColor
import network.bahn.colorbynumber.android.coloring.PuzzleAssetLoader
import network.bahn.colorbynumber.android.coloring.PuzzleBounds
import network.bahn.colorbynumber.android.coloring.PuzzlePoint
import network.bahn.colorbynumber.android.coloring.PuzzleSession
import network.bahn.colorbynumber.android.coloring.PuzzleTopology
import network.bahn.colorbynumber.android.ui.theme.ColorByNumberTheme

private const val SAMPLE_PUZZLE_ASSET = "puzzles/topology_new_3.cbn"

class ColoringActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ColorByNumberTheme {
                ColoringRoute()
            }
        }
    }
}

private sealed interface ColoringUiState {
    data object Loading : ColoringUiState
    data class Success(val puzzle: LoadedPuzzle) : ColoringUiState
    data class Error(val message: String) : ColoringUiState
}

@Composable
private fun ColoringRoute() {
    val context = LocalContext.current
    val loader = remember(context) { PuzzleAssetLoader(context) }
    val state by produceState<ColoringUiState>(initialValue = ColoringUiState.Loading, loader) {
        value = try {
            val puzzle = withContext(Dispatchers.IO) {
                loader.load(SAMPLE_PUZZLE_ASSET)
            }
            ColoringUiState.Success(puzzle)
        } catch (error: Exception) {
            ColoringUiState.Error(
                error.message ?: context.getString(R.string.coloring_load_failed),
            )
        }
    }

    ColoringScreen(state = state)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ColoringScreen(state: ColoringUiState) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(text = stringResource(R.string.coloring_title))
                },
            )
        },
    ) { innerPadding ->
        when (state) {
            ColoringUiState.Loading -> LoadingState(modifier = Modifier.padding(innerPadding))
            is ColoringUiState.Error -> ErrorState(
                message = state.message,
                modifier = Modifier.padding(innerPadding),
            )

            is ColoringUiState.Success -> PuzzleContent(
                puzzle = state.puzzle,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
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
    modifier: Modifier = Modifier,
) {
    var session by remember(puzzle) { mutableStateOf(PuzzleSession()) }
    val paletteById = remember(puzzle.palette) { puzzle.palette.associateBy { it.id } }
    val selectedPalette = session.selectedPaletteId?.let { paletteById[it] }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.coloring_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(
                    R.string.coloring_progress,
                    session.filledCount,
                    puzzle.renderRegions.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = selectedPalette?.let {
                    stringResource(R.string.coloring_selected_palette, it.id, it.label)
                } ?: stringResource(R.string.coloring_no_palette_selected),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            tonalElevation = 2.dp,
            shadowElevation = 1.dp,
        ) {
            PuzzleCanvas(
                puzzle = puzzle,
                session = session,
                paletteById = paletteById,
                onPuzzleTapped = { worldPoint ->
                    val selectedPaletteId = session.selectedPaletteId ?: return@PuzzleCanvas
                    val hitRegion = PuzzleTopology.hitTestRegion(puzzle.renderRegions, worldPoint) ?: return@PuzzleCanvas
                    val targetPaletteId = hitRegion.region.targetPaletteId ?: return@PuzzleCanvas

                    if (targetPaletteId == selectedPaletteId) {
                        session = session.copy(
                            fillsByRegionId = session.fillsByRegionId + (hitRegion.region.id to selectedPaletteId),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            )
        }
        PaletteStrip(
            colors = puzzle.palette,
            selectedPaletteId = session.selectedPaletteId,
            onPaletteSelected = { paletteId ->
                session = session.copy(selectedPaletteId = paletteId)
            },
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
    val numberTextSize = 18.sp
    val canvasPaddingPx = with(density) { 24.dp.toPx() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val transform = remember(canvasSize, puzzle.worldBounds, canvasPaddingPx) {
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
            .pointerInput(transform, puzzle.renderRegions) {
                detectTapGestures { offset ->
                    val currentTransform = transform ?: return@detectTapGestures
                    onPuzzleTapped(currentTransform.toWorld(offset))
                }
            },
    ) {
        drawRect(color = Color.White)

        val currentTransform = transform ?: return@Canvas

        puzzle.renderRegions.forEach { renderRegion ->
            val fillColor = session.fillsByRegionId[renderRegion.region.id]
                ?.let { paletteById[it]?.composeColor }
                ?: Color(0xFFF7F7F7)
            val path = polygonPath(renderRegion.polygon, currentTransform)
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

private fun polygonPath(points: List<PuzzlePoint>, transform: ScreenTransform): Path {
    val path = Path()
    points.forEachIndexed { index, point ->
        val mapped = transform.toScreen(point)
        if (index == 0) {
            path.moveTo(mapped.x, mapped.y)
        } else {
            path.lineTo(mapped.x, mapped.y)
        }
    }
    path.close()
    return path
}

@Composable
private fun PaletteStrip(
    colors: List<PaletteColor>,
    selectedPaletteId: Int?,
    onPaletteSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.palette_title),
            style = MaterialTheme.typography.titleMedium,
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
                                width = if (isSelected) 3.dp else 1.dp,
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

private data class ScreenTransform(
    val scale: Float,
    val translateX: Float,
    val translateY: Float,
    val bounds: PuzzleBounds,
) {
    fun toScreen(point: PuzzlePoint): Offset =
        Offset(
            x = translateX + ((point.x - bounds.minX) * scale),
            y = translateY + ((point.y - bounds.minY) * scale),
        )

    fun toWorld(point: Offset): PuzzlePoint =
        PuzzlePoint(
            x = ((point.x - translateX) / scale) + bounds.minX,
            y = ((point.y - translateY) / scale) + bounds.minY,
        )

    companion object {
        fun fit(canvasSize: Size, bounds: PuzzleBounds, padding: Float): ScreenTransform {
            val availableWidth = (canvasSize.width - (padding * 2f)).coerceAtLeast(1f)
            val availableHeight = (canvasSize.height - (padding * 2f)).coerceAtLeast(1f)
            val scale = minOf(availableWidth / bounds.width, availableHeight / bounds.height)

            val contentWidth = bounds.width * scale
            val contentHeight = bounds.height * scale
            val translateX = (canvasSize.width - contentWidth) / 2f
            val translateY = (canvasSize.height - contentHeight) / 2f

            return ScreenTransform(
                scale = scale,
                translateX = translateX,
                translateY = translateY,
                bounds = bounds,
            )
        }
    }
}
