# TerrainMesh

**0.2 BETA testing status:** The preview particle changes have **not been checked in-game yet**. Compilation and the 14 automated tests pass, but distant visibility and appearance still need in-game verification. This update adds long-distance particles, a configurable 512-block default range, and markers that grow with distance, while keeping the particle budget unchanged.

**0.2 BETA — potentially unstable.** This is an early testing release; bugs and partial edits are possible. Test on a staging server and back up your world before large edits.

Generate solid, grass-covered terrain inside a perimeter you select in Minecraft. Fill empty spaces between roads, connect edges at different elevations, or add optional hills. Preview first, confirm to build, and undo through FAWE.

[Download 0.2 BETA](https://github.com/jcthegamer2024/TerrainMesh/releases/tag/v0.2-BETA) · [Report a bug](https://github.com/jcthegamer2024/TerrainMesh/issues)

Solid polygon terrain for **Paper 26.3**, **Java 25**, and **FastAsyncWorldEdit 2.16.2**. Gradle 9.7.0 is supplied through the wrapper. Java 25 is the minimum for the selected Paper API; Java 21 cannot compile or run this target.

## Start here: fill an area without adding a hill

All commands below go in **Minecraft chat**, except commands explicitly marked as server-console or terminal commands. You need operator status or the permissions listed below.

**Use `/terrainmesh`, not `/tm`.** FAWE also registers `/tm`, so that alias can invoke FAWE and produce an "Invalid value for Mask" error. `/terrainmesh wand` invokes the correct plugin. If another plugin conflicts with the full name, use `/terrainmesh:terrainmesh wand`.

### 1. Select the perimeter

```text
/terrainmesh wand
```

Hold the supplied blaze rod and **left-click blocks around the inside edges of your roads**, going clockwise or anticlockwise. Select at least three points. Select the block whose Y coordinate should be the terrain's top block at that location.

- The selection connects consecutive points and automatically joins the last to the first. Do not select the first point again to close it.
- Add extra points around bends and changes in road height. A straight line joins each pair of points; the plugin cannot infer a curved road between widely spaced points.
- Keep road surfaces outside the selected interior. Existing blocks immediately along the selected boundary are preserved, and the material protection list adds another safeguard.
- **Right-click** with the wand to remove the last boundary point, or use `/terrainmesh undo`.
- `/terrainmesh list` shows numbered coordinates. `/terrainmesh clear` removes both the boundary and any hill points so you can start again.

### 2. Choose the bottom Y

```text
/terrainmesh bottom 60
```

This makes the solid fill start at **Y=60**, continuing upward to the generated surface in every included column. You do not need to stand at that height or click a bottom block. Use F3 to inspect coordinates and choose the ground/foundation level below your roads.

For example, if the road edges are around Y=80 and the ground beneath is Y=60, use bottom 60. Do not set bottom above the lowest selected point or generated surface. A lower bottom means more blocks and a larger edit. The default is Y=0; setting it closer to the ground can greatly reduce the work.

### 3. Choose the blocks and surface style

```text
/terrainmesh hill clear
/terrainmesh noise 0
/terrainmesh smooth 70
/terrainmesh material surface GRASS_BLOCK
/terrainmesh material subsurface DIRT
/terrainmesh material foundation STONE
```

These settings create terrain that joins your boundary elevations **without an added hill or procedural bumps**. Different boundary heights still create slopes; equal boundary heights create a level surface. `hill clear` only removes optional hill controls, preserving the perimeter.

The surface material is the top block. The next four blocks below it are dirt by default; the rest down to bottom Y is foundation stone. Change any layer using a solid Minecraft block name, for example `/terrainmesh material surface MOSS_BLOCK`. Configure the dirt-layer depth in `terrain.subsurface-depth` in the server config.

### 4. Preview and build

```text
/terrainmesh preview
```

Wait for **Plan ready**, inspect the particles, and check the reported heights and block estimate. Preview does not change blocks. If everything looks correct, run:

```text
/terrainmesh generate confirm
```

Confirm within 120 seconds by default. Any selection or setting change invalidates the plan, so preview again after changing something. You may also run `/terrainmesh generate` to calculate a fresh plan, then `/terrainmesh generate confirm` to build it.

Wait for the completion message before doing another edit. To undo the terrain, use **FAWE's `//undo`**. `/terrainmesh undo` edits your point selection and does not undo terrain generation.

## Optional: add hills and natural variation

After selecting the perimeter, target a block **inside** it at the height you want the hill to reach:

```text
/terrainmesh hill add
```

If that position is in empty air, build temporary scaffolding to the desired height and target its top block. The target's Y coordinate is the desired top occupied terrain block. Each hill receives an ID; add multiple hills for more complex shapes.

```text
/terrainmesh hill list
/terrainmesh hill remove 1
/terrainmesh hill clear
```

Hill controls stay at their selected heights while the terrain joins the road edges. For subtle natural variation, try:

```text
/terrainmesh smooth 85
/terrainmesh noise 5
/terrainmesh seed 12345
/terrainmesh preview
```

Noise defaults to zero and fades out near the boundary and hill controls. The same seed, controls and settings produce the same terrain. After reviewing the new preview, run `/terrainmesh generate confirm`.

## Preview colours and common problems

| What you see | Meaning |
| --- | --- |
| Yellow lines | Closed polygon boundary |
| Orange points | Selected boundary controls |
| Magenta points | Interior hill controls |
| Cyan lines | Initial constrained triangle mesh |
| Green particles | Final terrain surface after hill corrections, smoothing and noise |

Large previews are deliberately sampled and capped; a gap between particles is not a gap in the final solid fill. Selection particles can remain after a preview ends because your point selection remains active.

### Seeing large selections from a distance

Preview and selection particles use long-distance delivery by default, with a 512-block viewing radius. Markers grow with distance up to Minecraft's maximum dust size. Only the selecting player receives them; the default budget remains 500 particles per second per player.

Administrators can adjust these settings in `plugins/TerrainMesh/config.yml`, then restart:

```yaml
preview:
  force-particles: true
  max-distance: 512
  particle-size: 2.5
```

`max-distance` accepts 32–2048 blocks; `particle-size` accepts 0.5–4.0. These defaults also apply to existing configs that lack the new keys. Long-distance delivery improves visibility but does not guarantee that particles render beyond your client's view distance or through obstacles. Try a higher view distance and fly above the area for an overview. Particles do not load chunks. See [Paper's long-distance particle documentation](https://jd.papermc.io/paper/26.3/com/destroystokyo/paper/ParticleBuilder.html#force(boolean)).

| Problem | What to do |
| --- | --- |
| FAWE says `Invalid value for Mask` after `/tm wand` | Use `/terrainmesh wand`; `/tm` conflicts with FAWE. |
| No permission | Ask an administrator to grant `terrainmesh.use` and `terrainmesh.generate`, or operator status for local testing. |
| Invalid/self-intersecting polygon | Check `/terrainmesh list`; select points in perimeter order without crossing lines or selecting the same X/Z twice. |
| Hill is outside/on the boundary | Remove that hill ID and select a position strictly inside the polygon. |
| Bottom exceeds the terrain | Lower `/terrainmesh bottom <y>`, then preview again. |
| No current confirmation | Run `/terrainmesh preview`, wait for Plan ready, then confirm before it expires. Another player's new plan can also expire yours to bound memory. |
| Estimate exceeds limits | Raise bottom Y while keeping it below the surface, or split the project into smaller selections. Administrators can review server budgets. |
| Terrain job already running | Wait for completion or use `/terrainmesh cancel` before changing selection/settings. |
| Need to stop a generation | Use `/terrainmesh cancel`, wait for it to stop, then `//undo` if partial terrain remains. Cancellation is not an atomic rollback. |

## Build and installation

```powershell
.\gradlew.bat clean build
```

Download `TerrainMesh-0.2-BETA.jar` from the prerelease above, or build it locally at `build/libs/TerrainMesh-0.2-BETA.jar`. Copy it to your Paper server's `plugins` directory alongside FastAsyncWorldEdit. Restart the server. FAWE is required and loads before TerrainMesh. JTS 1.20.0 is included in the plugin JAR; Paper and FAWE are compile-only dependencies and are not bundled. Dependencies are pinned for reproducible builds. Configure `plugins/TerrainMesh/config.yml`, then restart to apply changes.

For local development, run `.\gradlew.bat runServer` (or `./gradlew runServer`). The task automatically downloads the official FAWE 2.16.2 Paper release from GitHub and loads it alongside the built TerrainMesh plugin. Server files are kept in the ignored `run/` directory.

Join the local server at `localhost`. In the **server console**, run `op YourMinecraftName` to enable testing commands, then follow the walkthrough above. Stop the server normally with `stop` when finished. On Linux/macOS, use `./gradlew clean build` to build; if required, enable the wrapper with `chmod +x gradlew`.

The automated suite tests geometry, raster constraints, estimates, cancellation and overlap leases. It does **not** substitute for a live Paper/FAWE integration test. Before deployment, verify a small concave selection, protected road blocks, cancellation and `//undo` on a staging server using your production FAWE configuration. No live server integration test has been run in this workspace.

## Workflow

1. Run `/terrainmesh wand`. Left-click blocks around the inside perimeter of the roads in order. Right-click removes the last boundary point. `/terrainmesh add` selects the targeted block without a wand.
2. Add enough points to follow bends, curves and changes in road elevation. The last point connects automatically to the first. Use `/terrainmesh list` to review numbered coordinates.
3. Target an interior block at the desired hill elevation and run `/terrainmesh hill add`. Hill IDs remain stable until the entire selection is cleared. Use temporary scaffold blocks if the desired height is in the air; remove the scaffolding before generation if you do not want it retained above the surface.
4. Set `/terrainmesh smooth 85`, `/terrainmesh noise 5`, `/terrainmesh bottom 0` as desired.
5. Run `/terrainmesh preview`. Yellow lines show boundary segments; orange points are boundary controls, magenta points are hill controls, cyan lines show the underlying constrained triangle mesh, and green particles show the final raster surface. The cyan mesh is the initial interpolation scaffold; smoothing and hill corrections change the final surface shown in green.
6. Review area, column count, control counts, height range, estimated submissions and memory budget. Run `/terrainmesh generate confirm` to edit. `/terrainmesh generate` computes a fresh plan and requests confirmation too.
7. Use FAWE's `//undo` to restore the edit. `/terrainmesh undo` only removes the latest selection point.

All settings and points belong to the individual player. Selection state is in memory and is discarded on disconnect or restart. Switching worlds cancels work and invalidates confirmation; clear the selection before selecting in a different world.

## Commands and permissions

The main command is `/terrainmesh`. A `/tm` alias is registered but conflicts with FAWE, so use the full command throughout. All commands require `terrainmesh.use`. Generation additionally requires `terrainmesh.generate`. Both default to operators. `terrainmesh.admin` grants both permissions and does not bypass configured budgets or road protection. FAWE's own editing permissions, region restrictions and limits apply to the actor. Prefix every entry in the table below with `/terrainmesh `.

| Command | Action |
| --- | --- |
| `wand` | Give a tagged blaze rod selection wand |
| `add` | Add the targeted block within 128 blocks |
| `undo` | Remove the last boundary point |
| `clear` | Clear all boundary and hill points |
| `list` | List ordered boundary coordinates and selection world |
| `hill add` | Add a targeted interior elevation constraint |
| `hill remove <id>` | Remove a hill constraint |
| `hill list` / `hill clear` | List or clear hill constraints |
| `preview` | Plan asynchronously and display the mesh and surface |
| `generate` | Plan asynchronously and request confirmation |
| `generate confirm` | Generate the current confirmed plan |
| `cancel` | Remove preview/confirmation and request job cancellation |
| `smooth <0-100>` | Constrained smoothing iterations (twice this value) |
| `resolution <1-8>` | Smoothing neighborhood radius and minimum preview sample spacing |
| `noise <0-100>` | Coherent variation amplitude, up to value/10 blocks |
| `seed <signed-long>` | Reproducible noise seed; default 0 |
| `bottom <y>` | Inclusive bottom of every fill column |
| `material surface <block>` | Set solid surface material |
| `material subsurface <block>` | Set solid subsurface material |
| `material foundation <block>` | Set solid foundation material |
| `sample <1-64>` | Add edge samples at approximately this spacing |
| `help` | Show command help |

Every selection or generation-setting change invalidates the previous confirmation. Changes are blocked while that player's job is queued or running. Confirmations expire after 120 seconds by default. For bounded aggregate memory, requesting another plan discards previously retained confirmations; players with displaced confirmations are notified. Preview particles expire independently after 60 seconds. Persistent selection particles continue while points are selected.

## Geometry and terrain quality

JTS validates a single simple polygon and triangulates it with constrained polygon triangulation, retaining perimeter segments and collinear elevation controls. Concave polygons are supported. Crossing edges, overlapping edges, duplicate X/Z controls, degenerate polygons and outside/on-boundary hill controls are rejected. Holes and multiple disjoint polygons are not supported.

The initial surface uses barycentric heights in the constrained mesh. Interior hills use distance-weighted corrections with a boundary term: each hill is exact at its position and its influence vanishes at the perimeter. Constrained grid diffusion then softens triangle creases. Boundary cells and hill cells are pinned on every iteration. The resolution setting adjusts the diffusion stencil; the edit always rasterizes every interior block column, even when resolution is greater than one. This avoids gaps from coarse sampling. Smoothing 0 retains the initial triangular scaffold and can show planar ridges. More control points may be needed for demanding shapes or very sharp road elevation changes.

Seeded value noise is spatially coherent with a 32-block base scale. A smooth attenuation envelope makes noise vanish at boundaries and hill controls. Default noise is zero. Final raster heights are rounded to the nearest integer and clamped to the world's `[minHeight, maxHeight)` range; invalid control heights and bottom heights are rejected. Bottom must not exceed any generated surface column.

Every included column is filled from bottom through surface: stone below, four dirt blocks below the grass surface by default. Material protection and FAWE restrictions can intentionally leave existing blocks or skip edits, so the plugin cannot promise uniformly solid replacement through protected structures or denied regions. Submission estimates are conservative upper bounds, not a world scan or an exact changed-block count.

## Road edges and block precision

Coordinates refer to selected **block centers in X/Z** and the **top occupied block Y**, not the top face's Y+1. Particles are offset by half a block in X/Z to match this convention. Continuous polygon boundaries pass through those centers; the raster includes centers on or inside the polygon and excludes every center outside it. Individual cubic blocks can straddle the ideal mathematical edge by up to half a block. Diagonal and curved boundaries therefore have Minecraft's staircase precision.

Boundary elevations interpolate linearly along each selected segment. Cells within approximately 0.71 blocks of the perimeter are pinned to the nearest segment height. Existing non-air blocks in this edge band are never replaced or cleared, even if their material is absent from the protection list. Air in those columns can be filled to meet roads. Elsewhere inside the polygon, configured protected materials are preserved at every elevation. **Unknown road blocks deeper inside the polygon cannot be identified automatically:** keep roads outside the selection and configure every road material that needs secondary protection. Preserve intentional road structures above terrain by leaving clearing disabled.

`sample` examines only already-loaded edge chunks on the server thread. It samples exposed solid blocks within eight blocks above/below the interpolated height along each straight segment and applies all samples only if validation succeeds. It does not trace a road curve or identify road materials. Inspect sampled elevations and preview the changed polygon before confirming. Manually select bends; long straight chords across curved roads are unsafe.

## Safety, threading and recovery

Geometry and heightmap work use one bounded worker queue (16 queued jobs). The default area limit is 1,000,000 square blocks. Inclusive edge columns can make the raster column count slightly greater than continuous polygon area. The bounding rectangle is also bounded to four times the configured maximum area, so extremely thin/long polygons may need splitting. Before raster allocation, a conservative working-memory estimate is checked against both `max-memory-mb` and half the available heap. Estimates cover the plugin raster/geometry, **not FAWE chunk queues, the server, disk history or other plugins**. Heightmaps use primitive arrays. Preview particles are capped per player per second, filtered by distance, and never load chunks or change blocks. Numbered controls use chat rather than temporary display entities.

Edits use real FAWE `EditSession` calls off the server thread. FAWE controls chunk queues, batching, lighting and world access; TerrainMesh does not read Bukkit world blocks from workers or set Bukkit blocks directly. Only selected interior columns are visited. Sessions close before reporting completion, then join the player's `LocalSession` history. TerrainMesh explicitly captures the world before queueing and forces non-fast editing with disk-backed history. Accounts with zero FAWE history allowance are refused. FAWE account/region limits still apply; configure history disk capacity, retention and permissions for large edits. TerrainMesh enforces its own conservative block cap as well. TerrainMesh does not apply WorldEdit session transforms or global source/destination masks; its polygon and material protection are always active.

Intersecting bounding rectangles of TerrainMesh edits in the same world are locked through FAWE flush and history registration. This is deliberately conservative and may reject non-overlapping concave shapes. External FAWE commands, other plugins and ordinary player block changes do not participate in these locks. Avoid other editing tools or player building in the generation area until completion, and avoid concurrent WorldEdit history operations for the generating player.

Cancellation is cooperative. A queued/planning job can stop before any modification. A running job checks cancellation between submissions, stops adding work, closes the session to flush already queued changes, and remembers the partial edit. It **does not** atomically cancel FAWE's queue or automatically roll back. On failure, a partial edit may remain; the player receives a recovery message and the server records the exception. History registration is attempted even after submission/flush failure. Use `//undo` after the job stops. If FAWE flush/history storage itself fails, inspect logs and restore from backups as necessary; undo cannot be guaranteed after disk failures, process crashes or expired history. Disconnects request cancellation and discard selection state. Shutdown requests cancellation without blocking the server thread on FAWE; avoid hot reload/plugin managers and wait for jobs to finish before planned server shutdown.

`generation.clear-above` defaults to false. When explicitly enabled in the administrator configuration, generation also clears above the surface through `generation.clear-to-y` (world-clamped), while retaining protected materials and existing edge blocks. The confirmation warns when clearing is enabled, and its conservative estimate includes clearing. Nothing above this ceiling is touched. Budget violations reject the plan before editing.

## Configuration

The supplied `config.yml` includes the requested terrain layers, smoothing/resolution/noise defaults, preview flags, material mask and MiniMessage prefix. Additional budgets are `generation.max-blocks`, `generation.max-memory-mb`, `generation.max-points`, `generation.max-hills`, confirmation lifetime, preview lifetime and particle count. Block layer materials must be solid and non-air. Invalid settings prevent plugin activation with an explicit log message. Restart after editing configuration; no live reload command is supplied.

Large fills can contain hundreds of millions of vertical blocks even when horizontal area is within the area limit. The default 50,000,000 submission cap may require increasing bottom Y or splitting projects. Leave memory, disk and server tick headroom for FAWE and test representative edits before raising limits. TerrainMesh does not promise a fixed throughput or tick-time bound.

## API references

- [FAWE API usage and threading](https://intellectualsites.gitbook.io/fastasyncworldedit/api/api-usage)
- [FAWE 2.16.2 EditSessionBuilder API](https://intellectualsites.github.io/fastasyncworldedit-javadocs/worldedit-core/com/sk89q/worldedit/EditSessionBuilder.html)
- [WorldEdit local sessions and undo](https://worldedit.enginehub.org/en/latest/api/concepts/local-sessions/)
- [JTS polygon triangulation](https://locationtech.github.io/jts/javadoc/org/locationtech/jts/triangulate/polygon/PolygonTriangulator.html)

JTS is distributed under EPL-2.0 / EDL-1.0. See the included dependency license metadata. Paper and FAWE remain separate server plugins with their own licenses.
