# Spell Storm: C++17 SFML 2.6 to Java 21 LibGDX Migration Mapping

## 1. Executive Summary

This document provides a comprehensive, bidirectional migration reference for the complete porting of the **Spell Storm** arcade spell-casting survival game from **C++17 + SFML 2.6** to a standalone **Java 21 + LibGDX 1.13.1** desktop application.

The migration successfully achieves 100% feature, gameplay, visual, and mathematical parity across all systems developed in Milestones 1 through 8. The target Java codebase is completely decoupled from C++ runtime binaries, shared libraries, and SFML dependencies, operating as an independent desktop application packaged and tested via Gradle.

---

## 2. Toolchain, Build System & Infrastructure Mapping

| Component | Reference Implementation (C++17) | Target Implementation (Java 21) | Migration Rationale & Notes |
| :--- | :--- | :--- | :--- |
| **Language & Runtime** | C++17 (GCC 11+ / Clang 13+) | Java 21 (OpenJDK LTS) | Modern LTS Java provides pattern matching, records, enhanced float math, and robust garbage collection. |
| **Build Automation** | CMake 3.20+ (`CMakeLists.txt`) | Gradle 8.10.2 (`build.gradle`, `gradlew`) | Standard JVM ecosystem build tool; Gradle wrapper guarantees reproducible cross-platform builds. |
| **Multimedia & Engine** | SFML 2.6.1 (`Graphics`, `Window`, `System`, `Audio`) | LibGDX 1.13.1 (`gdx-backend-lwjgl3`, `gdx-freetype`) | Industry-standard 2D Java game framework backed by LWJGL3 / OpenGL for high-performance desktop rendering. |
| **Font Rasterization** | `sf::Font` (FreeType wrapper) | `com.badlogicgames.gdx:gdx-freetype:1.13.1` | Native FreeType rasterization compiling TTF glyphs into dynamic LibGDX `BitmapFont` caches. |
| **Packaging & Distribution**| Native executable binary `build/SpellStorm` | Standalone Fat JAR (`SpellStorm-1.0.0-all.jar`) via Gradle | Single runnable JAR embedding all runtime dependencies and game assets; launches with `java -jar`. |
| **Test Framework** | Custom C++ test runner (`tests/test_main.cpp`) | JUnit 5 Platform BOM 5.10.2 (`junit-jupiter`) | Standard enterprise Java testing suite supporting nested tiers, parameterization, and tag filtering. |
| **Headless Testing** | Custom windowless simulation in C++ | `gdx-backend-headless` + Dynamic Mock `GL20` | Full headless LibGDX context executing 100% of game simulation, physics, and asset parsing without X11/OpenGL display. |

---

## 3. Subsystem & File-by-File Mapping

### 3.1 Core Architecture & Application Lifecycle

| C++ Header (`include/`) | C++ Source (`src/`) | Java Target Class | Java Package | Parity Notes |
| :--- | :--- | :--- | :--- | :--- |
| *None* (`main.cpp`) | `src/main.cpp` | `DesktopLauncher.java` | `com.spellstorm.desktop` | Desktop entry point configuring LWJGL3, 1280x720 window, 60 FPS cap, and headless asset verification CLI (`--headless`). |
| `core/GameOptions.hpp` | `src/main.cpp` | `EngineConfig.java` | `com.spellstorm.core` | Window configuration, debug flags (`--test-level-up`, `--test-game-over`, etc.), and display modes. |
| `core/State.hpp`, `core/StateStack.hpp` | `src/core/StateStack.cpp` | `GameState.java`, `SpellStormGame.java` | `com.spellstorm.core` | State enum (`START`, `PLAYING`, `PAUSED`, `LEVEL_UP`, `GAME_OVER`) and LibGDX `Game` delegation. |
| `core/GameSession.hpp` | `src/core/GameSession.cpp` | `GameSession.java` | `com.spellstorm.core` | Master game loop coordinator: initializes subsystems, handles inputs, orchestrates match ticks, modal pause, and `restartMatch()`. |
| `core/Arena.hpp` | `src/core/Arena.cpp` | Integrated into `TileMap.java` / `GameSession.java` | `com.spellstorm.world`, `com.spellstorm.core` | 4096 x 4096 world boundary definition with boundary clamping and margin calculations. |
| `core/ResourceManager.hpp` | `src/core/ResourceManager.cpp` | `AssetRegistry.java`, `TextureManager.java`, `FontManager.java` | `com.spellstorm.assets` | Asset loading pipeline; nearest-neighbor texture filtering; dynamic font size generation. |

