# FOREST SETTLEMENT

Java 21 \+ LWJGL 3

## R0–R40 Development Roadmap

A release\-oriented technical specification for building a 3D medieval settlement and life\-simulation game in the spirit of *Manor Lords* and *Life is Feudal: Forest Village*. The roadmap grows the game and its supporting engine together, release by release, rather than building a generic engine up front.

Baseline: Java 21 · Maven · LWJGL 3.4.3 · GLFW · OpenGL 3.3 core · GLSL · JOML 1.10.9

Prepared: 29 August 2026

## How to use this roadmap

Each release (`R0`–`R40`) is a working milestone, not a calendar sprint — it is done when its acceptance criteria hold, not when a deadline passes. A release is the unit of planning; the "Requirements" under each one are the pieces of engineering that release needs, not a strict build order within it.

### Project principles

- **Game first, engine second.** Every reusable abstraction should be pulled out of something the game already needed, not built speculatively ahead of demand.
- **Simulation and presentation are separate.** OpenGL state is never authoritative game state — you must be able to describe "what the settlement contains" without a render call in sight.
- **Fixed\-step simulation.** Hunger, growth, production and movement advance on a fixed tick, never on render frame time.
- **Correctness before scale.** Prove the mechanic with one villager and one house before worrying about a thousand of either.
- **Headless\-testable simulation.** The simulation core must run — and be asserted against — in unit tests with no window and no GPU.
- **Content moves out of Java over time.** Early releases hard\-code buildings and recipes; later releases externalize them so content growth doesn't mean new Java classes.
- **No ECS by default.** Plain Java domain objects are the baseline architecture; a data\-oriented or ECS\-style redesign is earned by a measured bottleneck, not assumed up front.
- **Placeholder art is fine until the loop is fun.** Visual polish is deliberately deferred behind gameplay correctness in every phase.
- **Make consequences visible.** A correct simulation nobody can feel is a failed one — *Citystate II*'s own postmortem names exactly this as its central design mistake. Every system that affects the player (a shortage, an unhappy household, a completed building) needs a near\-term, legible signal, not just a technically correct long\-run number.

### Release groups

| Phase | Releases | Outcome |
| --- | --- | --- |
| Foundation | R0–R7 | Window, loop, OpenGL fundamentals, camera, textures, lighting |
| World | R8–R16 | Terrain, picking, asset pipeline, building placement, pathfinding, first villager |
| Simulation | R17–R30 | Resources, jobs, logistics, construction, needs, population, farming, seasons |
| Game & scale | R31–R37 | Roads, UI, shadows, vegetation, culling, large\-population performance |
| Persistence & slice | R38–R40 | Save/load, presentation pass, distributable playable vertical slice |

### Dependencies between releases

Most releases only assume the previous release's acceptance criteria hold. A few create harder cross\-references worth tracking explicitly if you ever reorder or parallelize work: R14's world grid is a hard prerequisite for R15 (pathfinding) and R31 (roads); R15 is a hard prerequisite for R16 and R20; R17's simulation architecture is a soft prerequisite the entire Simulation phase (R18–R30) leans on; and R26's data\-driven definitions is a soft dependency — later content\-heavy releases are easier with it in place but don't strictly require it first. Everything else under a release's "Requirements" is scoped to that release alone.

### Two kinds of release: engine vs. game mechanics

Not every release carries the same kind of commitment. Some releases build technical capability that has nothing to do with which specific game you're making — a terrain renderer, a pathfinder, a save system — and once built, revisiting them should be rare and driven by a real technical need, not a design change. Others encode this particular game's rules — which resources exist, what a villager needs to survive, how a field grows crops — and those should be expected to change as the design evolves through playtesting. Treat the first kind as close to a permanent foundation, and the second kind as a first draft.

The numbering stays sequential rather than split into two separate tracks, because a handful of engine releases can only be built meaningfully once real game content exists to exercise them — you can't profile large\-population performance (R37), tune save/load (R38), or add animation and audio (R39) against a settlement that has no citizens, resources or buildings yet. So engine and mechanics releases are necessarily interleaved; the table below tells you which is which, without implying you must finish all of one before starting the other.

