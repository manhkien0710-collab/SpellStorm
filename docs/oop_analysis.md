# Object-Oriented Programming (OOP) Analysis & Design Principles

This document provides a comprehensive academic analysis of the Object-Oriented Programming principles, design patterns, and C++17 best practices implemented in **Spell Storm**. Every principle is cross-referenced with exact files, classes, and methods.

---

## 1. Encapsulation (Đóng gói)

Encapsulation ensures that an object's internal representation is hidden from the outside, exposing only a validated, invariant-preserving interface.

### Concrete Implementation: `HealthComponent`
- **File**: [`include/combat/HealthComponent.hpp`](../include/combat/HealthComponent.hpp), [`src/combat/HealthComponent.cpp`](../src/combat/HealthComponent.cpp)
- **Private State**:
  ```cpp
  private:
      float m_current;
      float m_max;
      float m_armor;
  ```
- **Invariant Protection**:
  1. `m_current` can never exceed `m_max` or fall below `0.f`.
  2. `m_armor` is strictly clamped non-negative (`std::max(0.f, armor)`).
  3. External callers cannot modify `m_current` directly. They must invoke `takeDamage(const DamageInfo&)` or `heal(float)`.
  4. The formula for diminishing returns on armor mitigation is hidden entirely inside `takeDamage`:
     ```cpp
     const float dealt = damage.ignoreArmor ? damage.amount : mitigateDamage(damage.amount, m_armor);
     m_current = std::max(0.f, m_current - dealt);
     ```

### Concrete Implementation: `ElementSet`
- **File**: [`include/spells/Element.hpp`](../include/spells/Element.hpp)
- Encapsulates raw bitwise manipulation of an integer mask (`uint32_t m_mask`). External code calls high-level semantics: `set.add(Element::Fire)`, `set.contains(Element::Wind)`, and `set.count()`.

---

## 2. Abstraction (Trừu tượng hóa)

Abstraction hides complex background mechanics and exposes intuitive, focused interfaces.

### Concrete Implementation: `CombatServices` Interface
- **File**: [`include/combat/CombatServices.hpp`](../include/combat/CombatServices.hpp)
- The spell effects (`HitEffect`) need to find nearby targets and inflict damage without knowing how the game engine stores entities (vectors, spatial hashes, quadtrees) or how the window renders.
- `CombatServices` abstracts this through pure virtual functions:
  ```cpp
  class CombatServices {
  public:
      virtual ~CombatServices() = default;
      virtual void damageEnemy(Enemy& enemy, const DamageInfo& damage, sf::Color color) = 0;
      virtual Enemy* findNearestEnemy(sf::Vector2f from, float range, const std::vector<EntityId>& exclude) = 0;
      virtual void forEachEnemyInRadius(sf::Vector2f center, float radius, const std::function<void(Enemy&)>& action) = 0;
      virtual EffectSystem& effects() = 0;
  };
  ```

### Concrete Implementation: `State` Base Class
- **File**: [`include/core/State.hpp`](../include/core/State.hpp)
- The `StateStack` manipulates any screen (Menu, Playing, Pause) through three abstract functions: `handleEvent(event)`, `update(dt)`, and `render(target)`.

---

## 3. Inheritance (Kế thừa)

Inheritance is applied exclusively for genuine **"is-a"** relationships where the derived type models an ontological specialization of the base type.

### Concrete Hierarchy: `Entity` $\rightarrow$ `Character` $\rightarrow$ `Enemy`
- **Files**:
  - [`include/entities/Entity.hpp`](../include/entities/Entity.hpp)
  - [`include/entities/Character.hpp`](../include/entities/Character.hpp)
  - [`include/entities/Enemy.hpp`](../include/entities/Enemy.hpp)
- **Hierarchy Rationale**:
  - `Entity`: Base for any physical arena presence with position, radius, unique ID, and alive flag.
  - `Character`: An `Entity` that possesses physical movement velocity, knockback impulses, vital statistics, and status conditions.
  - `Enemy`: A `Character` that possesses an AI profile, hostile targeting behavior, and EXP drop rewards.
  - `PlayerWizard`: A `Character` that possesses player keyboard/mouse input handlers.

```text
Entity (Abstract Base)
  └── Character (Mobile Living Entity)
        ├── PlayerWizard (Controllable Avatar)
        └── Enemy (Abstract AI Hostile)
```

---

## 4. Polymorphism (Đa hình)

Polymorphism allows algorithms to operate on base class pointers or references while dynamically dispatching to the specialized behavior of concrete types at runtime.

### Dynamic Polymorphism (Virtual Functions & Vtables)