### 3.2 Math, Physics & Collision Engine

| C++ Header (`include/`) | C++ Source (`src/`) | Java Target Class | Java Package | Parity Notes |
| :--- | :--- | :--- | :--- | :--- |
| `sf::Vector2f` / `core/MathUtils.hpp` | `src/core/MathUtils.cpp` | `Vector2D.java` | `com.spellstorm.math` | 2D float vector with add, sub, scl, dot, len, nor, angles, sub-epsilon division protection, and zero normalization. |
| `core/MathUtils.hpp` | `src/core/MathUtils.cpp` | `MathUtils.java` | `com.spellstorm.math` | Exponential decay smoothing `exp(-speed * dt)`, clamping, linear interpolation, degrees/radians conversions. |
| `core/Random.hpp` | `src/core/Random.cpp` | `MathUtils.java` / `java.util.Random` | `com.spellstorm.math` | Deterministic pseudo-random number generation supporting multi-seed procedural reproducibility. |
| `environment/TileMap.hpp` (collision routines) | `src/environment/TileMap.cpp` | `CollisionUtils.java` | `com.spellstorm.math` | Continuous Circle-AABB, Circle-Circle, strict `< r^2` tangent exclusion, and 2-pass sliding collision resolution. |

### 3.3 Camera Subsystem & Coordinate Transformations

| C++ Header (`include/`) | C++ Source (`src/`) | Java Target Class | Java Package | Parity Notes |
| :--- | :--- | :--- | :--- | :--- |
| `core/Camera.hpp` | `src/core/Camera.cpp` | `GameCamera.java` | `com.spellstorm.camera` | Dual-view camera: world camera with exponential follow (`speed=14`), zoom clamping `[0.75, 2.25]`, default `1.35`, and frustum clamping within `[0, 4096] x [0, 4096]`. |
| `core/Camera.hpp` (unprojection) | `src/core/Camera.cpp` | `GameCamera.java` (`screenToWorld`, `worldToScreen`) | `com.spellstorm.camera` | Bijective coordinate transformation invariant across all zoom levels and viewport aspect ratios. |

### 3.4 World Generation & Environment

| C++ Header (`include/`) | C++ Source (`src/`) | Java Target Class | Java Package | Parity Notes |
| :--- | :--- | :--- | :--- | :--- |
| `environment/TileMap.hpp` | `src/environment/TileMap.cpp` | `TileMap.java` | `com.spellstorm.world` | 128x128 grid (4096x4096px), 32px tiles, out-of-bounds water containment, +/-2 tile frustum expansion culling (`[minTx-2, maxTx+2]`). |
| `environment/TileMap.hpp` (`TileType`) | `src/environment/TileMap.cpp` | `TileType.java`, `DecoType.java` | `com.spellstorm.world` | Tile types (`GRASS`, `DIRT`, `STONE`, `WATER`, `SAND`) and decoration classifications (`TREE`, `ROCK`, `RUINS`, `BUSH`, `LOG`, etc.). |
| `environment/TileMap.hpp` (`Obstacle`) | `src/environment/TileMap.cpp` | `Obstacle.java` | `com.spellstorm.world` | Obstacle positioning, collision circle radius, depth sorting by anchor Y ascending. |
| `environment/TileMap.hpp` (world generation) | `src/environment/TileMap.cpp` | `WorldGenerator.java` | `com.spellstorm.world` | Procedural generation: sinusoidal river around x=42, 5 widened 5-tile bridges with flared dirt approach landings, Ancient Ruins, Campsite, safe-zone (r=12 tiles), 44px obstacle spacing. |