| Release | Title | Layer |
| --- | --- | --- |
| R0 | Bootstrap and a Living Window | Engine |
| R1 | Game Loop and Time | Engine |
| R2 | First Programmable Render | Engine |
| R3 | Indexed Geometry and Meshes | Engine |
| R4 | 3D Mathematics and Transformations | Engine |
| R5 | Strategy Camera | Engine |
| R6 | Textures and Materials | Engine |
| R7 | Lighting Fundamentals | Engine |
| R8 | Terrain Mesh | Engine |
| R9 | Terrain Materials | Engine |
| R10 | Mouse Picking and World Selection | Engine |
| R11 | Asset Pipeline and Model Loading | Engine |
| R12 | Scene and Render Representation | Engine |
| R13 | Building Placement | Mixed — the placement/preview/validation system is engine; specific building footprints are mechanics |
| R14 | World Grid and Spatial Queries | Engine |
| R15 | A\* Pathfinding | Engine |
| R16 | First Villager | Mixed — the movable\-agent/path\-following system is engine; "villager" as a concept is mechanics |
| R17 | Simulation Model and Systems | Engine |
| R18 | Resources and Inventories | Mechanics |
| R19 | Jobs, Tasks and Work Assignment | Mixed — the job/task engine is reusable; which jobs exist is mechanics |
| R20 | Forestry Vertical Slice | Mechanics |
| R21 | Storage and Hauling | Mixed — the hauling/reservation engine is reusable; "storage building" specifics are mechanics |
| R22 | Construction | Mechanics |
| R23 | Citizen Needs | Mechanics |
| R24 | Housing and Population | Mechanics |
| R25 | Production Buildings | Mixed — the recipe/workplace engine is reusable; specific recipes are mechanics |
| R26 | Data\-Driven Game Definitions | Engine |
| R27 | Agriculture | Mechanics |
| R28 | Calendar and Game\-Speed Simulation | Engine |
| R29 | Seasons and Temperature | Mechanics |
| R30 | Food, Heating and Survival Loop | Mechanics |
| R31 | Roads and Movement Costs | Mechanics |
| R32 | Developer UI and Diagnostics | Engine |
| R33 | Player UI and Settlement Information | Mixed — the UI framework and input\-capture split is engine; the specific panels shown are mechanics\-driven |
| R34 | Shadows and Outdoor Atmosphere | Engine |
| R35 | Forests, Instancing and Vegetation | Engine |
| R36 | Spatial Partitioning, Culling and LOD | Engine |
| R37 | Large\-Population Simulation | Engine |
| R38 | Save, Load and Versioning | Engine |
| R39 | Animation, Audio and Presentation Pass | Engine |
| R40 | Playable Vertical Slice and Distribution | Integration — proves engine and mechanics work together |

25 of the 41 releases are pure engine, 9 are pure game mechanics, 6 are mixed (a reusable capability proven out through placeholder game content), and R40 is the integration milestone that ties both together. If the game's design changes significantly during development, expect that to land mostly in the Mechanics and Mixed rows — the Engine rows are the ones worth protecting from churn.

## R0 — Bootstrap and a Living Window

Goal. Stand up the smallest reliable Java/LWJGL application: a native window, an OpenGL context, a clean lifecycle, and enough diagnostics to trust it.

### Requirements