#### 1. Status Effects (`StatusEffect`)
- **File**: [`include/combat/StatusEffect.hpp`](../include/combat/StatusEffect.hpp)
- `StatusEffect::update(dt, owner)` invokes virtual `onTick(dt, owner)`.
- `BurnStatus` overrides `onTick` to periodically apply damage-over-time.
- `SlowStatus` overrides `speedMultiplier()` to return `(1.f - m_slowFraction)`.
- The character's `StatusEffectList` iterates over `std::vector<std::unique_ptr<StatusEffect>>` uniformly without checking concrete types via `dynamic_cast`.

#### 2. Composable Hit Effects (`HitEffect`)
- **File**: [`include/spells/HitEffect.hpp`](../include/spells/HitEffect.hpp)
- Declares `virtual void apply(const HitContext& context) const = 0;`.
- When a spell hits a target, `SpellDefinition::applyOnHit` loops through its heterogeneous vector of effects:
  ```cpp
  for (const auto& effect : m_effects) {
      effect->apply(context); // Polymorphic dispatch
  }
  ```
- Subclasses implement drastically different algorithms:
  - `DamageEffect`: directly damages the target.
  - `BurnEffect`: attaches `BurnStatus` to the character.
  - `ChainEffect`: finds adjacent enemies within jump radius and branches execution.
  - `AreaEffect`: iterates over all entities within blast radius and cascades child effects.

---

## 5. Composition over Inheritance (Hợp thành thay vì Kế thừa)

Rather than building rigid, deep inheritance trees (e.g. `BurningKnockbackExplosiveProjectileCharacter`), Spell Storm builds complex behaviors by composing smaller, focused components.

### 1. `Character` composes `HealthComponent` and `StatusEffectList`
- **File**: [`include/entities/Character.hpp`](../include/entities/Character.hpp)
- Instead of subclassing `HealthObject`, `Character` has:
  ```cpp
  private:
      HealthComponent m_health;
      StatusEffectList m_statuses;
  ```
- **Benefits**:
  - High cohesion: `HealthComponent` is reusable for destructible objects, shields, or props that do not have movement physics.
  - Easy unit testing: `HealthComponent` is testable in complete isolation without mocking graphics or coordinates.

### 2. `SpellDefinition` composes `HitEffect` Strategies
- **File**: [`include/spells/SpellDefinition.hpp`](../include/spells/SpellDefinition.hpp)
- Rather than creating a class hierarchy for each spell (`FireballSpell`, `FrostTempestSpell`, `ChainLightningSpell`), a single `SpellDefinition` class holds:
  ```cpp
  ProjectileProfile m_profile;
  std::vector<std::unique_ptr<HitEffect>> m_effects;
  ```
- Any new spell is composed by chaining effects:
  ```cpp
  auto firestorm = std::make_shared<SpellDefinition>(...);
  auto area = std::make_unique<AreaEffect>(90.f, true);
  area->then(std::make_unique<DamageEffect>(1.2f));
  area->then(std::make_unique<BurnEffect>(0.4f, 2.5f));
  firestorm->addEffect(std::move(area));
  ```

---

## 6. Separation of Concerns (Tách biệt Trách nhiệm)

| Subsystem / Class | Strictly Responsible For | Strictly NOT Responsible For |
| :--- | :--- | :--- |
| `HealthComponent` | Current/Max HP, armor mitigation formula | Movement, rendering, entity IDs |
| `StatusEffect` | Duration timer, speed modifier, damage tick | SFML colors, particle effects |
| `DrawUtils` | Procedural SFML drawing and color lerping | Damage calculation, game rules |
| `SpellRegistry` | Mapping element combinations to `SpellDefinition` | Projectile physics, input polling |
| `EffectSystem` | Particle bursts, expanding rings, beams | Combat rules, game states |

---

## 7. C++17 Memory Safety & Lifetimes

1. **Virtual Destructors**:
   - `Entity`, `Character`, `Enemy`, `StatusEffect`, `HitEffect`, and `State` declare `virtual ~ClassName() = default;` ensuring correct polymorphic deletion through base-class pointers.
2. **Elimination of Raw Owning Pointers**:
   - Zero `new` and `delete` in application code. All heap resources use `std::make_unique` or `std::make_shared`.
3. **No Use-After-Free in Combat Chains**:
   - In [`ChainEffect::apply`](../src/spells/HitEffect.cpp), entity IDs and positions are copied *before* invoking child damage callbacks, guaranteeing that an enemy dying mid-chain never results in dangling pointer dereference.
4. **Deterministic ID Generation**:
   - `Entity::resetIdCounter()` provides a clean way to reset ID tracking between test suites and matches.