### 3.5 Milestone 8 Navigation & Pathfinding

| C++ Header (`include/`) | C++ Source (`src/`) | Java Target Class | Java Package | Parity Notes |
| :--- | :--- | :--- | :--- | :--- |
| `systems/PathfindingSystem.hpp` | `src/systems/PathfindingSystem.cpp` | `AStarPathfinder.java` | `com.spellstorm.navigation` | 8-directional A* with exact Octile heuristic; pre-allocated zero-allocation `CellState[]` grid with 32-bit runId. |
| `systems/PathfindingSystem.hpp` (corner cutting) | `src/systems/PathfindingSystem.cpp` | `AStarPathfinder.java` (`CornerCuttingRule.STRICT`) | `com.spellstorm.navigation` | Double-orthogonal corner-cutting prevention: requires both cardinal neighbors to be walkable before allowing diagonal traversal. |
| `systems/PathfindingSystem.hpp` (radius checks) | `src/systems/PathfindingSystem.cpp` | `AStarPathfinder.java` | `com.spellstorm.navigation` | Collision radius awareness supporting Melee (16), Ranged (15), Tank (26), and Boss (50) across bridges. |
| `systems/PathfindingSystem.hpp` (fallbacks) | `src/systems/PathfindingSystem.cpp` | `AStarPathfinder.java` | `com.spellstorm.navigation` | Two-layer fallback: Layer 1 (5x5 kernel search for blocked targets), Layer 2 (shoreline vantage point for unreachable deep water). |
| `systems/PathfindingSystem.hpp` (caching) | `src/systems/PathfindingSystem.cpp` | `AStarPathfinder.PathCache` | `com.spellstorm.navigation` | Direct Line-of-Sight charging (<650 units), string-pulling waypoint advancement, stuck detection (0.35s), staggered repath cadence (`0.35s + (id % 5)*0.05s`). |

### 3.6 Entity Hierarchy & Physics

| C++ Header (`include/`) | C++ Source (`src/`) | Java Target Class | Java Package | Parity Notes |
| :--- | :--- | :--- | :--- | :--- |
| `entities/Entity.hpp` | `src/entities/Entity.cpp` | `Entity.java` | `com.spellstorm.entities` | Base entity with position, velocity, knockback impulse with exponential decay `exp(-7*dt)`, collision radius. |
| `entities/Character.hpp` | `src/entities/Character.cpp` | `Character.java` | `com.spellstorm.entities` | Health management, armor mitigation, speed multipliers, status effect application, and facing direction. |
| `entities/Player.hpp` | `src/entities/Player.cpp` | `Player.java` | `com.spellstorm.entities` | Player character (HP 250, armor 30, speed 230, cast cd 0.28s, pickup radius 45px), keyboard movement, upgrade perk stats. |
| `entities/Player.hpp` (shooting mode) | `src/entities/Player.cpp` | `ShootingMode.java`, `AimingSubsystem.java` | `com.spellstorm.entities` | Manual mode (LMB fire) vs Auto-shot mode (continuous nearest-enemy targeting), debounced 'V' key toggle (0.25s debounce). |
| `entities/Enemy.hpp` | `src/entities/Enemy.cpp` | `Enemy.java`, `EnemyType.java` | `com.spellstorm.entities` | Base enemy with AI state machine, wander, chase, attack cadence, and path cache. |
| `entities/MeleeEnemy.hpp` | `src/entities/MeleeEnemy.cpp` | `MeleeEnemy.java` | `com.spellstorm.entities` | Melee enemy: HP 60, speed 105, contact damage 18, radius 16, exp reward 10. |
| `entities/RangedEnemy.hpp` | `src/entities/RangedEnemy.cpp` | `RangedEnemy.java` | `com.spellstorm.entities` | Ranged enemy: HP 45, speed 120, kites at 240px, shoots bolt (dmg 12, spd 180), radius 15, exp reward 15. |
| `entities/TankEnemy.hpp` | `src/entities/TankEnemy.cpp` | `TankEnemy.java` | `com.spellstorm.entities` | Tank enemy: HP 200, armor 25, speed 65, contact damage 32, knockback resistance 0.5, radius 26, exp reward 30. |
| `entities/BossEnemy.hpp` | `src/entities/BossEnemy.cpp` | `BossEnemy.java` | `com.spellstorm.entities` | Colossus Overlord boss: HP 1800, armor 45, speed 55, contact damage 45, radius 50, exp reward 250, 3-way spread volley attack. |
| `entities/Projectile.hpp` | `src/entities/Projectile.cpp` | `Projectile.java`, `ProjectileFaction.java` | `com.spellstorm.entities` | Ballistic projectile with velocity, lifetime, faction, pierce count (max 3), 20% falloff per hit, 25% damage floor, multi-hit entity tracking. |
| `entities/ExperiencePickup.hpp` | `src/entities/ExperiencePickup.cpp` | `ExperiencePickup.java` | `com.spellstorm.entities` | Dropped EXP gems (Cyan 10, Emerald 25, Gold 75), floating sinusoidal bob, magnet pull acceleration. |

