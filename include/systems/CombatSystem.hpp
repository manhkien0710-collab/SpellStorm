#pragma once

#include "combat/CombatServices.hpp"
#include "core/Arena.hpp"
#include "entities/Enemy.hpp"
#include "entities/Player.hpp"
#include "entities/Projectile.hpp"
#include "systems/EffectSystem.hpp"

#include <functional>
#include <memory>
#include <vector>

namespace ss {

// Coordinates projectiles, hit collisions, on-hit spell effects,
// and implements CombatServices for area/chain elemental spells.
class CombatSystem : public CombatServices {
public:
    CombatSystem(const Arena& arena, EffectSystem& effects);

    // Spawns a new projectile into the battlefield
    void addProjectile(std::unique_ptr<Projectile> projectile);

    // Clears all in-flight projectiles
    void clearProjectiles() { m_projectiles.clear(); }

    void setActiveEnemies(std::vector<std::unique_ptr<Enemy>>* enemies) { m_activeEnemies = enemies; }

    std::size_t projectileCount() const { return m_projectiles.size(); }
    const std::vector<std::unique_ptr<Projectile>>& projectiles() const { return m_projectiles; }

    // Simulation update: moves projectiles, handles arena boundaries,
    // detects collisions with opposing factions, triggers on-hit spell effects,
    // and safely prunes dead/expired missiles.
    void update(float dt, Player& player, std::vector<std::unique_ptr<Enemy>>& enemies);

    // Renders all active projectiles
    void draw(sf::RenderTarget& target, const class ResourceManager* resources = nullptr) const;

    // CombatServices implementation
    void damageEnemy(Enemy& enemy, const DamageInfo& damage, sf::Color color) override;
    Enemy* findNearestEnemy(sf::Vector2f from, float range, const std::vector<EntityId>& exclude) override;
    void forEachEnemyInRadius(sf::Vector2f center, float radius, const std::function<void(Enemy&)>& action) override;
    EffectSystem& effects() override { return m_effects; }

private:
    const Arena& m_arena;
    EffectSystem& m_effects;
    std::vector<std::unique_ptr<Projectile>> m_projectiles;
    std::vector<std::unique_ptr<Enemy>>* m_activeEnemies = nullptr;
};

} // namespace ss
