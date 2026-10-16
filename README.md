# Spell Storm - 2D Magic Arena (OOP Assignment)

A 2D offline arena action game built with **C++17**, **SFML 2.6**, and modern Object-Oriented Software Engineering principles. Designed and engineered for an Object-Oriented Programming (OOP) university capstone project.

---

## 1. Overview & Concept

In **Spell Storm**, the player controls a wizard in an arena combat zone, wielding elemental magic against hostile forces. The project showcases how to architect a modular, extensible, and memory-safe 2D game using clean C++17 design patterns, avoiding monolithic god-objects and spaghetti state machines.

### Key Features
- **2D Art Asset Pipeline & Sprite Rendering**: Robust resource manager (`ResourceManager`) loading textures and fonts with automatic candidate path resolution, sprite sheet frame slicing, decoupled collision geometry, and graceful fallback to procedural graphics.
- **Dual Shooting Modes**: Instant toggle between Manual Aiming (mouse-directed left-click casting) and Auto-Shot (automatic nearest-target tracking with lead prediction) via `V` key.
- **Elemental Magic System**: 5 core elements (Fire, Ice, Lightning, Wind, Earth) and dynamic dual-element combination recipes (e.g. *Firestorm*, *Frost Tempest*, *Magma Core*).
- **Extensible Spell Engine**: Spells are defined through composition using reusable strategy objects (`HitEffect`, `BurnEffect`, `SlowEffect`, `ChainEffect`, `AreaEffect`).
- **Experience & Progression System**: Enemies drop collectible EXP gems (cyan/emerald/gold based on value). Magnets pull gems toward the player, with excess EXP carrying over seamlessly.
- **Interactive Level-Up Upgrades**: Pauses gameplay simulation and displays 3 randomized, non-duplicate upgrade cards (Health, Speed, Spell Damage, Cooldown, Projectile Speed, Projectile Lifetime) with rank caps and hover/click feedback.
- **Composition over Inheritance**: Entity vital statistics and damage mitigation are encapsulated into [`HealthComponent`](include/combat/HealthComponent.hpp).
- **Graceful Procedural Fallback**: Zero external runtime crash risks; seamlessly falls back to procedural SFML primitive rendering if art assets are missing.
- **Decoupled Architecture**: Strict separation of presentation (SFML graphics) from core combat domain logic.
- **Robust Memory Safety**: Strict RAII, smart pointer ownership semantics (`std::unique_ptr`, `std::shared_ptr`), verified with AddressSanitizer and UndefinedBehaviorSanitizer.

---

## 2. Requirements & Dependencies

- **OS**: Linux (tested on Ubuntu 24.04 LTS x86_64), macOS, or Windows
- **Compiler**: C++17 compliant compiler (`g++ >= 9`, `clang++ >= 10`, or MSVC 2019+)
- **Build System**: CMake `>= 3.16` and GNU Make
- **Libraries**:
  - `SFML 2.6.x` (`sfml-graphics`, `sfml-window`, `sfml-system`)
  - TrueType Fonts (DejaVu font bundle bundled in `assets/fonts/`)

---

## 3. How to Build

### Standard Release Build
```bash
# Generate build configuration
cmake -B build -DCMAKE_BUILD_TYPE=Release -DSPELLSTORM_BUILD_TESTS=ON

# Compile using all CPU cores
cmake --build build -j$(nproc)
```

### Build with Sanitizers (Debug / Verification)
```bash
cmake -B build-asan -DCMAKE_BUILD_TYPE=Debug -DSPELLSTORM_BUILD_TESTS=ON -DSPELLSTORM_SANITIZE=ON
cmake --build build-asan -j$(nproc)
```

---

## 4. How to Run

### Interactive Game Application
```bash
./build/SpellStorm
```

### Command-Line Flags
- `--headless`: Verifies resource loading and spell registry without opening a window (ideal for CI/CD).
- `--autoplay`: Enables automated bot play (aiming and casting) for testing and verification.
- `--test-showcase`: Spawns all 3 enemy types (Melee, Ranged, Tank), 3 EXP gem tiers, and active spell casting for visual asset inspection.
- `--test-auto-shot`: Starts directly in Auto-Shot mode.
- `--test-level-up`: Opens the game directly in the Level-Up perk selection modal.
- `--test-game-over`: Opens the game directly in the Game Over screen with sample telemetry.
- `--test-restart-flow`: Runs an end-to-end automated sequence: gameplay -> death -> Game Over -> restart -> gameplay.
- `--screenshot <path>`: Captures a window screenshot upon exit.
- `--quit-after <seconds>`: Automatically closes the window after specified seconds.

```bash
# Example: Run asset showcase with screenshot capture
./build/SpellStorm --test-showcase --screenshot milestone6_showcase_all_assets.png --quit-after 0.15

# Example: Run headless verification
./build/SpellStorm --headless

# Example: Run automated test with level-up screen verification
./build/SpellStorm --test-level-up --quit-after 1.0

# Example: Capture Game Over screen
./build/SpellStorm --test-game-over --screenshot milestone5_gameover_screen.png --quit-after 1.0

# Example: Verify end-to-end restart flow
./build/SpellStorm --test-restart-flow --quit-after 2.5
```