### 3.7 Combat System, Spells & Status Effects

| C++ Header (`include/`) | C++ Source (`src/`) | Java Target Class | Java Package | Parity Notes |
| :--- | :--- | :--- | :--- | :--- |
| `combat/Damage.hpp` | `src/combat/HealthComponent.cpp` | `DamageMitigation.java`, `DamageInfo.java` | `com.spellstorm.combat` | Formula `damage * 100 / (100 + max(0, armor))`, negative raw damage returns 0, negative armor clamped to 0. |
| `combat/StatusEffect.hpp` | `src/combat/StatusEffect.cpp` | `StatusEffect.java`, `BurnStatus.java`, `SlowStatus.java` | `com.spellstorm.combat` | Burn (ticks every 0.5s, true damage ignoring armor, 0 damage at frame 0), Slow (reduces speed, clamped `[0.0, 0.9]`). |
| `spells/Element.hpp` | `src/spells/Element.cpp` | `Element.java`, `ElementSet.java` | `com.spellstorm.combat` | 5 base elements (`FIRE`, `ICE`, `LIGHTNING`, `EARTH`, `ARCANE`) bitmask combinations. |
| `spells/SpellDefinition.hpp` | `src/spells/SpellDefinition.cpp` | `SpellDefinition.java`, `ProjectileProfile.java` | `com.spellstorm.combat` | Spell metadata: damage, speed, radius, lifetime, pierce count, effects list. |
| `spells/SpellRegistry.hpp` | `src/spells/SpellRegistry.cpp` | `SpellRegistry.java` | `com.spellstorm.combat` | Element combinations resolving into advanced spells (Fireball, Frost Nova, Lightning Bolt, Firestorm, Frost Tempest, Magma Core). |
| `spells/HitEffect.hpp` | `src/spells/HitEffect.cpp` | `HitEffect.java`, `BurnEffect.java`, `SlowEffect.java`, `KnockbackEffect.java`, `AreaEffect.java`, `ChainEffect.java` | `com.spellstorm.combat` | Polymorphic on-hit effects executed upon projectile impact. |
| `systems/CombatSystem.hpp` | `src/systems/CombatSystem.cpp` | `CombatSystem.java` | `com.spellstorm.combat` | Projectile lifecycle, spatial broadphase, projectile-entity collision, arena boundary culling (+15px margin). |

### 3.8 Systems, Progression & Power-Ups

