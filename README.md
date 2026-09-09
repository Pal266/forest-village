# Forest Settlement

A 3D village-builder / life-simulation game in the spirit of *Manor Lords* and
*Life is Feudal: Forest Village*, built from scratch in Java with LWJGL
(GLFW + OpenGL).

## Status

Currently at **R5 (Strategy Camera)** of the R0–R40 development roadmap. A
window, fixed-timestep game loop, shader-based rendering, indexed meshes, 3D
transforms and an orbiting strategy camera are all in place; there's no
terrain, world content or simulation yet.

- [`Forest Settlement Roadmap.md`](Forest%20Settlement%20Roadmap.md) — the full R0–R40 plan
- [`Tutorials/`](Tutorials) — a step-by-step writeup of each completed release

## Requirements

- Java 21+
- Maven
- Windows — native LWJGL binaries are currently pinned to `natives-windows` in `pom.xml`

## Running

```
mvn compile exec:java -Dexec.mainClass=forestsettlement.Main
```

## Controls

| Input | Action |
| --- | --- |
| `W` `A` `S` `D` | Pan the camera |
| Right-click drag | Rotate the camera |
| Scroll wheel | Zoom |
| `Space` | Pause / resume the simulation clock |
| `Esc` | Quit |
| `F` *(debug mode only)* | Toggle back-face culling — R3 demo |
| `-` / `=` *(debug mode only)* | Move the far clip plane — R4 demo |

## Configuration

`src/main/resources/system.properties` controls debug mode via `debug = true`/`false`.
Debug mode enables the GLFW debug context, GL error logging to stderr, and the
debug-only keybinds above.

## Tests

```
mvn test
```

## Project layout

| Package | Owns |
| --- | --- |
| `forestsettlement` | Entry point, game loop orchestration |
| `forestsettlement.window` | GLFW window lifecycle |
| `forestsettlement.camera` | Strategy camera and its input handling |
| `forestsettlement.render` | Shaders and meshes |
| `forestsettlement.scene` | Current (placeholder) world content |
| `forestsettlement.time` | Fixed-timestep simulation clock |
| `forestsettlement.properties` | Runtime configuration |

## Logs

Each run writes a timestamped log to `logs/` (git-ignored) in addition to
console output; see `src/main/resources/tinylog.properties`.