### Running Unit Tests
```bash
./build/spellstorm_tests
```

---

## 5. Controls

| Key / Input | Action |
| :--- | :--- |
| **W / A / S / D** or **Arrow Keys** | Move Wizard |
| **Left Mouse Button** | Cast active spell towards cursor (Manual Mode) / Select card / Click Confirm / Click Restart & Quit |
| **V** | Toggle **Shooting Mode** (Manual Aim vs Auto-Shot) |
| **Key 1** | Infuse **Fire** element (Burn over time) |
| **Key 2** | Infuse **Ice** element (Slow target) |
| **Key 3** | Infuse **Lightning** element (Chain jump to foes) |
| **Key 4** | Infuse **Wind** element (High speed, piercing knockback) |
| **Key 5** | Infuse **Earth** element (Heavy impact, boulder crush) |
| **Combine Elements** | Press any two element keys in sequence (e.g. `1` then `4` activates *Firestorm*) |
| **In Level-Up Modal** | Click card (or press **1 / 2 / 3**) to select; Click **CONFIRM UPGRADE** (or press **Enter / Space**) to apply |
| **In Game Over Screen** | Click **RESTART** (or press **R / Enter / Space**) to restart match; Click **QUIT** (or press **Escape / Q**) to exit |
| **Space** | In game: Test damage on player (mitigation demo) / In modal: Confirm selection |
| **Enter** | In modal: Confirm selected upgrade or restart |
| **H** | Heal player (showcases health clamping and recovery) |
| **L** | Grant +30 EXP to test leveling up and the upgrade modal interactively |
| **Escape** | Exit game session / Quit in Game Over |

---

## 6. Project Layout

```text
SpellStorm/
├── CMakeLists.txt              # CMake build script
├── README.md                   # Project overview & guide
├── .gitignore                  # Git untracked filter
├── assets/                     # 2D Art Assets & Fonts
│   ├── fonts/                  # TrueType fonts (DejaVuSans, DejaVuSans-Bold, DejaVuSerif-Bold)
│   └── textures/
│       ├── characters/         # wizard.png (Player 2-frame sprite sheet)
│       ├── enemies/            # melee_fiend.png, ranged_cultist.png, tank_golem.png
│       ├── projectiles/        # spell_fire, spell_ice, spell_lightning, spell_wind, spell_earth, projectile_enemy
│       ├── pickups/            # gem_cyan, gem_emerald, gem_gold
│       └── environment/        # floor_tile.png
├── include/                    # C++ Header files
│   ├── combat/                 # Combat, damage, health, status effects
│   ├── core/                   # Math, draw utils, random, ResourceManager, Arena, GameSession
│   ├── entities/               # Entity, Character, Player, Enemy (Melee, Ranged, Tank), Projectile, ExperiencePickup
│   ├── spells/                 # Elements, HitEffects, SpellDefinition, SpellRegistry
│   ├── systems/                # CombatSystem, EnemySpawner, ExperienceSystem, PlayerProgression, UpgradeSystem
│   └── ui/                     # LevelUpOverlay, GameOverOverlay
├── src/                        # C++ Implementation files
│   ├── combat/
│   ├── core/
│   ├── entities/
│   ├── main.cpp                # Application entry point
│   ├── spells/
│   ├── systems/
│   └── ui/
├── tests/                      # Automated unit test suite (683 test assertions)
│   ├── TestHelper.hpp
│   ├── test_assets.cpp         # Asset pipeline, candidate paths, caching, fallbacks
│   ├── test_combat.cpp
│   ├── test_game_over.cpp
│   ├── test_main.cpp
│   ├── test_progression.cpp
│   ├── test_shooting_mode.cpp
│   ├── test_simulation.cpp
│   ├── test_spells.cpp
│   └── test_systems.cpp
├── tools/
│   └── generate_assets.py      # Authoring generator for starter pixel art assets
└── docs/                       # Assignment documentation
    ├── asset_specifications.md # Detailed 2D art specifications & dimensions
    ├── design.md               # Architectural design & Mermaid diagrams
    ├── oop_analysis.md         # OOP principles analysis with code mapping
    ├── testing.md              # Test execution & verification reports
    └── presentation.md         # Defense plan (8-12 min) & instructor Q&A
```

---

## 7. Assignment Documentation Quick Links

- [docs/asset_specifications.md](docs/asset_specifications.md): 2D art asset specs, dimensions, palettes, and pipeline model.
- [docs/design.md](docs/design.md): UML diagrams, component interactions, and state flow.
- [docs/oop_analysis.md](docs/oop_analysis.md): Concrete demonstrations of Encapsulation, Abstraction, Inheritance, Polymorphism, and Composition.
- [docs/testing.md](docs/testing.md): Automated and sanitization test reports.
- [docs/presentation.md](docs/presentation.md): Presentation agenda and defense Q&A.