| C++ Header (`include/`) | C++ Source (`src/`) | Java Target Class | Java Package | Parity Notes |
| :--- | :--- | :--- | :--- | :--- |
| `systems/EnemySpawner.hpp` | `src/systems/EnemySpawner.cpp` | `EnemySpawner.java` | `com.spellstorm.systems` | 120s scaling timeline: interval 2.2s -> 0.55s, cap 20 -> 36, late-game HP scaling +25% (strictly excluding boss), boss spawn at 60s and every 90s. |
| `systems/ExperienceSystem.hpp` | `src/systems/ExperienceSystem.cpp` | `ExperienceSystem.java` | `com.spellstorm.systems` | Gem drops upon enemy death, magnet attraction within `pickupRadius * 2.8f`, gem collection. |
| `systems/PlayerProgression.hpp` | `src/systems/PlayerProgression.cpp` | `PlayerProgression.java` | `com.spellstorm.systems` | Level curve `25 + (level-1)*20`, excess EXP carryover across multi-level jumps, pending level-up queue. |
| `systems/UpgradeSystem.hpp` | `src/systems/UpgradeSystem.cpp` | `UpgradeManager.java`, `UpgradeType.java`, `UpgradeInfo.java` | `com.spellstorm.systems` | 7 perks with max rank caps, Fisher-Yates non-duplicate 3-card generation. |
| `systems/PowerUpManager.hpp` | `src/systems/PowerUpManager.cpp` | `PowerUpManager.java`, `PowerUpType.java`, `ActiveTemporaryAbility.java`, `WorldPowerUp.java` | `com.spellstorm.systems` | 3 temporary power-ups (Arcane Overdrive, Piercing Surge, Elemental Burst), 25s ground lifetime (blinking alpha at <4s), exact 5.0s active duration, modal pause freeze, same-type refresh, different-type replace. |

### 3.9 User Interface & Modal Overlays

| C++ Header (`include/`) | C++ Source (`src/`) | Java Target Class | Java Package | Parity Notes |
| :--- | :--- | :--- | :--- | :--- |
| `core/DrawUtils.hpp` (HUD) | `src/core/DrawUtils.cpp` | `HudRenderer.java` | `com.spellstorm.ui` | Screen-space fixed 1280x720 HUD: vitals bars, EXP progress bar, shooting mode badge, power-up countdown bar, top-center boss health bar (560x22). |
| `core/DrawUtils.hpp` (Minimap) | `src/core/DrawUtils.cpp` | `MinimapRenderer.java` | `com.spellstorm.ui` | 150x150 minimap at (1110, 545), 4x4 tile subsampling, player marker and enemy blips. |
| `ui/LevelUpOverlay.hpp` | `src/ui/LevelUpOverlay.cpp` | `LevelUpOverlay.java` | `com.spellstorm.ui` | Two-step selection (card click selects, confirm button/Enter commits), 0.15s open grace timer, description text wrapping bounded to <= 244px. |
| `ui/GameOverOverlay.hpp` | `src/ui/GameOverOverlay.cpp` | `GameOverOverlay.java` | `com.spellstorm.ui` | 0.20s grace period, formatted survival time "MM:SS", restart flow (R/Enter/Space), quit flow (Esc/Q). |

---

## 4. Test Suite Mapping

Every C++ reference test suite in the archived `legacy-cpp-archive` `tests/` directory was meticulously mapped and expanded into comprehensive JUnit 5 suites under `src/test/java/com/spellstorm/`:

| C++ Reference Test File | Primary Java Test Suites | Key Verifications Covered |
| :--- | :--- | :--- |
| `tests/test_simulation.cpp` | `MathUtilsTest.java`, `Vector2DTest.java` | Vector algebra, sub-epsilon division, angle conversions, exponential decay. |
| `tests/test_world_camera.cpp` | `GameCameraTest.java`, `TileMapTest.java`, `CollisionUtilsTest.java` | Frustum boundaries, zoom clamping `[0.75, 2.25]`, screen unprojection, sliding collision. |
| `tests/test_combat.cpp` | `CombatDamageTest.java`, `BossCombatTest.java`, `StatusEffectTest.java` | Damage mitigation formula, boss HP/armor/volley, burn true damage, slow clamping. |
| `tests/test_spells.cpp` | `SpellRegistryTest.java`, `ProjectileTest.java` | Spell combinations, pierce count (max 3), 20% damage falloff, 25% damage floor. |
| `tests/test_shooting_mode.cpp` | `ShootingModeTest.java` | Manual default, 'V' key debounce (0.25s), auto-shot targeting nearest enemy with lowest ID tie-breaker. |
| `tests/test_progression.cpp` | `ProgressionAndExpTest.java`, `UpgradePerksTest.java` | Level curve `25+(lvl-1)*20`, 3 gem tiers, 7 perk multipliers, 244px text wrapping. |
| `tests/test_systems.cpp` | `PowerUpLifecycleTest.java`, `EnemySpawnerTest.java`, `BossSpawnerTest.java` | 5.0s power-up lifecycle, pause freeze, same-type refresh, different-type replace, 120s spawner timeline. |
| `tests/test_game_over.cpp` | `GameOverAndRestartTest.java`, `StateAndLifecycleTest.java` | Player death, single Game Over transition, simulation freeze, formatted "MM:SS", idempotent restart. |
| `tests/test_assets.cpp` | `AssetLoadingTest.java`, `AssetPipelineAdversarialTest.java` | 41/41 asset verification, Nearest-neighbor filtering, sprite sheet slicing, FreeType fonts. |
| *New Java Architecture Tests* | `EngineConfigAndBuildTest.java`, `HeadlessInfrastructureTest.java` | CLI arg parsing, window options, headless test runner idempotency, path isolation guards. |
| *New Integration Suites* | `Tier3CrossFeaturePairwiseTest.java` (34 tests) | Pairwise subsystem interactions (Camera x Unprojection, River x Collision, Modal x Pause, etc.). |
| *New Real-World Suites* | `Tier4RealWorldScenariosTest.java` (5 tests) | 120s full match timeline, boss battle, bridge gauntlet, power-up juggling, death & restart. |
| *New Adversarial Hardening*| `Tier5AdversarialMathCameraWorldNavTest.java` (41 tests), `Tier5EntitiesCombatSystemsChallengeTest.java` (48 tests) | Boundary singularities, float underflow/overflow, division-by-zero, stress testing across all systems. |

---

## 5. Architectural Divergences and Parity Solutions

### 5.1 Coordinate System & Rendering Inversion
- **C++ SFML**: Uses an inverted Y-axis (Y=0 at top of window, increasing downwards).
- **LibGDX**: Uses standard Cartesian Y-up (Y=0 at bottom of window, increasing upwards) for world space.
- **Parity Solution**: `GameCamera` handles the unprojection seamlessly. For screen-space overlays (HUD, Minimap, Overlays), a fixed 1280x720 orthographic camera is configured with standard screen-space orientation, and `MinimapAndHudTest` verifies that world coordinates `(0, 0)` to `(4096, 4096)` map faithfully to screen coordinates without vertical flipping artifacts.

### 5.2 Memory Management & Zero-Allocation Pathfinding
- **C++ SFML**: Utilized stack allocation, RAII, `std::vector`, and raw pointer arrays.
- **Java LibGDX**: Frequent per-frame allocations during A* pathfinding would trigger garbage collection pauses (GC jitter).
- **Parity Solution**: `AStarPathfinder.java` pre-allocates a single flat `CellState[128 * 128]` array indexed by `y * 128 + x`, utilizing a 32-bit `runId` timestamp to clear visited states in $O(1)$ time without re-allocating memory during path searches.

### 5.3 Headless Testing Without Display Server
- **C++ SFML**: Allowed constructing objects without opening an `sf::RenderWindow`.
- **Java LibGDX**: By default requires an active OpenGL context for `Texture`, `SpriteBatch`, and `Pixmap`.
- **Parity Solution**: `HeadlessTestRunner.java` initializes LibGDX's `HeadlessApplication` and injects a dynamic Java proxy Mock `GL20` that returns valid texture/buffer handles and `GL_FRAMEBUFFER_COMPLETE`. Pure simulation logic is decoupled from rendering, enabling 749 tests to execute in under 3 seconds in headless CI environments.
