# Spell Storm: System Architecture & Design Specification

## 1. Architectural Philosophy: Pure Simulation Decoupling

The primary architectural design principle of the **Spell Storm Java 21** migration is the **complete decoupling of pure game simulation logic from graphics rendering and windowing backends**.

In traditional game migrations, simulation and rendering are tightly coupled to graphics primitives (e.g., `sf::Sprite`, `sf::RenderWindow`, or LibGDX `SpriteBatch`). This makes automated headless testing difficult or requires heavy mock infrastructure. 

In Spell Storm Java:
1. **Simulation Subsystems** (`math`, `world`, `navigation`, `entities`, `combat`, `systems`) operate strictly on numeric coordinates, mathematical vectors, and domain models.
2. **Graphics & Audio Pipelines** (`assets`, `camera`, `ui`, `desktop`) act as thin projection layers that consume simulation state without mutating gameplay rules.
3. **Headless Execution Compatibility**: The entire game simulation can execute 60 ticks per second or simulate 120-second matches in milliseconds without requiring an X11/Wayland display server, an OpenGL GPU, or graphics hardware.

```
                      +-----------------------------+
                      |   DesktopLauncher (LWJGL3)  |
                      +--------------+--------------+
                                     |
                      +--------------v--------------+
                      |       SpellStormGame        |
                      +--------------+--------------+
                                     |
                      +--------------v--------------+
                      |        GameSession          |
                      +--+-----------------------+--+
                         |                       |
       +-----------------v---+               +---v-----------------+
       | Pure Simulation Core|               |  Presentation Layer |
       +---------------------+               +---------------------+
       | - Math & Collision  |               | - GameCamera        |
       | - TileMap & World   |               | - Asset Registry    |
       | - A* Navigation M8  |               | - Texture Manager   |
       | - Entities & Physics|               | - Font Manager      |
       | - Combat & Spells   |               | - HudRenderer       |
       | - Spawner & Waves   |               | - MinimapRenderer   |
       | - Progression & EXP |               | - LevelUpOverlay    |
       | - PowerUp Lifecycle |               | - GameOverOverlay   |
       +---------------------+               +---------------------+
```

---

## 2. Package Architecture (`com.spellstorm.*`)

The Java codebase is modularized under the root package `com.spellstorm`:

### 2.1 `com.spellstorm.desktop`
- **Role**: Application bootstrapping and native OS windowing.
- **Key Classes**:
  - `DesktopLauncher`: Configures the LibGDX `Lwjgl3ApplicationConfiguration` (1280x720 window, 60 FPS cap, VSync, window icon). Handles the `--headless` CLI flag for automated asset verification without opening an OpenGL window.

### 2.2 `com.spellstorm.core`
- **Role**: Master game coordinator and state machine.
- **Key Classes**:
  - `SpellStormGame`: Extends LibGDX `Game`, managing transitions between screens and life-cycle events.
  - `GameSession`: Central controller maintaining the player, entity collections, spatial bounds, combat systems, and update/render orchestration.
  - `GameState`: Enumeration defining game flow (`START`, `PLAYING`, `PAUSED`, `LEVEL_UP`, `GAME_OVER`).
  - `EngineConfig`: Command-line parser and configuration store for debug modes, screen dimensions, and asset verification flags.

### 2.3 `com.spellstorm.math`
- **Role**: Mathematical foundations and geometric collision queries.
- **Key Classes**:
  - `Vector2D`: Fluid 2D vector offering arithmetic, length/distance, normalization with sub-epsilon protection, dot/cross products, and angular calculations.
  - `MathUtils`: Scalar utilities including frame-rate independent exponential decay smoothing `exp(-speed * dt)`, linear interpolation, and degrees/radians conversions.
  - `CollisionUtils`: Geometric collision testing (Circle-AABB, Circle-Circle) with strict `< r^2` tangent exclusion and 2-pass sliding collision resolution.

### 2.4 `com.spellstorm.camera`
- **Role**: Viewport management, coordinate mapping, and zoom control.
- **Key Classes**:
  - `GameCamera`: Dual-view camera supporting smooth exponential position following (`speed=14.0`), smooth zoom interpolation (`speed=10.0`), zoom boundary clamping `[0.75, 2.25]`, and visible frustum containment within `[0, 4096] x [0, 4096]`. Provides bijective coordinate unprojection.

### 2.5 `com.spellstorm.assets`
- **Role**: Resource lifecycle management and format decoding.
- **Key Classes**:
  - `AssetRegistry`: Registry of candidate asset paths and filesystem resolution.
  - `TextureManager`: Texture cache with Nearest-Neighbor filtering (`TextureFilter.Nearest`), sprite sheet frame slicing (64x32 character frames, 32x32 tiles), and null-safe texture queries.
  - `FontManager`: FreeType font generator rendering TrueType fonts at requested point sizes with cached fallback fonts.

