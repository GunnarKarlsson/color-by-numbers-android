# Color-by-Numbers Mobile Architecture Notes

## 1. How the system works

This project is already organized around a topology-first document model that is a good fit for mobile rendering and interaction.

### 1.1 Core document shape

At runtime, a puzzle is represented by a `Document` with these main parts:

- `regions`: user-facing puzzle regions, each with a stable `region.id`, displayed number, number position, and `target_palette_id`
- `palette`: the set of colors available to the puzzle
- `palette_link`: optional reference to an external palette file on disk
- `document_topology`: the persisted geometry model
- `topology`: a derived runtime cache used for rendering and geometry queries

The important separation is:

- region metadata is stored per region
- geometry is stored in shared topology
- player progress is stored separately from the puzzle geometry

### 1.2 Vertex-edge-region topology model

Geometry is stored as:

- `vertices`: points in world/image space
- `edges`: line segments between vertex ids
- `regions`: ordered loops of edge references

Each region boundary is a list of `RegionEdgeRef { edge_id, reversed }`.

That gives us three useful properties:

1. a shared border only exists once as a geometric edge
2. neighboring regions can reference the same edge in opposite directions
3. fill/progress logic stays keyed by `region.id`, not by edge

This is the main reason the model works well for color-by-numbers: rendering and editing can share borders, while gameplay state remains simple.

### 1.3 How polygons are reconstructed

Even though geometry is stored as shared edges, most renderer operations still want a polygon.

To get a polygon for a region:

1. find the region boundary loop
2. walk its edge refs in order
3. resolve each edge to its start/end vertices
4. reverse the edge direction when `reversed == true`
5. append the points in sequence
6. close the polygon if needed

This reconstructed polygon is then used for:

- fill triangulation
- point-in-polygon hit testing
- editor overlays
- number placement helpers

### 1.4 How shared borders are built

When polygon paths are converted into topology, the system:

1. deduplicates nearby vertices using an epsilon tolerance
2. normalizes each edge key as `(min(start, end), max(start, end))`
3. reuses an existing edge if another region already references the same segment
4. stores direction per region using `reversed`

This means adjacent regions render with a single divider line instead of two overlapping lines.

### 1.5 How rendering works

The rendering pipeline is conceptually:

1. draw the canvas background
2. for each region, reconstruct its polygon
3. triangulate the polygon into a mesh
4. fill it with the current player color, or a neutral unfilled color
5. draw the region number at `number_position`
6. draw outlines from the topology's unique edges

There are two key details here:

- fills are region-based, not edge-based
- outlines come from `topology.unique_segments()`, so each shared edge is painted once

For an Android MVP, the same split should be kept:

- region polygon for fill and hit testing
- unique edge list for outline rendering

### 1.6 How clicks/taps select a region

Region selection is currently done with point-in-polygon testing:

1. convert the tap from screen space into world/image space
2. iterate regions
3. reconstruct each region polygon
4. test the tap with point-in-polygon
5. stop at the first matching region

In the renderer, a region is only colored if:

- the user has selected a palette color, and
- that selected palette color matches the region's `target_palette_id`

That enforces the color-by-numbers rule that a region can only be filled with its assigned color.

### 1.7 How color selection works

Color selection is simple state:

- the palette is loaded from the document or linked palette file
- the current selected color is stored as `selected_palette_id`
- when the user taps a color swatch, that id becomes the active paint color

In the editor, palette selection is also used to assign target colors to regions. In the renderer, it controls which numbered regions can be painted.

### 1.8 How a region gets colored

The current renderer keeps progress as an in-memory `fills: Vec<Option<u32>>`, aligned to the `document.regions` order.

On a successful tap:

1. identify the tapped region
2. check that selected color matches `region.target_palette_id`
3. push the old fills state onto the undo stack
4. write `Some(selected_palette_id)` into that region's fill slot
5. redraw

Progress is then:

- `done = number of filled slots`
- `total = number of regions`

For mobile, the behavior should stay region-based, but the persisted format should be keyed by `region_id` rather than relying only on list index alignment.

### 1.9 How progress is saved

The current project saves puzzle progress in a sidecar JSON file, separate from the `.cbn` file.

Path rule:

- if the puzzle is `example.cbn`
- progress is saved as `example.cbn.progress.json`

Stored data:

- file version
- a list of `{ region_id, palette_color_id }` entries for filled regions

This is a good mobile design too, because:

- the original puzzle file stays immutable
- player progress can be reset independently
- cloud sync or backup can treat puzzle assets and user state separately

### 1.10 How progress is loaded

When progress is loaded:

1. clear current in-memory fills and undo/redo stacks
2. read the sidecar JSON
3. for each saved entry, find the matching region by `region_id`
4. only restore the fill if the saved `palette_color_id` still matches the region's current target color

That last rule is important. It prevents stale or invalid progress from being restored after palette or puzzle changes.

### 1.11 How progress is cleared

Progress clear has two parts:

1. clear in-memory state:
   - all fill slots become `None`
   - undo stack is cleared
   - redo stack is cleared
2. delete the progress sidecar file from disk if it exists

This should stay the same on Android.

### 1.12 How palette files fit in

The repo already supports standalone palette files (`.cbnpalette`).

A puzzle can:

- embed a palette directly in the `.cbn`
- optionally link to an external palette file

At load time, the app tries to resolve the linked palette file from disk. If that fails, it falls back to the embedded palette.

For mobile, this is useful because bundled puzzles can ship with embedded palettes, while downloaded/shared palettes can remain reusable assets.