- Java 21 Maven project with a reproducible, single\-command build
- LWJGL 3.4.3 core, GLFW and OpenGL modules with the correct platform natives (via the [lwjgl.org/customize](https://www.lwjgl.org/customize) configurator)
- Create, show, resize and close a GLFW window
- Make the OpenGL context current and create LWJGL's `GLCapabilities`
- V\-sync configuration and a framebuffer\-resize callback
- An OpenGL debug context/callback enabled in development builds
- Deterministic cleanup of callbacks, the window handle and GLFW itself on exit

### Acceptance criteria

- The application starts from both the IDE and a plain Maven command
- The window resizes without errors or a distorted viewport
- Escape and the window's close button both exit cleanly with no leaked native handles

### Explicitly out of scope

- No custom shaders yet
- No game simulation or engine framework beyond what this window needs

## R1 — Game Loop and Time

Goal. Build the runtime heartbeat and explicitly separate input, simulation update and rendering — the seam every later release depends on.

### Requirements

- Main\-thread GLFW event polling
- High\-resolution timing via `glfwGetTime()`
- A fixed\-step update accumulator, independent of render cadence
- Pause and a basic time\-scale hook (even if only `0x`/`1x` for now)
- Frame and update counters exposed for diagnostics

### Acceptance criteria

- Simulation updates stay stable and reproducible when render frame rate varies
- Pausing halts simulation time without corrupting the accumulator
- The timing/accumulator code is isolated enough to unit test without a window

### Explicitly out of scope

- No in\-game calendar yet — this is raw engine time, not game time
- No multithreaded simulation

## R2 — First Programmable Render

Goal. Understand the modern OpenGL pipeline by rendering a primitive by hand, before any abstraction hides the API.

### Requirements

- A minimal vertex and fragment shader pair in GLSL
- Shader compilation and linking with readable, log\-surfaced error reporting
- VAO/VBO creation and vertex attribute configuration
- Triangle rendering through your own shader program
- Explicit cleanup of every OpenGL object you created

### Acceptance criteria

- A triangle renders using a shader program you wrote and linked yourself
- Deliberately broken shader source produces a clear, readable failure — not a silent black screen
- All GPU resources created this release are released on shutdown

### Explicitly out of scope

- No generic material or shader\-management system
- No model loading

## R3 — Indexed Geometry and Meshes

Goal. Move from one hand\-typed triangle to reusable indexed geometry and the first small rendering abstraction.

### Requirements

- Element/index buffers (EBOs) alongside vertex buffers
- A clearly defined, interleaved vertex layout
- A `Mesh` type with an explicit ownership/lifecycle contract
- Support for drawing several distinct meshes in one frame
- Back\-face culling and a consistent winding\-order convention

### Acceptance criteria

- A quad and a cube both render from indexed geometry
- `Mesh` contains no game\-domain logic — it is purely a rendering primitive
- Culling can be toggled and its effect explained, not just observed

### Explicitly out of scope

- No textures yet
- No scene graph

## R4 — 3D Mathematics and Transformations

Goal. Bring JOML into the project and establish the model/view/projection pipeline that every future object depends on.

### Requirements

- JOML 1.10.9 vectors and matrices as the project's only math types
- Translation, rotation and scale composed into model matrices
- Perspective projection with a configurable field of view and clip planes
- Model, view and projection matrices uploaded as shader uniforms
- Depth testing enabled and understood, not just switched on

### Acceptance criteria

- Several cubes render simultaneously at distinct world transforms
- Window resize updates the projection matrix's aspect ratio correctly
- Near/far clipping and depth\-buffer behavior can be demonstrated and explained

### Explicitly out of scope

- No interactive camera yet — a fixed or scripted view is fine

## R5 — Strategy Camera

Goal. Replace the fixed view with the angled, constrained camera a settlement builder needs.

### Requirements

- A camera with an explicit position, target and constrained pitch
- Keyboard and/or screen\-edge panning
- Mouse\-drag rotation around the focus point
- Mouse\-wheel zoom with sensible min/max bounds
- View\-matrix generation driven entirely by JOML

### Acceptance criteria

- The camera can comfortably navigate a placeholder world (a flat, textured plane is enough)
- Camera movement is frame\-rate independent
- The camera exposes no OpenGL concepts to code outside the render layer

### Explicitly out of scope

- No terrain collision for the camera yet
- No cinematic/cutscene camera behavior

## R6 — Textures and Materials

Goal. Render textured geometry and establish a minimal material representation.

### Requirements

- Image loading via `stb_image`
- Per\-vertex texture coordinates
- Texture creation with filtering and wrapping configured deliberately
- Sampler uniforms wired through the shader pipeline
- A minimal `Material` type (at least a diffuse texture reference)
- sRGB/gamma handling documented for later, even if not fully solved now

### Acceptance criteria

- A textured cube and ground plane both render correctly
- A missing or malformed texture file fails with a clear diagnostic, not a crash
- Texture resources are owned and released explicitly, not left to the GC

### Explicitly out of scope

- No physically based rendering pipeline
- No texture atlasing or packing optimization

## R7 — Lighting Fundamentals

Goal. Add outdoor lighting readable enough for a prototype terrain and buildings.

### Requirements

- Correct per\-vertex or per\-fragment normals
- A single directional "sun" light
- Ambient, diffuse and specular contributions
- Correct normal transformation under non\-uniform scale
- Lighting parameters kept separate from any game\-domain state

### Acceptance criteria

- Meshes respond correctly to a movable directional light
- Normals can be visualized or otherwise verified as correct
- Lighting composes correctly with the textured meshes from R6

### Explicitly out of scope

- No shadows yet
- No physically based shading model

## R8 — Terrain Mesh

Goal. Replace the placeholder ground plane with the game's actual world surface.

### Requirements

- A grid\-based terrain representation with explicit width/height/resolution
- Height data (a generated or loaded heightmap)
- Terrain mesh generation from that height data
- Per\-vertex normals derived from the terrain geometry
- World\-to\-terrain coordinate conversion
- Terrain chunking as a boundary in the data model, even if not yet load\-streamed

### Acceptance criteria

- A non\-flat heightfield renders and can be navigated with the R5 camera
- Terrain dimensions and world scale are explicit, documented values
- Terrain generation is fully decoupled from the OpenGL upload step

### Explicitly out of scope

- No procedural biome system
- No streaming/infinite world

## R9 — Terrain Materials

Goal. Make the terrain read as a game surface — grass, dirt, rock — rather than one flat texture.

### Requirements

- At least three distinct terrain surface types
- A splat/blend\-map approach (or an equivalent prototype technique)
- Slope\- and height\-derived material classification
- Grass/dirt/rock transitions blended in the terrain shader
- Material sampling driven by the classification data, not hand\-painted

### Acceptance criteria

- The terrain visibly contains at least three blended materials
- Material classification can be regenerated deterministically from terrain data
- The renderer never becomes the source of truth for terrain material state

### Explicitly out of scope

- No final art pipeline
- No seasonal terrain variation yet

## R10 — Mouse Picking and World Selection

Goal. Translate 2D mouse input into a meaningful position in the 3D world.

### Requirements

- Screen\-to\-normalized\-device\-coordinate conversion
- Inverse projection/view transforms (JOML's unproject helpers)
- World\-space ray construction from the camera
- Ray/terrain intersection
- A hovered\-tile indicator for feedback
- A small, explicit selection\-input model (hover vs. click vs. drag)

### Acceptance criteria

- Moving the mouse consistently identifies a stable terrain location
- Selection stays correct across camera movement and window resize
- The picked location can be shown via a debug visualization

### Explicitly out of scope

- No building placement yet — this release is picking only

## R11 — Asset Pipeline and Model Loading

Goal. Load real 3D assets while keeping the asset format strictly separate from the runtime representation.

### Requirements

- Assimp integration through LWJGL's bindings
- An OBJ/glTF\-oriented import path
- Mesh, UV, normal and material extraction from imported models
- A consistent asset\-path convention
- A clear line between CPU\-side asset data and GPU resources
- Asset caching and failure handling (missing file, unsupported format)

### Acceptance criteria

- A simple cottage model loads from disk and renders correctly
- A single imported model can contain multiple meshes and materials
- Missing or malformed assets fail with an actionable diagnostic

### Explicitly out of scope

- No skeletal animation yet
- No hot\-reload requirement

## R12 — Scene and Render Representation

Goal. Build a clean presentation\-side representation capable of holding many visible objects.

### Requirements

- A `RenderInstance`/similar type: a mesh/material reference plus a transform
- Instance transforms kept independent of mesh asset data
- An explicit boundary between the "scene" (render data) and the game world
- Stable IDs or handles used wherever objects need to be referenced
- Basic render submission and ordering

### Acceptance criteria

- The same house asset renders correctly at many different transforms
- Removing a game object cleanly removes its presentation counterpart
- Simulation\-side code never calls OpenGL directly

### Explicitly out of scope

- No full ECS
- No sophisticated render graph or sorting

## R13 — Building Placement

Goal. Ship the first genuinely game\-shaped interaction: choose a building and place it in the world.

### Requirements

- Building definitions with an explicit footprint
- A placement preview/"ghost" render
- Placement rotation
- A terrain occupancy grid
- Slope/bounds/overlap validation before confirming placement
- Confirm and cancel input handling

### Acceptance criteria

- A player can select a house, preview it, rotate it and place it
- Invalid positions are rejected with visible, immediate feedback
- A placed building becomes a persistent simulation object, not just a render instance

### Explicitly out of scope

- No construction cost or build time yet — placement is instant
- No builder villagers yet

## R14 — World Grid and Spatial Queries

Goal. Introduce the simulation\-facing spatial structures that pathfinding, placement and logistics all depend on.

### Requirements

- A walkability grid over the world
- Static obstacles derived from terrain slope and placed buildings
- Coordinate conversions between world space, grid cells and terrain chunks
- Neighborhood/area queries over the grid
- Dirty\-region updates when buildings are placed or removed

### Acceptance criteria

- Placed buildings correctly block the grid cells under their footprint
- Walkability can be debug\-rendered as an overlay
- Grid queries are covered by unit tests independent of rendering

### Explicitly out of scope

- No pathfinding optimization beyond correctness at this stage

## R15 — A\* Pathfinding

Goal. Implement pathfinding as understood, tested engine code — not a black\-box dependency.

### Requirements

- Open/closed set management for A\*
- An admissible heuristic (Manhattan or octile, matching the movement policy)
- Explicit movement costs, including terrain\-derived cost where relevant
- A defined diagonal\-movement policy
- Path reconstruction from the search result
- Defined behavior for an unreachable target
- A debug overlay and basic metrics (nodes expanded, path length)

### Acceptance criteria

- A found path correctly routes around buildings and impassable terrain
- Unreachable destinations return a well\-defined result, never a hang or crash
- Core pathfinding logic has deterministic unit tests independent of the simulation loop

### Explicitly out of scope

- No hierarchical pathfinding
- No crowd/local avoidance yet

## R16 — First Villager

Goal. Add the first autonomous simulated citizen and make it traverse a calculated path.

### Requirements

- A villager identity/state model
- Position and movement speed on the fixed simulation tick
- A "move to destination" request API
- Path\-following behavior driven by the R15 pathfinder
- An explicit arrival state
- Interpolation from simulation state to render position

### Acceptance criteria

- One villager can be ordered to a reachable point and reliably arrives
- Movement speed is independent of render frame rate
- Pausing the simulation stops villager movement without breaking rendering

### Explicitly out of scope

- No jobs, needs or animation yet — this is movement in isolation

## R17 — Simulation Model and Systems

Goal. Formalize the simulation's domain architecture before its complexity grows further.

### Requirements

- An explicit simulation "world" aggregate with clear ownership boundaries
- A clear split between domain entities and the systems that act on them
- Stable entity identifiers, independent of array position
- A command/event approach used where it genuinely simplifies coordination
- An explicit tick\-ordering policy across systems
- Simulation tests that run with no OpenGL context at all

### Acceptance criteria

- A headless test can advance simulation ticks with no window or GPU
- The core simulation module has zero LWJGL dependency
- System execution order is documented and deterministic

### Explicitly out of scope

- No distributed or multiplayer simulation
- No mandatory ECS framework — plain domain objects remain the baseline

## R18 — Resources and Inventories

Goal. Introduce the material economy that logistics and construction will later depend on.

### Requirements

- Defined resource types (starting with wood and stone)
- Stack/quantity semantics per resource
- An `Inventory` type with an explicit capacity
- Transfer operations between inventories
- Resource nodes in the world (e.g. a tree holding wood potential)
- Validation and invariants preventing negative or duplicated quantities

### Acceptance criteria

- Wood and stone can exist both in world resource nodes and in inventories
- Transfers can never duplicate or silently destroy resources
- Inventory invariants are covered by unit tests

### Explicitly out of scope

- No production chains yet

## R19 — Jobs, Tasks and Work Assignment

Goal. Give villagers autonomous work through explicit jobs and executable tasks.

### Requirements

- Job definitions (what needs doing, and where)
- Task decomposition (a job broken into ordered steps a villager executes)
- Worker availability tracking
- Job priority
- A claim/reservation lifecycle so two villagers can't take the same job
- Failure, cancellation and retry states

### Acceptance criteria

- An idle villager can claim an available job and execute a simple task
- Two workers can never accidentally claim the same exclusive task
- A failed job correctly releases its reservations

### Explicitly out of scope

- No colony\-wide job optimizer — nearest\-available assignment is enough for now

## R20 — Forestry Vertical Slice

Goal. Close the first complete, end\-to\-end autonomous gameplay loop.

### Requirements

- Trees as resource nodes
- Logic to find an eligible tree for a job
- A villager walking to the work target via R15/R16
- A timed chopping action
- Log resource creation on completion
- Basic tree depletion/removal

### Acceptance criteria

- A villager autonomously walks to a tree and produces logs with no manual intervention
- The loop holds correctly at multiple simulation speeds
- Destroying or invalidating a target mid\-task is handled safely, not with a crash

### Explicitly out of scope

- No forest regrowth ecology yet
- No detailed chopping animation

## R21 — Storage and Hauling

Goal. Turn produced resources into a genuine logistics problem.

### Requirements

- A stockpile/storage building type
- Automatic haul\-job generation for produced resources
- Pickup and drop\-off reservations
- Inventory transfer as part of a haul task
- Destination selection among available storage
- Basic storage capacity rules

### Acceptance criteria

- Logs produced in R20 can be carried from the forest into storage
- Resources are never double\-reserved by competing haul jobs
- A full storage building produces a recoverable job state, not a stuck villager

### Explicitly out of scope

- No carts or vehicles
- No advanced logistics routing/optimization

## R22 — Construction

Goal. Make building placement create work, rather than producing an instantly finished structure.

### Requirements

- An explicit construction\-site state for placed buildings
- Per\-building material requirements
- Delivery tasks that feed a construction site
- Builder work/progress accumulation
- A defined completion transition to a functional building
- A cancel/refund policy for abandoned sites

### Acceptance criteria

- A placed house requires both delivered resources and accumulated work before it functions
- Multiple required resource types are supported by a single building definition
- Construction progress is explicit, saveable state

### Explicitly out of scope

- No building upgrade system yet
- No demolition/salvage balancing

## R23 — Citizen Needs

Goal. Add the first survival pressures that make the settlement simulation matter.

### Requirements

- A hunger need
- A rest/home concept
- A warmth placeholder (full heating arrives in R30)
- Need decay driven by simulation time, not render time
- A policy for how needs interrupt or reorder a villager's current task
- Deliberately minimal death/incapacitation rules for now

### Acceptance criteria

- Needs change with simulated game time, independent of render FPS
- A hungry citizen actively seeks an available food source
- Need thresholds and decay rates are data/config values, not hard\-coded literals

### Explicitly out of scope

- No deep psychological or mood modeling
- No disease system yet — see Post\-R40 possibilities

## R24 — Housing and Population

Goal. Turn buildings and villagers into a genuine settlement population model.

### Requirements

- House capacity
- Resident assignment to a specific house
- A household concept grouping residents
- A basic age/lifecycle model
- Population counters and summary statistics
- Simple, config\-driven birth/death rules

### Acceptance criteria

- Citizens can be assigned to homes and tracked as residents
- Population state survives long simulation runs without broken references
- Population statistics can be derived entirely from simulation state

### Explicitly out of scope

- No genetics
- No complex social\-relationship modeling
- No population growth beyond births yet — see Post\-R40 possibilities

## R25 — Production Buildings

Goal. Generalize "work" beyond forestry using recipes and workplaces.

### Requirements

- Workplace slots within a building
- Input and output inventories per building
- Production recipe definitions
- Worker assignment to a workplace
- Production progress accumulation
- Correct blocking behavior when input or output space is unavailable

### Acceptance criteria

- At least one production building transforms input resources into an output resource
- Recipe logic is reused across multiple building types without duplication
- Production stops correctly, and resumes correctly, around input/output shortages

### Explicitly out of scope

- No complex market economy

## R26 — Data\-Driven Game Definitions

Goal. Move content balancing out of Java source and into data.

### Requirements

- External resource type definitions
- External building definitions
- External recipe definitions
- A validation/schema strategy for loaded content
- Stable definition IDs, independent of display names
- Startup content loading with clear diagnostics

### Acceptance criteria

- A new resource or recipe can be added without writing a new Java class
- Invalid content definitions fail loudly, with actionable error messages
- Save files reference stable IDs, never display names, for forward compatibility

### Explicitly out of scope

- No mod SDK
- No runtime scripting language

## R27 — Agriculture

Goal. Add seasonal food production and area\-based work.

### Requirements

- Farm\-field designation over terrain
- Crop definitions
- Plant/grow/harvest state machine per field
- Worker tasks for each farming stage
- Yield calculation
- A soil/fertility placeholder, if useful for later balancing

### Acceptance criteria

- A player can designate a field and eventually obtain a harvested food resource
- Crop growth advances on simulation time, not render time
- Multiple workers can cooperate on a field without corrupting its state

### Explicitly out of scope

- No detailed soil chemistry
- No livestock yet

## R28 — Calendar and Game\-Speed Simulation

Goal. Introduce the explicit calendar that will drive farming, needs and later seasons.

### Requirements

- A tick\-to\-minutes/hours/days mapping
- A day/month/year calendar
- 0x/1x/2x/5x/10x speed controls
- Scheduled and periodic simulation systems (daily/weekly triggers)
- Long\-run determinism tests where practical

### Acceptance criteria

- The game can simulate multiple in\-game years without drift or crash
- Changing game speed changes simulated time, never simulation rules or outcomes
- Calendar transitions are testable headlessly, with no rendering involved

### Explicitly out of scope

- No weather system yet

## R29 — Seasons and Temperature

Goal. Make the annual cycle affect both simulation and presentation.

### Requirements

- A spring/summer/autumn/winter model
- A temperature curve driven by the calendar
- Crop season restrictions tied to R27
- Heating\-demand hooks for R30
- Seasonal terrain/vegetation presentation hooks
- A day\-length hook for later lighting use

### Acceptance criteria

- Season transitions occur purely from calendar state
- Agriculture visibly reacts to the current season
- A full simulated season can run headlessly

### Explicitly out of scope

- No full weather system
- No snow\-accumulation simulation

## R30 — Food, Heating and Survival Loop

Goal. Connect production, needs, housing and seasons into the first meaningful survival challenge.

### Requirements

- Food consumption tied to citizen needs
- A food storage/spoilage policy, if adopted
- Firewood/fuel as a resource
- House heating consuming that fuel
- Defined cold consequences
- Shortage warnings surfaced from simulation state

### Acceptance criteria

- A settlement can genuinely fail from a food or heating shortage
- Resources flow correctly through production and logistics into citizen needs
- Every failure condition is explainable directly from inspectable game state

### Explicitly out of scope

- No final balancing pass
- No disease/epidemic system

## R31 — Roads and Movement Costs

Goal. Give the player infrastructure that shapes logistics efficiency.

### Requirements

- Road placement over valid terrain
- Road occupancy on the R14 world grid
- Movement\-cost modifiers for road tiles
- Pathfinder integration with the new costs
- Road removal and grid updates
- Simple road visuals

### Acceptance criteria

- Villagers prefer routes through roads when the cost makes them advantageous
- Adding or removing roads safely updates in\-flight pathfinding
- Roads can only be built over valid terrain

### Explicitly out of scope

- No traffic simulation
- No carts/vehicles

## R32 — Developer UI and Diagnostics

Goal. Make an increasingly complex simulation observable and debuggable.

### Requirements

- Dear ImGui integration via `imgui-java` (SpaiR's LWJGL3 binding)
- FPS/UPS/frame\-time panels
- A selected\-entity inspector
- A job/task queue view
- Pathfinding statistics
- Simulation speed and debug controls
- Toggleable debug overlays

### Acceptance criteria

- A developer can inspect a villager's current state, job and path live
- The debug UI never becomes authoritative game state
- All diagnostics can be fully disabled for a production build

### Explicitly out of scope

- This is not the final player\-facing UI

## R33 — Player UI and Settlement Information

Goal. Provide the minimum interface required to actually play without developer tools.

### Requirements

- A build menu
- Resource counters
- A population summary
- A selected building/citizen panel
- Warnings and notifications (e.g. a food shortage)
- Time controls exposed to the player
- A clear split between UI input capture and world input capture

### Acceptance criteria

- The core game loop can be played end\-to\-end without opening the ImGui developer windows
- The UI correctly reflects live simulation state
- Clicking UI elements never accidentally places or selects world objects

### Explicitly out of scope

- No final visual polish
- No fully accessibility\-complete UI yet

## R34 — Shadows and Outdoor Atmosphere

Goal. Deliver the first substantial rendering\-quality upgrade.

### Requirements

- Directional\-light shadow mapping
- A depth framebuffer
- Shadow\-space transforms
- Bias and shadow\-acne/peter\-panning handling
- A distance fog/atmosphere cue
- A quality setting to scale the feature back

### Acceptance criteria

- Buildings, trees and terrain all cast usable directional shadows
- Common shadow artifacts are controlled to an acceptable level
- Shadows can be disabled entirely for diagnostics or performance

### Explicitly out of scope

- No full PBR pipeline
- No cinematic post\-processing suite

## R35 — Forests, Instancing and Vegetation

Goal. Render settlement\-scale vegetation efficiently.

### Requirements

- Instanced rendering for repeated meshes
- Per\-instance transform/data buffers
- Batched tree/rock/crop rendering
- Visibility grouping by terrain chunk
- Prototype vegetation distribution over terrain
- Per\-instance visual variation (scale/rotation jitter)

### Acceptance criteria

- Thousands of repeated vegetation objects render with materially fewer draw calls than one\-per\-object
- Gameplay tree entities (from R20) can still be individually removed or changed without rebuilding the whole world
- Rendering performance metrics for this system are visible in the R32 diagnostics

### Explicitly out of scope

- No final biome generator
- No GPU\-driven rendering requirement

## R36 — Spatial Partitioning, Culling and LOD

Goal. Scale rendering and world queries to larger maps.

### Requirements

- A chunk/quadtree or equivalent spatial partition
- View\-frustum culling
- A distance\-based level\-of\-detail policy
- Chunk\-level visibility determination
- Spatial\-query acceleration reused by pathfinding/placement
- Before/after profiling of this work

### Acceptance criteria

- Off\-screen chunks are not submitted to the renderer
- LOD transitions preserve gameplay\-relevant object identity (nothing "changes species" as it simplifies)
- The measured performance improvement is recorded, not just assumed

### Explicitly out of scope

- No premature occlusion\-culling complexity unless profiling justifies it

## R37 — Large\-Population Simulation

Goal. Stress the CPU side deliberately and optimize measured bottlenecks, not theoretical ones.

### Requirements

- Synthetic population benchmark scenarios
- Simulation\-system timing instrumentation
- Reduced\-frequency updates for systems where that's valid (not every system needs every tick)
- Path caching/reuse
- Path\-request scheduling to avoid frame spikes
- Data\-oriented refactors applied only to measured hot paths
- Optional worker threads used only behind clear, documented ownership rules
- A hard rule, enforced by convention or an added assertion, that GLFW and OpenGL calls never happen off the main thread — worker threads compute simulation data only, handed back through a thread\-safe queue the main thread drains each frame

### Acceptance criteria

- Benchmarks exist for 10/100/500/1000\+ citizens with recorded results
- All optimizations preserve simulation correctness, verified by the R17 headless tests
- No race conditions are introduced by any optional parallelism
- No GLFW or OpenGL call is ever made from a worker thread, in code review or in an added debug assertion

### Explicitly out of scope

- No arbitrary promised citizen count
- No rewrite into an ECS without measured evidence it's needed

## R38 — Save, Load and Versioning

Goal. Persist settlements safely across code evolution.

### Requirements

- A versioned save\-game data\-transfer model
- Stable entity references across save/load
- A defined serialization format
- Load\-time reconstruction and validation
- Autosave and manual\-save hooks
- A migration strategy for future save\-format versions
- Atomic/defensive save\-file writing

### Acceptance criteria

- A non\-trivial settlement round\-trips correctly through save and load
- The loaded simulation contains no dangling references
- Corrupt or incompatible save files fail gracefully, without crashing the game

### Explicitly out of scope

- No cloud saves
- No promise of permanent backward compatibility

## R39 — Animation, Audio and Presentation Pass

Goal. Give the simulation enough presentation quality to feel alive without changing its architecture.

### Requirements

- A basic skeletal animation pipeline, or a pragmatic animated\-asset approach if that's a better fit
- Idle/walk/work animation states
- OpenAL / OpenAL Soft audio via LWJGL
- Positional world sounds
- Ambient settlement sound
- Particle effects for selected work/environment moments (chopping, construction dust, smoke)
- Animation state driven from simulation presentation data, never simulation logic

### Acceptance criteria

- Villagers visibly walk and work rather than sliding or T\-posing
- Key actions have matching sound feedback
- Audio and animation can both be disabled without changing simulation outcomes

### Explicitly out of scope

- No final art\-content volume
- No cinematic narrative system

## R40 — Playable Vertical Slice and Distribution

Goal. Consolidate the project into a stable, distributable prototype that demonstrates the complete settlement loop.

### Requirements

- New\-game flow and map generation/selection
- Buildable house, storage, production, farm and road types
- The full forestry, hauling, construction and food loop
- Population needs and seasonal survival
- Save/load available from the main flow
- Settings and key bindings sufficient for a prototype
- A profiling and regression pass across all systems
- Packaging with all required natives and assets
- A README covering controls and architecture notes

### Acceptance criteria

- A fresh player can start a settlement and survive at least one full seasonal cycle
- The packaged executable runs on the primary supported OS outside the IDE
- Core simulation tests pass, with no known save\-corruption or blocker\-class bugs
- The performance envelope and known limitations are documented

### Explicitly out of scope

- Not a commercial 1.0 release
- No multiplayer
- No requirement for final graphics, content volume or balance

## Definition of Done for every release

- The project builds from a clean checkout using the documented Java/Maven toolchain
- New behavior has automated tests wherever it can be tested without graphics
- OpenGL and other native resources introduced by the release have explicit ownership and cleanup
- No simulation rule depends on render frame rate
- Realistically diagnosable errors produce useful logs or messages, never a silent black screen
- The application still reaches every previous release's working behavior — no milestone is knowingly broken to reach the next one
- Any intentional technical debt or deferred optimization is written down before moving on

## Target architecture by R40

**Application layer** — startup, configuration, game states and orchestration.

**Simulation layer** — world, citizens, buildings, resources, jobs, pathfinding, calendar, production and needs. This layer has no LWJGL or OpenGL dependency at all.

**Presentation layer** — camera, render scene, terrain renderer, models, animation, effects and audio.

**Infrastructure layer** — asset loading, serialization, data definitions, diagnostics and platform integration.

**UI layer** — the player\-facing interface, kept separate from developer/debug tooling.

## Post\-R40 possibilities (not committed)

This list is deliberately a set of open categories, not a spec. It exists to show the roadmap has room for genre\-standard depth — not to decide now which of these get built, or in what form. Everything below sits on the Mechanics side of the engine/mechanics split described earlier in this document — expect it to be shaped by what the vertical slice teaches you, not decided now. Revisit it after R40, against what the game actually needs and what you actually want to build once you're there.

- Environmental hazards and events (fire, storms, blight, and similar) — a genre\-standard pressure once construction and needs exist
- Health and contagion mechanics beyond the baseline needs in R23/R30
- Population growth through channels other than births
- Military and conflict systems (defense, walls, sieges) — several reference games treat this as a late, separable addition rather than a core\-loop requirement
- Economic depth beyond simple production (taxation, external politics, regional rivals)
- Ecology and land\-use consequences (livestock, hunting/fishing, soil/deforestation effects)
- More sophisticated social simulation
- Procedural map generation and biomes
- Advanced terrain modification
- Better water, weather, snow and atmospheric rendering
- Mod support and content tooling
- More advanced animation and character rendering
- Further CPU/GPU optimization based on real profiling
- Multiplayer, as its own architectural initiative — never an incremental checkbox on this roadmap

## Comparison against genre references

This roadmap was checked against three sources: the systems actually shipped in *Manor Lords*, *Life is Feudal: Forest Village* and *Banished* — the games this project draws its genre from — and general guidance on structuring a solo/indie game\-development roadmap. The point of this comparison isn't to lock in a feature list ahead of time — what the game actually needs will keep changing as it's built — it's to check that the roadmap's shape leaves room for genre\-standard depth, and to flag anywhere a real gap was silently unaddressed rather than a deliberate cut.

### Where the roadmap already matches good practice

Defining each release by a goal, explicit requirements, acceptance criteria and an explicit out\-of\-scope list lines up closely with standard advice to plan milestones around concrete, testable outcomes rather than dates or task lists. The five release groups also translate reasonably onto the industry\-standard milestone vocabulary: Foundation \+ World (R0–R16) ≈ first playable; Simulation (R17–R30) ≈ alpha; Game & Scale (R31–R37) ≈ beta; Persistence & Slice (R38–R40) ≈ vertical slice.

### Genre\-pattern categories now visible in Post\-R40

None of these are commitments — they're categories the reference games all lean on in some form, added so the roadmap doesn't quietly forget they exist. Which of them get built, and exactly how, is a decision for after R40, informed by what the vertical slice actually needs.

| Category | Reference precedent | Where it now lives |
| --- | --- | --- |
| Environmental hazards/events | *Life is Feudal* (lightning, tornadoes, earthquakes) and *Banished* (fire, tornadoes, infestations) both build real pressure from this | Post\-R40 possibilities, as an open category |
| Health/contagion beyond baseline needs | *Banished* ties outbreaks to trade contact; *Life is Feudal* ties disease to cold and diet | Post\-R40 possibilities, as an open category |
| Population growth beyond births | Nomads joining the settlement are a *Banished* staple | Noted as one possible channel in Post\-R40; R24 stays open\-ended rather than committing to it |
| Military/conflict systems | *Manor Lords* treats combat and sieges as a defining pillar of its own roadmap, arriving well after the core economy | Post\-R40 possibilities, sequenced the same way — after the core loop, not before it |
| Economic depth (taxation, regional politics) | *Manor Lords* ties taxation to its trade economy | Post\-R40 possibilities |
| Land\-use consequences | *Manor Lords* links deforestation/overuse to soil degradation | Post\-R40 possibilities |

Two things from this comparison went into the committed roadmap (R0–R40) rather than the open list, because they're process/engineering points, not gameplay features that might change:

- **Make consequences visible** (a new project principle) — *Citystate II*'s postmortem names a simulation nobody can feel as its central design failure, a risk worth designing against from the start, whatever the specific systems end up being.
- **R37's GLFW/OpenGL main\-thread rule** — a fixed technical constraint from LWJGL itself, not a gameplay decision, so it stays a hard requirement rather than an open possibility.

### Deliberately left unchanged

Multiplayer, a mod SDK or scripting language, and full physically based rendering stay out of scope everywhere they were already excluded — nothing in this comparison changed that judgment. *Manor Lords'* own roadmap treats naval battles, AI\-controlled rival cities and cultural/faction systems as multi\-year additions well past its own equivalent of R40; this project treats them the same way.

## Technical reference note

This learning sequence is deliberately compatible with the approach used in Antonio Hernández Bejarano's free online book *3D Game Development with LWJGL 3*\: GLFW/window setup, the game loop, programmable rendering, geometry, projection, textures, camera and lighting all follow a very similar early arc. This roadmap diverges from it by organizing those same rendering concepts around a settlement\-game vertical slice from the start, and by treating the long\-running simulation as a first\-class architectural concern rather than an afterthought layered on top of a renderer.

Primary references to keep alongside this roadmap: the official LWJGL guide and build configurator at [lwjgl.org](https://www.lwjgl.org/guide); GLFW's own documentation; the Khronos OpenGL reference/wiki; the JOML reference at [joml\-ci.github.io/JOML](https://joml-ci.github.io/JOML/); Assimp's documentation for the model\-import path; OpenAL / OpenAL Soft for audio; RenderDoc for graphics debugging; Bejarano's LWJGL book at [ahbejarano.gitbook.io/lwjglgamedev](https://ahbejarano.gitbook.io/lwjglgamedev); and Red Blob Games' [A\* and pathfinding guide](https://www.redblobgames.com/pathfinding/a-star/introduction.html) for the concepts behind R15.