### 2.6 `com.spellstorm.world`
- **Role**: Procedural world generation and environmental collision.
- **Key Classes**:
  - `TileType`: Terrain classifications (`GRASS`, `DIRT`, `STONE`, `WATER`, `SAND`) with walkability and water flags.
  - `TileMap`: 128x128 grid (4096x4096px) managing tile storage, out-of-bounds containment, obstacle collections, and +/-2 tile frustum expansion culling.
  - `WorldGenerator`: Procedural map generator creating the sinusoidal river, 5 widened bridges with 4-tile flared dirt landings, Ancient Ruins, Campsite, safe-zone (r=12 tiles), and 44px obstacle spacing.
  - `Obstacle`: World obstacles with position, collision circle radius, and anchor Y depth sorting.
  - `DecoType`: Visual decoration types (`TREE`, `ROCK`, `RUINS`, `BUSH`, `LOG`).

### 2.7 `com.spellstorm.navigation`
- **Role**: Milestone 8 artificial intelligence pathfinding.
- **Key Classes**:
  - `AStarPathfinder`: 8-directional A* with exact Octile heuristic, double-orthogonal corner-cutting prevention (`CornerCuttingRule.STRICT`), multi-radius entity clearance, and two-layer fallback routing.
  - `PathNode`: Priority queue node tracking path costs ($g$, $h$, $f = g + h$).
  - `AStarPathfinder.PathCache`: Repath throttling (0.35s-0.55s staggered), stuck detection (0.35s), arrival radius (22 units), and string-pulling line-of-sight shortcutting.

### 2.8 `com.spellstorm.entities`
- **Role**: Entity hierarchy, physics, and character controllers.
- **Key Classes**:
  - `Entity`: Base game object with position, velocity, collision radius, and exponential knockback decay `exp(-7*dt)`.
  - `Character`: Entity subclass with health, armor mitigation, speed multipliers, and status effects.
  - `Player`: Player character with stats (HP 250, armor 30, speed 230), keyboard movement, and upgrade perk attributes.
  - `ShootingMode`: Enumeration (`MANUAL`, `AUTO`) with debounced 'V' key toggling.
  - `AimingSubsystem`: Nearest-enemy targeting with lowest entity ID tie-breaker for auto-shot mode.
  - `Enemy`: Base enemy AI state machine.
  - `MeleeEnemy`, `RangedEnemy`, `TankEnemy`: Specialized enemy variants with distinct speeds, health pools, and attacks.
  - `BossEnemy`: Colossus Overlord boss (HP 1800, armor 45, radius 50, 3-way spread volley).
  - `Projectile`: Ballistic missiles with lifetime, faction, pierce count (max 3), 20% falloff per hit, and 25% floor.
  - `ExperiencePickup`: Dropped EXP gems with floating sinusoidal bob and magnet acceleration.
  - `WorldPowerUp`: Ground power-up collectibles with 25s lifetime and blinking alpha.

### 2.9 `com.spellstorm.combat`
- **Role**: Damage formulas, spell registries, and status effects.
- **Key Classes**:
  - `DamageMitigation`: Pure calculation `damage * 100 / (100 + max(0, armor))`.
  - `DamageInfo`: Damage payload carrying raw value and `ignoreArmor` flag.
  - `StatusEffect`, `BurnStatus`, `SlowStatus`: Temporal status effects applied to characters.
  - `Element`, `ElementSet`: Elemental bitmask system (`FIRE`, `ICE`, `LIGHTNING`, `EARTH`, `ARCANE`).
  - `SpellDefinition`, `SpellRegistry`: Spell crafting recipes resolving combinations into unique projectiles.
  - `HitEffect`: Polymorphic on-hit effects (burn, slow, knockback, area, chain).
  - `CombatSystem`: Spatial projectile collision detection and boundary culling (+15px margin).

### 2.10 `com.spellstorm.systems`
- **Role**: Match systems and progression lifecycles.
- **Key Classes**:
  - `EnemySpawner`: Wave scaling over 120s (interval 2.2s -> 0.55s, cap 20 -> 36, HP scaling +25% strictly excluding boss, boss spawn at 60s and 90s).
  - `ExperienceSystem`: Spawns and manages EXP gems; magnet attraction within `pickupRadius * 2.8f`.
  - `PlayerProgression`: Level curve `25 + (level-1)*20`, excess EXP carryover, pending level-up queue.
  - `UpgradeManager`: 7 upgrade perks with rank caps and Fisher-Yates 3-card non-duplicate generation.
  - `PowerUpManager`: Manages 3 temporary abilities (Overdrive, Piercing Surge, Elemental Burst), exact 5.0s active duration, modal pause freeze, same-type refresh, different-type replace.
  - `ActiveTemporaryAbility`: Tracks active ability timer and stat modifiers.