### 1.13 Recommended invariants

The mobile implementation should preserve these rules:

- `region.id` must remain stable across save/load
- topology is the canonical geometry source
- region polygons are reconstructed from topology, not stored separately
- fills are keyed by region identity
- outlines are drawn from unique edges
- progress storage must not mutate the original puzzle file

## 2. Suggested Android MVP

### 2.1 MVP goals

The Android MVP should do only the player-facing workflow:

1. load a `.cbn` puzzle from app storage
2. optionally resolve a linked `.cbnpalette`
3. render outlines, region numbers, and fills
4. let the user choose a color
5. let the user tap regions to fill them
6. save progress automatically
7. let the user clear progress

Editing tools are not needed for the first Android release.

### 2.2 Recommended stack

Use:

- Kotlin + Jetpack Compose for UI
- `Canvas` in Compose for drawing
- a shared Rust core exposed with UniFFI for:
  - `.cbn` parsing
  - `.cbnpalette` parsing
  - topology reconstruction
  - point-in-polygon hit testing
  - triangulation-ready polygon output
  - progress serialization helpers

This keeps file format and geometry logic consistent with desktop, while Android owns the UI and touch interaction.

### 2.3 Suggested module split

Recommended Android-side structure:

- `PuzzleRepository`
  - open puzzle file
  - open linked palette if present
  - read/write progress sidecar
- `PuzzleSessionViewModel`
  - selected color
  - current fills
  - progress counters
  - undo/redo if included in MVP
- `PuzzleRenderer`
  - draw fills
  - draw outlines
  - draw numbers
- `TouchMapper`
  - convert screen coordinates to world coordinates
  - account for zoom/pan
- `RustCore` bridge
  - load document
  - list polygons
  - list unique segments
  - hit-test point
  - validate topology

### 2.4 Recommended runtime model

On Android, the active session model should look roughly like this:

```kotlin
data class PuzzleSession(
    val document: PuzzleDocument,
    val selectedPaletteId: UInt?,
    val fillsByRegionId: Map<UInt, UInt?>,
    val zoom: Float,
    val panX: Float,
    val panY: Float
)
```

Important choices:

- keep fills keyed by `regionId`
- avoid coupling progress to region list indices
- treat loaded `.cbn` data as immutable during play

### 2.5 Puzzle load flow

When the player opens a puzzle:

1. read the `.cbn`
2. reconstruct or hydrate topology in memory
3. resolve palette:
   - use linked `.cbnpalette` if available
   - otherwise use embedded palette
4. load `example.cbn.progress.json` if it exists
5. build the initial `PuzzleSession`
6. precompute render-ready data if helpful:
   - region polygons
   - unique outline segments
   - region bounds for faster hit testing

For MVP performance, it is reasonable to cache polygons and unique segments once per loaded document.

### 2.6 Rendering flow on Android

For each frame:

1. apply zoom/pan transform
2. draw all filled region meshes/polygons
3. draw all region numbers
4. draw outline segments on top
5. draw the palette strip and selected swatch state outside the canvas

If Compose `Canvas` polygon fill becomes limiting, a practical fallback is:

- use Android `Path` per region polygon for fill and hit-testing
- still use the Rust core for topology/polygon reconstruction

### 2.7 Tap-to-fill flow

When the user taps the canvas:

1. map screen coordinates to world coordinates
2. hit-test the world point against regions
3. if no region matches, do nothing
4. if the selected palette id does not match the region's `target_palette_id`, do nothing
5. otherwise mark the region as filled
6. save progress
7. update progress UI

For the MVP, save progress immediately after every successful fill action. If needed, debounce writes by a few hundred milliseconds.

### 2.8 Color selection flow

The palette UI should:

1. display palette colors in document order
2. show the selected color clearly
3. optionally show the palette number label (`1`, `2`, `3`, ...)
4. disable or visually dim colors that have no remaining unfilled regions, if desired later

For MVP, simple selected/unselected swatches are enough.

### 2.9 Progress persistence on Android

Keep the same sidecar approach used by the desktop app:

- puzzle: `something.cbn`
- progress: `something.cbn.progress.json`

Store:

- version
- `filled_regions: [{ region_id, palette_color_id }]`

Write policy for MVP:

- autosave after each successful fill
- autosave after undo/redo if undo is included
- clear the file on explicit "Clear Progress"

Storage location:

- imported puzzles and their progress files should live in app-private storage
- if the user imports from shared storage, copy the asset into app storage first

### 2.10 Clear-progress behavior

The Android "Clear Progress" action should:

1. reset all `fillsByRegionId` entries to empty
2. clear undo/redo history if present
3. delete the sidecar progress file
4. redraw immediately

This action should not modify the `.cbn` or `.cbnpalette` files.

### 2.11 Nice-to-have but not MVP-critical

These are good follow-ups after the first playable version:

- undo/redo
- completed-color highlighting
- animated fill feedback
- puzzle list screen
- thumbnail caching
- cloud backup/sync
- partial region index for faster hit testing on large puzzles

### 2.12 Recommended MVP delivery order

1. Extract or formalize the Rust core APIs for document load, topology access, polygon reconstruction, and progress IO.
2. Build a basic Android screen that loads a bundled `.cbn` and draws it.
3. Add palette selection.
4. Add tap hit testing and correct-color fill behavior.
5. Add autosave progress sidecar support.
6. Add clear-progress action.
7. Add import/open flow for user-supplied `.cbn` and `.cbnpalette` files.

This gives a small but complete gameplay loop with the current topology model as the foundation.
