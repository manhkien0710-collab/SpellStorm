#pragma once

#include "combat/Damage.hpp"
#include "entities/Entity.hpp"

#include <SFML/Graphics/Color.hpp>
#include <SFML/System/Vector2.hpp>

#include <functional>
#include <vector>

namespace ss {

class Enemy;
class EffectSystem;

// Interface through which spell effects interact with the battlefield.
// HitEffects depend on this abstraction, not on World or CombatSystem
// (Dependency Inversion), so they can be tested and reused freely.
class CombatServices {
public:
    virtual ~CombatServices() = default;

    virtual void damageEnemy(Enemy& enemy, const DamageInfo& damage, sf::Color color) = 0;
    virtual Enemy* findNearestEnemy(sf::Vector2f from, float range, const std::vector<EntityId>& exclude) = 0;
    virtual void forEachEnemyInRadius(sf::Vector2f center, float radius, const std::function<void(Enemy&)>& action) = 0;
    virtual EffectSystem& effects() = 0;
};

} // namespace ss