### 2.11 `com.spellstorm.ui`
- **Role**: Screen-space rendering and modal overlays.
- **Key Classes**:
  - `HudRenderer`: Fixed 1280x720 HUD rendering vitals, EXP bar, shooting mode badge, power-up countdown, and top-center boss bar (560x22).
  - `MinimapRenderer`: 150x150 widget at (1110, 545) with 4x4 tile subsampling and blips.
  - `LevelUpOverlay`: Modal card selection with 0.15s grace period, two-step confirmation, and 244px text wrapping.
  - `GameOverOverlay`: Modal game-over screen with 0.20s grace period, formatted "MM:SS" survival time, and restart flow.

---

## 3. Coordinate Systems & Camera Unprojection

The architecture employs two distinct, non-interfering coordinate spaces:

```
+-----------------------------------------------------------------------------+
| WORLD SPACE (4096 x 4096 px)                                                |
| Dynamic World Camera: Pos=(x, y), Zoom=[0.75, 2.25]                         |
|                                                                             |
|  (0, 4096) ----------------------------------------------- (4096, 4096)     |
|     |                                                             |         |
|     |    Visible Frustum clamped inside bounds                    |         |
|     |    +-------------------------+                              |         |
|     |    | Screen-to-World Aiming  |                              |         |
|     |    | theta = atan2(wy-py,    |                              |         |
|     |    |               wx-px)    |                              |         |
|     |    +-------------------------+                              |         |
|     |                                                             |         |
|   (0, 0) --------------------------------------------------- (4096, 0)      |
+-----------------------------------------------------------------------------+
                                      |
                      Bijective Unprojection Mapping
                                      |
+-----------------------------------------------------------------------------+
| SCREEN SPACE (1280 x 720 px)                                                |
| Fixed UI Camera: Identity Transform                                         |
|                                                                             |
|  (0, 720) ------------------------------------------------- (1280, 720)     |
|     |  [Top-Center Boss Bar: 560x22]          [Minimap: 150x150]  |         |
|     |                                         at (1110, 545)      |         |
|     |                                                             |         |
|     |                                                             |         |
|     |  [Vitals & EXP Bar]     [Mode Badge]    [Power-Up Bar]      |         |
|   (0, 0) --------------------------------------------------- (1280, 0)      |
+-----------------------------------------------------------------------------+
```

### 3.1 World Space (4096 x 4096)
- Centered at `(2048, 2048)`.
- All entity positions, collision detection, projectile physics, pathfinding, and tile grids operate strictly in world units.
- **World Camera Dynamics**:
  - Follows player position with exponential decay smoothing: $P_{cam} = P_{target} + (P_{cam} - P_{target}) \cdot e^{-14.0 \cdot dt}$.
  - Zoom interpolates towards target zoom: $Z_{cam} = Z_{target} + (Z_{cam} - Z_{target}) \cdot e^{-10.0 \cdot dt}$.
  - Zoom is hard-clamped to $[0.75, 2.25]$.
  - Camera center is clamped such that the visible frustum $[x_{min}, y_{min}, x_{max}, y_{max}]$ never falls outside $[0, 4096] \times [0, 4096]$.

### 3.2 Screen Space (Fixed 1280 x 720)
- The user interface is rendered using a dedicated orthographic camera configured to exactly $1280 \times 720$.
- UI elements (Minimap, HUD, Boss Health Bar, Modal Overlays) remain pixel-perfect and invariant regardless of world camera position, world zoom level, or window scaling.

### 3.3 Bijective Unprojection & Accurate Aiming
To ensure player aiming and spell casting are 100% accurate at all zoom levels:
$$\vec{W}_{mouse} = \text{GameCamera.screenToWorld}(S_x, S_y)$$
The casting angle is computed invariant to zoom:
$$\theta = \text{atan2}(W_y - P_{player.y}, W_x - P_{player.x})$$
`GameCameraTest` and `Tier5AdversarialMathCameraWorldNavTest` empirically confirm that round-trip projection $\text{worldToScreen}(\text{screenToWorld}(p)) \equiv p$ within $10^{-3}$ pixels across 1,000 random coordinates and 5 distinct zoom levels.

---

## 4. Visual Layer Ordering & Rendering Pipeline

To eliminate visual artifacts, z-fighting, and texture bleeding, rendering executes in a strict 9-layer pipeline across two camera passes:

```
========================= PASS 1: WORLD CAMERA (PROJECTION) =========================
[Layer 1] Terrain & Arena Floor:
          - TileMap tiles (rendered with +/-2 tile frustum margin [minTx-2, maxTx+2])
          - Arena floor runes and borders
          - Solid obstacles and natural decorations (depth-sorted by anchor Y ascending)

[Layer 2] Enemies:
          - Melee, Ranged, and Tank enemies
          - Drop shadows and directional sprites
          - Entity mini health bars

[Layer 3] Projectiles:
          - Player and enemy spell missiles
          - Rotated projectile sprites and element glow auras

[Layer 4] Experience Pickups:
          - Cyan, Emerald, and Gold EXP gems
          - Sinusoidal vertical bobbing (bobOffset = sin(t * 4.0) * 4.0)
          - Magnet attraction trail particles

[Layer 5] World Power-Up Pickups:
          - Ground temporary ability collectibles
          - Blinking alpha oscillation during final 4.0s (alpha = 0.5 + 0.5 * sin(rem * 15.0))

[Layer 6] Player Character:
          - Player drop shadow
          - Directional character sprite (facing mouse cursor / target)
          - Orbiting magic orb and spell cast particles

========================= PASS 2: UI CAMERA (FIXED 1280x720) =========================
[Layer 7] Minimap Widget:
          - Background panel at (1110, 545), size 150x150
          - 4x4 tile subsampled terrain radar
          - Player position marker and enemy blips

[Layer 8] Fixed Screen-Space HUD:
          - Player Health Bar & Armor readout
          - Experience progress bar and Level indicator
          - Active Shooting Mode badge (MANUAL / AUTO)
          - Active Temporary Power-Up countdown bar and icon
          - Top-Center Colossus Boss Health Bar (560x22 px, with "ANCIENT GOLEM OVERLORD" title)

[Layer 9] Modal Overlays:
          - Level-Up Overlay: Dark translucent backdrop, 3 perk cards with 244px text wrapping,
            two-step selection (card click selects, confirm button/Enter commits)
          - Game-Over Overlay: Dark red vignette, "MM:SS" survival time, Restart / Quit buttons
```

---

## 5. Subsystem Interfaces and Inter-Package Contracts

```
+----------------------------------------------------------------------------------+
| math ↔ world / entities / camera                                                 |
| - Vector2D: Immutable/fluid 2D float vector arithmetic and trigonometry          |
| - CollisionUtils: checkCircleVsAABB, checkCircleVsCircle, resolveSliding         |
+----------------------------------------------------------------------------------+
                                        |
+----------------------------------------------------------------------------------+
| world ↔ navigation / entities                                                    |
| - TileMap: isTraversable(x, y, r), getTile(x, y), getVisibleTileRange(camera)   |
| - AStarPathfinder: findPath(startX, startY, targetX, targetY, entityRadius)      |
+----------------------------------------------------------------------------------+
                                        |
+----------------------------------------------------------------------------------+
| combat ↔ entities / systems                                                      |
| - DamageMitigation: calculateDamage(rawDamage, targetArmor)                      |
| - StatusEffect: BurnStatus (ticks every 0.5s), SlowStatus (clamps [0.0, 0.9])    |
| - SpellRegistry: resolve(ElementSet) -> SpellDefinition                          |
| - CombatSystem: updateProjectiles, checkCollisions, cullOutOfBounds(+15px)       |
+----------------------------------------------------------------------------------+
                                        |
+----------------------------------------------------------------------------------+
| systems ↔ progression / ui                                                       |
| - PlayerProgression: addExp(amount), getLevel(), getPendingLevelUps()            |
| - PowerUpManager: collectPowerUp(type), update(dt, paused), getActiveAbility()   |
| - EnemySpawner: update(dt, player, tileMap), reset()                             |
+----------------------------------------------------------------------------------+
```

---

## 6. Performance Optimizations & Zero-Allocation Patterns

To maintain a consistent 60 FPS and eliminate JVM garbage collection pauses during intensive combat, the following performance design patterns are implemented:

1. **Pre-allocated Zero-Allocation A\* Buffers**:
   `AStarPathfinder` avoids heap churn by pre-allocating a flat `CellState[128 * 128]` array. A 32-bit `runId` timestamp invalidates previous search states in $O(1)$ time without traversing or re-allocating grid nodes.
2. **Nearest-Neighbor Texture Filtering**:
   All 36 game textures are configured with `TextureFilter.Nearest`, guaranteeing crisp retro pixel art rendering and eliminating bilinear filtering blur.
3. **Frustum Tile Culling Expansion**:
   `TileMap.getVisibleTileRange()` computes the visible tile bounds with a `+/-2` tile safety margin:
   $$\text{minTx} = \text{clamp}(\lfloor x_{min} / 32 \rfloor - 2, 0, 127)$$
   $$\text{maxTx} = \text{clamp}(\lceil x_{max} / 32 \rceil + 2, 0, 127)$$
   This guarantees that no black unrendered seams or flashing occur during high-speed camera pans or zoom changes.
4. **Dynamic FreeType Font Caching**:
   `FontManager` rasterizes TrueType fonts on-demand per requested point size and caches the resulting `BitmapFont` instances, avoiding redundant TTF re-rasterization every frame.
