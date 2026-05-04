# Android Rendering Debrief

This note is for the Android app agent. It compares the legacy Android app in `/Users/gunnar/AndroidStudioProjects/ColorByNumber` with the current rendering and color-by-numbers implementation in this repo.

## Executive summary

The legacy Android app assumes every playable region is a single simple polygon with one boundary loop. The current app no longer works that way internally.

The current model is topology-first and hole-aware:

- geometry is stored as shared `vertices` + `edges` + per-region edge loops
- each region can now have an `outer` loop plus zero or more `holes`
- rendering is based on reconstructed `RegionShape`, not a flat polygon list
- hit testing must exclude holes
- fill/progress remains region-based and keyed by stable `region.id`
- outlines still come from unique topology edges and should only be drawn once

If the Android app keeps the legacy "single polygon only" assumption, it will render newer puzzles incorrectly and will mis-handle taps in regions that contain holes or nested geometry.

## What the legacy Android app does

The legacy app's current behavior is centered around these files:

- `ColoringActivity.kt`
- `PuzzleTopology.kt`
- `PuzzleModels.kt`
- `PuzzleJsonParser.kt`

### Legacy rendering pipeline

The legacy flow is:

1. parse the puzzle JSON into `PuzzleDocument`
2. convert each topology region into a single `polygon: List<PuzzlePoint>`
3. render each region by building one Compose `Path`
4. draw the number at `numberPosition`
5. draw all topology edges as outline segments
6. on tap, run point-in-polygon against the same single polygon

Important legacy assumptions:

- `TopologyRegionBoundary` only has `boundary`
- each region has one loop
- there is no support for holes
- hit testing is `pointInPolygon(point, polygon)`
- fill rendering is a direct polygon fill

That is still fine for simple puzzles, but it is now behind the current desktop/document model.

## What changed in this repo

The current repo changed both the document model and the renderer behavior.

### 1. Topology format changed from one loop to shape-with-holes

Current topology uses:

- `vertices`
- `edges`
- `regions`

But each region is now:

- `outer: Vec<RegionEdgeRef>`
- `holes: Vec<Vec<RegionEdgeRef>>`

The current Rust model still accepts legacy saved data where the region used a single `boundary` field, but that is now compatibility behavior, not the preferred runtime shape.

Meaning for Android:

- stop assuming a region is one boundary loop
- parse `outer` and `holes`
- optionally accept legacy `boundary` as a fallback that maps to `outer`

## 2. Rendering is now based on `RegionShape`, not flat polygons

The current renderer reconstructs a `RegionShape`:

- `outer: Vec<[f32; 2]>`
- `holes: Vec<Vec<[f32; 2]>>`

That shape is then used for:

- fill drawing
- hit testing
- number placement validation
- editor overlays and topology editing

The old Android app constructs:

- `RenderRegion(region, polygon)`

The new conceptual equivalent is closer to:

- `RenderRegion(region, shape)`

where `shape` contains `outer` plus `holes`.

## 3. Fill rendering is now hole-aware

In the current repo, the renderer fills regions by triangulating the outer loop plus holes into a mesh before drawing.

That is a meaningful change from the legacy Android code, which just fills a single polygon path.

Android does not have to copy the exact desktop implementation, but it must preserve the behavior:

- the outer region should fill
- interior holes must remain empty
- holes must not be tappable as part of the parent region

Practical Android options:

1. Build a single `Path` with outer + hole contours and use an even-odd fill rule.
2. Or expose triangulation-ready geometry from Rust and draw that.

Either is acceptable. The important part is that the shape is no longer a single loop.

## 4. Hit testing is now hole-aware

The legacy Android code does:

- `pointInPolygon(point, polygon)`

The current repo does:

- inside outer polygon
- and not inside any hole polygon

Conceptually:

```kotlin
inside = pointInPolygon(point, outer) && holes.none { hole -> pointInPolygon(point, hole) }
```

This is one of the biggest gameplay differences. If Android keeps the old hit test, taps inside a hole will incorrectly color the parent region.

## 5. Topology is the canonical geometry source

In the current repo, the canonical saved geometry is topology, and runtime rendering reconstructs region shapes from it.

Important implications:

- shared borders are represented once as shared edges
- adjacent regions reuse the same edge ids with opposite direction when needed
- outlines should still come from unique topology segments, not by redrawing each region boundary separately

This is still aligned with the legacy Android app's outline strategy, but Android now needs shape reconstruction that supports:

- `outer`
- `holes`
- closed loops
- reversed edge traversal

## 6. The desktop/editor can now generate more complex region structures

The current repo is not just a player. It can edit topology, and those editing features create geometry that the old Android assumptions do not cover well.

Examples of new behaviors in this repo:

- inserting a closed path can split existing regions into multiple atomic subregions
- nested polygons can become parent holes
- shared edges are deduplicated with epsilon-based vertex merging
- moving a shared vertex updates all dependent regions
- deleting a shared vertex updates both parent-hole geometry and child region geometry

The Android player does not need editor features, but it must be able to load the geometry those features produce.

That means the Android player must support:

- regions with holes
- stable region ids after splits
- shared-edge topology reconstruction

## 7. Color-by-numbers logic is still region-based, but there are stricter invariants

The good news: the core coloring rule has not fundamentally changed.

It is still:

1. user selects a palette color
2. tap is mapped from screen space to world space
3. app hit-tests the tapped region
4. region is fillable only if selected color matches `target_palette_id`
5. progress is recorded for that region id

But the new repo reinforces these invariants:

- `target_palette_id` is the real color assignment
- displayed `number` is derived from palette order and may be recomputed
- progress is keyed by `region.id`, not by list position
- restoring progress should only reapply fills that still match the region's current `target_palette_id`

Android should keep that same rule set.

## 8. Progress persistence is more explicitly sidecar-based

The current repo uses a sidecar progress file:

- puzzle: `something.cbn`
- progress: `something.cbn.progress.json`

Stored entries are:

- `region_id`
- `palette_color_id`

This is similar to the legacy Android app's `fillsByRegionId` approach and should stay that way.

Important restore rule from the current repo:

- only restore a fill if the saved `palette_color_id` still matches that region's current `target_palette_id`

That prevents stale progress from being restored after palette changes or puzzle edits.

## 9. Palette handling is more formalized

The current repo supports:

- embedded palette in the `.cbn`
- optional linked `.cbnpalette`
- fallback to embedded palette if linked palette cannot be loaded

The legacy Android app already has linked-palette fallback logic, which is still the right behavior.

The Android agent should preserve:

- linked palette resolution first
- embedded palette fallback second
- fill permission based on `region.target_palette_id`

## 10. JSON/schema compatibility changes Android must handle

The legacy Android parser currently expects topology regions like this:

```json
{
  "region_id": 7,
  "boundary": [
    { "edge_id": 1, "reversed": false }
  ]
}
```

The current repo can now persist regions like this:

```json
{
  "region_id": 7,
  "outer": [
    { "edge_id": 1, "reversed": false }
  ],
  "holes": [
    [
      { "edge_id": 9, "reversed": false },
      { "edge_id": 10, "reversed": false }
    ]
  ]
}
```

Required Android parser behavior:

- if `outer` exists, use it
- if `holes` exists, parse it
- if only legacy `boundary` exists, treat it as `outer`
- reconstruct closed loops from edge refs

## What the Android agent should change

### Required model changes

Replace the legacy one-loop region render model with a hole-aware shape model.

Recommended Android-side data shape:

```kotlin
data class RenderShape(
    val outer: List<PuzzlePoint>,
    val holes: List<List<PuzzlePoint>>,
)

data class RenderRegion(
    val region: PuzzleRegion,
    val shape: RenderShape,
)
```

Update topology region models from:

```kotlin
data class TopologyRegionBoundary(
    val regionId: Int,
    val boundary: List<RegionEdgeRef>,
)
```

to something like:

```kotlin
data class TopologyRegionBoundary(
    val regionId: Int,
    val outer: List<RegionEdgeRef>,
    val holes: List<List<RegionEdgeRef>>,
)
```

with parser fallback from legacy `boundary`.

### Required reconstruction changes

Replace:

- `regionPolygon(topology, regionId): List<PuzzlePoint>?`

with something like:

- `regionShape(topology, regionId): RenderShape?`

where:

- `outer` is reconstructed from the outer loop
- each hole loop is reconstructed separately
- all loops are closed consistently

### Required rendering changes

Update fill drawing so it supports holes.

Do not keep the old assumption:

- one region -> one polygon path

Instead use:

- one region -> one outer contour + zero or more hole contours

Then:

- fill using even-odd `Path` behavior or triangulated mesh
- draw the number at the existing `numberPosition`
- draw outlines from unique topology segments as before

### Required hit-test changes

Replace:

- `pointInPolygon(point, region.polygon)`

with:

- `pointInPolygon(point, region.shape.outer) && !pointInAnyHole`

This is required for correctness.

### Required progress behavior

Keep:

- `fillsByRegionId`

Do not regress to:

- progress keyed only by list index

When restoring progress:

- map by `region.id`
- only restore if the saved palette id still matches `target_palette_id`

## Recommended implementation order for Android

1. Update JSON parsing to support `outer` and `holes`, with fallback from `boundary`.
2. Replace flat region polygons with hole-aware render shapes.
3. Update hit testing to exclude holes.
4. Update fill rendering to preserve holes.
5. Keep outline rendering from unique topology edges.
6. Keep progress keyed by `region.id` and validate restored palette ids.
7. Test against a puzzle that contains a parent region with an interior hole.

## Concrete brief to give the Android agent

You can hand this summary directly to the Android agent:

> The legacy Android renderer assumes each region is a single polygon reconstructed from `topology.regions[*].boundary`. The current desktop/document model is now topology-first and hole-aware. A region may have `outer` plus `holes`, and rendering/hit-testing must operate on that full shape, not a single polygon. Update the Android parser to accept `outer` and `holes` with legacy `boundary` fallback. Replace `RenderRegion.polygon` with a shape model that carries the outer contour and hole contours. Fill rendering must preserve holes, and hit testing must return true only when the point is inside the outer contour and outside all holes. Keep progress keyed by stable `region.id`, and only restore saved fills when the stored palette id still matches the region's current `target_palette_id`. Outlines should continue to come from unique topology edges so shared borders are drawn once.

## Bottom line

The main rendering change is not cosmetic. It is a geometry-model change:

- old Android: region = one polygon
- current app: region = shape with shared-edge topology, outer loop, and optional holes

That is the key message the Android agent needs to implement correctly.
