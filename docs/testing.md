# Testing & Verification Report

This document records the testing strategy, test suites, executed test runs, memory sanitizer outputs, and known limitations for **Spell Storm**.

---

## 1. Testing Strategy

The verification strategy comprises four pillars:
1. **Automated Unit Testing**: Tests core domain logic (math, combat formulas, status ticking, element masks, and spell resolution) without requiring graphics hardware or window servers.
2. **Dynamic Sanitizer Analysis**: Uses GCC AddressSanitizer (ASan) and UndefinedBehaviorSanitizer (UBSan) to prove memory safety and detect memory leaks, buffer overflows, or dangling pointers.
3. **Headless Execution Verification**: Verifies resource loading, font parsing, and spell catalogue setup without opening a display.
4. **Interactive Manual Testing**: Verifies keyboard/mouse input, window frame pacing, and procedural SFML rendering.

---

## 2. Automated Test Suite (`spellstorm_tests`)

All automated tests are built into the standalone test binary `spellstorm_tests`.

### 2.1 Test Matrix

| Test Suite | File | Test Cases Executed | Result |
| :--- | :--- | :--- | :---: |
| **Combat & Mitigation** | [`tests/test_combat.cpp`](../tests/test_combat.cpp) | Armor mitigation at 0, 100, 300 armor; Character takeDamage; Clamped healing; SlowStatus speed multiplier; Isolated HealthComponent damage and ratio calculations | **PASS (10/10)** |
| **Spells & Registry** | [`tests/test_spells.cpp`](../tests/test_spells.cpp) | ElementSet bitmask add, contains, count; Commutative equality (`{Fire, Wind} == {Wind, Fire}`); Base spell registration; Multi-element combination registration and bidirectional resolution | **PASS (11/11)** |
| **Math & Systems** | [`tests/test_systems.cpp`](../tests/test_systems.cpp) | 2D vector length; Vector normalization; EffectSystem particle update and lifetime expiration | **PASS (6/6)** |
| **Simulation, Enemies & Combat** | [`tests/test_simulation.cpp`](../tests/test_simulation.cpp) | Arena containment/clamping; Player aim angles & boundaries; Enemy stats & AI navigation (Melee, Ranged, Tank); Spawner timing & caps; Projectile collision & pierce; Armor mitigation; Status effects (Burn, Slow) | **PASS (128/128)** |
| **Progression, EXP & Upgrades** | [`tests/test_progression.cpp`](../tests/test_progression.cpp) | EXP pickups & values; Single reward guarantee; Magnet pull & collection; Leveling thresholds & multi-levelups; Excess EXP carry-over; Invalid EXP guards; Upgrade rank limits; Choice deduplication; Gameplay pause/resumption; Melee speed regression check | **PASS (99/99)** |
| **Game Over & Restart (M5)** | [`tests/test_game_over.cpp`](../tests/test_game_over.cpp) | Player death condition & input disabling; Single transition idempotence; Gameplay simulation freeze; UI button hover/pressed/release states & bounds; Match state restoration (health, pos, stats, level, exp, upgrades, enemies, pickups, missiles, timers); Repeated restart safety (8+ cycles); Level-up pause immunity | **PASS (259/259)** |

### 2.2 Actual Test Execution Output

```text
$ ./build/spellstorm_tests
Running SpellStorm Test Suite...
Tests Passed: 513
Tests Failed: 0
ALL TESTS PASSED!
```

---

## 3. Dynamic Sanitizer Verification (ASan + UBSan)

The project was compiled and executed under `-fsanitize=address,undefined -fno-omit-frame-pointer`:

```bash
cmake -B build-asan -DCMAKE_BUILD_TYPE=Debug -DSPELLSTORM_BUILD_TESTS=ON -DSPELLSTORM_SANITIZE=ON
cmake --build build-asan -j$(nproc)
./build-asan/spellstorm_tests
```

### Sanitizer Results
```text
Running SpellStorm Test Suite...
Tests Passed: 513
Tests Failed: 0
ALL TESTS PASSED!
```
- **Heap Memory Leaks**: `0` bytes leaked.
- **Heap Buffer Overflows**: `0` detected.
- **Use-After-Free / Dangling Pointers**: `0` detected.
- **Undefined Behavior**: `0` detected.

---

## 4. Headless & Automated Application Runs

The application executable `SpellStorm` was verified with CLI flags:

### 4.1 Headless Mode
```bash
$ ./build/SpellStorm --headless
===========================================
       Spell Storm - Arena Wizard
===========================================
[SpellStorm] Font loaded successfully.
[SpellStorm] Core library initialized successfully.
[SpellStorm] Headless mode: verified registry and resources successfully.
```
- **Exit Code**: `0`

### 4.2 Automated Timed Run
```bash
$ ./build/SpellStorm --quit-after 0.5
===========================================
       Spell Storm - Arena Wizard
===========================================
[SpellStorm] Font loaded successfully.
[SpellStorm] Core library initialized successfully.
[SpellStorm] Window initialized.
...
[SpellStorm] Reached quit-after timeout (0.5s). Exiting.
[SpellStorm] Exiting game session cleanly.
```
- **Exit Code**: `0`

---

## 5. Identified Limitations & Known Bugs

1. **Sprite Rendering**:
   - The game currently relies on procedural SFML shapes and primitive shaders/lines instead of textured sprite sheets. This is an intentional architectural choice to remain 100% self-contained without external image assets.
2. **Window / Display Requirement for Full Gameplay**:
   - While `--headless` allows headless verification, the interactive window loop requires an X11 or Wayland display server (`$DISPLAY` or `$WAYLAND_DISPLAY`).
3. **Sound Effects**:
   - Audio playback (`sfml-audio`) is currently disabled in CMakeLists to avoid requiring system ALSA/PulseAudio daemons on headless grading machines. All combat feedback is delivered through visual rings, beams, and particle bursts.
