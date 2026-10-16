#include "systems/CombatSystem.hpp"

#include "core/MathUtils.hpp"

#include <algorithm>

namespace ss {

CombatSystem::CombatSystem(const Arena& arena, EffectSystem& effects)
    : m_arena(arena)
    , m_effects(effects)
{
}

void CombatSystem::addProjectile(std::unique_ptr<Projectile> projectile)
{
    if (projectile) {
        m_projectiles.push_back(std::move(projectile));
    }
}

void CombatSystem::damageEnemy(Enemy& enemy, const DamageInfo& damage, sf::Color color)
{
    if (enemy.isAlive()) {
        enemy.takeDamage(damage);
        m_effects.burst(enemy.position(), color, 8, 120.f, 0.25f);
    }
}

Enemy* CombatSystem::findNearestEnemy(sf::Vector2f from, float range, const std::vector<EntityId>& exclude)
{
    if (!m_activeEnemies) {
        return nullptr;
    }

    Enemy* best = nullptr;
    float bestDistSq = range * range;

    for (auto& enemy : *m_activeEnemies) {
        if (!enemy->isAlive()) {
            continue;
        }
        if (std::find(exclude.begin(), exclude.end(), enemy->id()) != exclude.end()) {
            continue;
        }
        const float dSq = math::distanceSq(from, enemy->position());
        if (dSq <= bestDistSq) {
            bestDistSq = dSq;
            best = enemy.get();
        }
    }
    return best;
}

void CombatSystem::forEachEnemyInRadius(sf::Vector2f center, float radius, const std::function<void(Enemy&)>& action)
{
    if (!m_activeEnemies) {
        return;
    }

    const float rSq = radius * radius;
    for (auto& enemy : *m_activeEnemies) {
        if (enemy->isAlive() && math::distanceSq(center, enemy->position()) <= rSq) {
            action(*enemy);
        }
    }
}

void CombatSystem::update(float dt, Player& player, std::vector<std::unique_ptr<Enemy>>& enemies)
{
    m_activeEnemies = &enemies;

    // 1. Update in-flight projectile positions and boundaries
    for (auto& p : m_projectiles) {
        p->update(dt);
        // Destroy missiles that travel outside arena walls
        if (!m_arena.contains(p->position(), -15.f)) {
            p->destroy();
        }
    }

    // 2. Collision detection and damage application
    for (auto& p : m_projectiles) {
        if (!p->isAlive()) {
            continue;
        }

        if (p->faction() == ProjectileFaction::Player) {
            for (auto& enemy : enemies) {
                if (!enemy->isAlive()) {
                    continue;
                }

                if (p->canHit(enemy->id()) &&
                    math::circlesOverlap(p->position(), p->radius(), enemy->position(), enemy->radius())) {
                    p->recordHit(enemy->id());

                    if (p->spell()) {
                        HitContext ctx{*enemy, p->position(), p->direction(), p->damage(), p->primaryColor(), *this};
                        p->spell()->applyOnHit(ctx);
                        m_effects.burst(p->position(), p->primaryColor(), 8, 110.f, 0.22f);
                    } else {
                        damageEnemy(*enemy, {p->damage(), false}, p->primaryColor());
                    }

                    if (p->pierceRemaining() > 0) {
                        p->decrementPierce();
                    } else {
                        p->destroy();
                        break;
                    }
                }
            }
        } else if (p->faction() == ProjectileFaction::Enemy) {
            if (player.isAlive() &&
                math::circlesOverlap(p->position(), p->radius(), player.position(), player.radius())) {
                player.takeDamage({p->damage(), false});
                m_effects.burst(player.position(), p->primaryColor(), 8, 100.f, 0.25f);
                p->destroy();
            }
        }
    }

    // 3. Prune destroyed projectiles
    m_projectiles.erase(
        std::remove_if(m_projectiles.begin(), m_projectiles.end(), [](const std::unique_ptr<Projectile>& p) {
            return !p->isAlive();
        }),
        m_projectiles.end()
    );

    m_activeEnemies = nullptr;
}

void CombatSystem::draw(sf::RenderTarget& target, const class ResourceManager* resources) const
{
    for (const auto& p : m_projectiles) {
        p->draw(target, resources);
    }
}

} // namespace ss
