#include "core/GameSession.hpp"
#include "core/DrawUtils.hpp"
#include "core/MathUtils.hpp"
#include "entities/BossEnemy.hpp"
#include "entities/MeleeEnemy.hpp"
#include "entities/Projectile.hpp"
#include "entities/RangedEnemy.hpp"
#include "entities/TankEnemy.hpp"

#include <SFML/Graphics/RectangleShape.hpp>
#include <SFML/Graphics/Text.hpp>
#include <SFML/Window/Mouse.hpp>
#include <algorithm>
#include <limits>

#include "core/ResourceManager.hpp"
#include "environment/TileMap.hpp"

namespace ss {

GameSession::GameSession(const Arena& arena, const SpellRegistry& registry, unsigned int seed,
                         const ResourceManager* resources)
    : m_arena(arena)
    , m_registry(registry)
    , m_rng(seed != 0 ? seed : 42)
    , m_player(arena.center())
    , m_camera(sf::Vector2f(1280.f, 720.f), arena.bounds())
    , m_combatSystem(arena, m_effects)
    , m_powerUpManager(arena, m_rng)
    , m_spawner(arena, m_rng)
    , m_resources(resources)
{
    restartMatch();
}

void GameSession::restartMatch()
{
    // 1. Reset player vital statistics, modifiers, position, alive flag
    m_player.reset(m_arena.center());
    m_camera.setZoom(Camera::kDefaultZoom);
    m_camera.setCenter(m_player.position());

    // 2. Clear all active entities and missiles
    m_enemies.clear();
    m_combatSystem.clearProjectiles();
    m_expSystem.clear();
    m_powerUpManager.reset();
    m_effects.clear();

    // 3. Reset progression systems and spawner
    m_spawner.reset();
    m_upgradeManager.reset();
    m_progression.reset(1);

    // 4. Close any open overlays
    m_levelUpOverlay.close();
    m_gameOverOverlay.close();

    // 5. Reset match timers and metrics
    m_matchElapsed = 0.f;
    m_enemiesDefeated = 0;
    m_overlayAutoTimer = 0.f;

    // 6. Reset active spell element mixture to base Fire
    m_activeMix = {Element::Fire};
    m_currentSpell = m_registry.resolve(m_activeMix);

    // 7. Reset shooting mode to default Manual
    m_shootingMode = ShootingMode::Manual;
    m_toggleKeyHeld = false;
    m_modeNotificationTimer = 0.f;
}

void GameSession::toggleShootingMode()
{
    if (!isPlaying()) {
        return;
    }
    if (m_shootingMode == ShootingMode::Manual) {
        m_shootingMode = ShootingMode::Auto;
        m_modeNotificationTimer = 1.8f;
        m_modeNotificationText = "AUTO-SHOT ACTIVATED [V]";
        m_modeNotificationColor = sf::Color(60, 255, 130);
        m_effects.ring(m_player.position(), 28.f, sf::Color(60, 255, 130), 0.25f);
    } else {
        m_shootingMode = ShootingMode::Manual;
        m_modeNotificationTimer = 1.8f;
        m_modeNotificationText = "MANUAL SHOOTING ACTIVATED [V]";
        m_modeNotificationColor = sf::Color(100, 200, 255);
        m_effects.ring(m_player.position(), 28.f, sf::Color(100, 200, 255), 0.25f);
    }
}

Enemy* GameSession::findTargetEnemy() const
{
    Enemy* best = nullptr;
    float bestDistSq = std::numeric_limits<float>::max();

    for (const auto& enemy : m_enemies) {
        if (!enemy || !enemy->isAlive()) {
            continue;
        }
        const float dSq = math::distanceSq(m_player.position(), enemy->position());
        if (dSq < bestDistSq - 1e-4f) {
            bestDistSq = dSq;
            best = enemy.get();
        } else if (std::abs(dSq - bestDistSq) <= 1e-4f) {
            // Deterministic tie-breaking by lower entity ID
            if (!best || enemy->id() < best->id()) {
                best = enemy.get();
            }
        }
    }
    return best;
}

void GameSession::triggerGameOver()
{
    if (m_gameOverOverlay.isOpen()) {
        return; // Guard against duplicate state transitions
    }

    // Ensure player is marked dead and closed out of level up modal
    if (m_player.isAlive()) {
        m_player.destroy();
    }
    m_levelUpOverlay.close();

    // Open Game Over screen with final match metrics
    m_gameOverOverlay.open(m_matchElapsed, m_enemiesDefeated, m_progression.level());

    // Death feedback particles and ring
    m_effects.burst(m_player.position(), sf::Color(255, 60, 60), 22, 190.f, 0.5f);
    m_effects.ring(m_player.position(), 40.f, sf::Color(255, 80, 80), 0.35f);
}

void GameSession::infuseElement(Element element)
{
    if (!isPlaying()) {
        return;
    }
    m_activeMix.push_back(element);
    if (m_activeMix.size() > 2) {
        m_activeMix.erase(m_activeMix.begin());
    }
    m_currentSpell = m_registry.resolve(m_activeMix);
}

void GameSession::applySelectedLevelUpUpgrade()
{
    if (const auto* up = m_levelUpOverlay.selectedUpgrade()) {
        m_upgradeManager.applyUpgrade(up->type, m_player);
        m_progression.consumeLevelUp();
        m_effects.burst(m_player.position(), up->accentColor, 18, 160.f, 0.4f);
        m_effects.ring(m_player.position(), 35.f, sf::Color::White, 0.25f);
        m_levelUpOverlay.close();

        if (m_progression.hasPendingLevelUp()) {
            auto nextChoices = m_upgradeManager.getAvailableUpgrades(3, m_rng);
            if (!nextChoices.empty()) {
                m_levelUpOverlay.open(nextChoices);
            } else {
                m_progression.consumeLevelUp();
            }
        }
    }
}

void GameSession::castSpell(sf::Vector2f targetPos)
{
    if (!m_currentSpell || !m_player.canCast() || !m_player.isAlive() || !isPlaying()) {
        return;
    }

    const sf::Vector2f playerPos = m_player.position();
    const sf::Vector2f toMouse = targetPos - playerPos;
    const sf::Vector2f baseDir = (math::lengthSq(toMouse) > 1e-4f)
        ? math::normalize(toMouse)
        : math::fromAngle(m_player.aimAngle());

    const auto& profile = m_currentSpell->profile();
    m_player.triggerCast(profile.cooldownMultiplier * m_powerUpManager.castCooldownMultiplier());

    const float damageBoost = m_powerUpManager.isElementalBurstActive() ? 1.35f : 1.0f;
    const float baseDamage = 35.f * profile.damageMultiplier * m_player.damageMultiplier() * damageBoost;
    const float baseSpeed = 420.f * profile.speedMultiplier * m_player.projectileSpeedMultiplier();
    const float baseRadius = profile.radius + (m_powerUpManager.isElementalBurstActive() ? 3.0f : 0.0f);
    const float baseLifetime = profile.lifetime * m_player.projectileLifetimeMultiplier();
    const int pierce = std::min(Projectile::kMaxAllowedPierce,
                                profile.pierce + m_player.bonusPierce() + m_powerUpManager.bonusPierce());
    const int extra = profile.extraProjectiles + (m_powerUpManager.isElementalBurstActive() ? 1 : 0);

    if (extra == 0) {
        m_combatSystem.addProjectile(std::make_unique<Projectile>(
            playerPos + baseDir * (m_player.radius() + 4.f),
            baseDir, baseSpeed, baseRadius, baseDamage, baseLifetime, pierce,
            ProjectileFaction::Player, m_currentSpell->primaryColor(), m_currentSpell->secondaryColor(),
            m_currentSpell
        ));
    } else {
        const float totalSpread = math::toRadians(profile.spreadDegrees * static_cast<float>(extra));
        const float startAngle = math::angleOf(baseDir) - totalSpread * 0.5f;
        const float step = (extra > 0) ? (totalSpread / static_cast<float>(extra)) : 0.f;

        for (int i = 0; i <= extra; ++i) {
            const float angle = startAngle + step * static_cast<float>(i);
            const sf::Vector2f dir = math::fromAngle(angle);
            m_combatSystem.addProjectile(std::make_unique<Projectile>(
                playerPos + dir * (m_player.radius() + 4.f),
                dir, baseSpeed, baseRadius, baseDamage, baseLifetime, pierce,
                ProjectileFaction::Player, m_currentSpell->primaryColor(), m_currentSpell->secondaryColor(),
                m_currentSpell
            ));
        }
    }

    // Muzzle feedback
    m_effects.ring(playerPos, 20.f, m_currentSpell->secondaryColor(), 0.16f);
    m_effects.burst(playerPos + baseDir * (m_player.radius() + 6.f), m_currentSpell->primaryColor(), 6, 75.f, 0.15f);
}

void GameSession::handleContinuousInput(sf::Vector2f mousePos)
{
    if (isPlaying() && m_shootingMode == ShootingMode::Manual && sf::Mouse::isButtonPressed(sf::Mouse::Left) && m_player.canCast()) {
        castSpell(mousePos);
    }
}

SessionEventResult GameSession::handleEvent(const sf::Event& event, sf::Vector2f worldMousePos,
                                            std::optional<sf::Vector2f> uiMousePos)
{
    const sf::Vector2f uiPos = uiMousePos.value_or(worldMousePos);

    if (event.type == sf::Event::KeyReleased && event.key.code == sf::Keyboard::V) {
        m_toggleKeyHeld = false;
    }

    // 1. Game Over screen has highest input priority when active
    if (m_gameOverOverlay.isOpen()) {
        if (event.type == sf::Event::MouseMoved) {
            m_gameOverOverlay.handleMouseMove(uiPos);
            return SessionEventResult::Handled;
        } else if (event.type == sf::Event::MouseButtonPressed) {
            if (event.mouseButton.button == sf::Mouse::Left) {
                m_gameOverOverlay.handleMouseButtonPressed(uiPos);
            }
            return SessionEventResult::Handled;
        } else if (event.type == sf::Event::MouseButtonReleased) {
            if (event.mouseButton.button == sf::Mouse::Left) {
                GameOverAction action = m_gameOverOverlay.handleMouseButtonReleased(uiPos);
                if (action == GameOverAction::Restart) {
                    restartMatch();
                    return SessionEventResult::Handled;
                } else if (action == GameOverAction::Quit) {
                    return SessionEventResult::RequestQuit;
                }
            }
            return SessionEventResult::Handled;
        } else if (event.type == sf::Event::KeyPressed) {
            GameOverAction action = m_gameOverOverlay.handleKeyPress(event.key.code);
            if (action == GameOverAction::Restart) {
                restartMatch();
                return SessionEventResult::Handled;
            } else if (action == GameOverAction::Quit) {
                return SessionEventResult::RequestQuit;
            }
            return SessionEventResult::Handled;
        }
        return SessionEventResult::Handled; // Absorb any other events during Game Over
    }

    // 2. Level Up modal input
    if (m_levelUpOverlay.isOpen()) {
        if (event.type == sf::Event::MouseMoved) {
            m_levelUpOverlay.handleMouseMove(uiPos);
            return SessionEventResult::Handled;
        } else if (event.type == sf::Event::KeyPressed) {
            if (event.key.code == sf::Keyboard::Escape) {
                return SessionEventResult::RequestQuit;
            }
            if (m_levelUpOverlay.handleKeyPress(event.key.code)) {
                applySelectedLevelUpUpgrade();
            }
            return SessionEventResult::Handled;
        } else if (event.type == sf::Event::MouseButtonPressed) {
            if (event.mouseButton.button == sf::Mouse::Left) {
                if (m_levelUpOverlay.handleMouseClick(uiPos)) {
                    applySelectedLevelUpUpgrade();
                }
            }
            return SessionEventResult::Handled;
        }
        return SessionEventResult::Handled;
    }

    // 3. Normal Playing controls
    if (event.type == sf::Event::KeyPressed) {
        if (event.key.code == sf::Keyboard::Escape) {
            return SessionEventResult::RequestQuit;
        } else if (event.key.code == sf::Keyboard::V) {
            if (!m_toggleKeyHeld) {
                m_toggleKeyHeld = true;
                toggleShootingMode();
            }
            return SessionEventResult::Handled;
        } else if (event.key.code == sf::Keyboard::Num1) {
            infuseElement(Element::Fire);
            return SessionEventResult::Handled;
        } else if (event.key.code == sf::Keyboard::Num2) {
            infuseElement(Element::Ice);
            return SessionEventResult::Handled;
        } else if (event.key.code == sf::Keyboard::Num3) {
            infuseElement(Element::Lightning);
            return SessionEventResult::Handled;
        } else if (event.key.code == sf::Keyboard::Num4) {
            infuseElement(Element::Wind);
            return SessionEventResult::Handled;
        } else if (event.key.code == sf::Keyboard::Num5) {
            infuseElement(Element::Earth);
            return SessionEventResult::Handled;
        } else if (event.key.code == sf::Keyboard::Space) {
            m_player.takeDamage({25.f, false});
            m_effects.burst(m_player.position(), sf::Color::White, 8, 120.f, 0.3f);
            if (!m_player.isAlive()) {
                triggerGameOver();
            }
            return SessionEventResult::Handled;
        } else if (event.key.code == sf::Keyboard::H) {
            m_player.heal(20.f);
            m_effects.ring(m_player.position(), 35.f, sf::Color(80, 255, 120), 0.3f);
            return SessionEventResult::Handled;
        } else if (event.key.code == sf::Keyboard::L) {
            m_progression.addExp(30);
            if (m_progression.hasPendingLevelUp() && !m_levelUpOverlay.isOpen()) {
                auto choices = m_upgradeManager.getAvailableUpgrades(3, m_rng);
                if (!choices.empty()) {
                    m_levelUpOverlay.open(choices);
                }
            }
            return SessionEventResult::Handled;
        } else if (event.key.code == sf::Keyboard::Equal || event.key.code == sf::Keyboard::Add) {
            m_camera.zoomBy(0.15f);
            return SessionEventResult::Handled;
        } else if (event.key.code == sf::Keyboard::Hyphen || event.key.code == sf::Keyboard::Subtract) {
            m_camera.zoomBy(-0.15f);
            return SessionEventResult::Handled;
        } else if (event.key.code == sf::Keyboard::Num0) {
            m_camera.resetZoom();
            return SessionEventResult::Handled;
        }
    } else if (event.type == sf::Event::KeyReleased) {
        if (event.key.code == sf::Keyboard::V) {
            m_toggleKeyHeld = false;
            return SessionEventResult::Handled;
        }
    } else if (event.type == sf::Event::MouseWheelScrolled) {
        if (event.mouseWheelScroll.wheel == sf::Mouse::VerticalWheel) {
            const float delta = event.mouseWheelScroll.delta;
            if (delta > 0.f) {
                m_camera.zoomBy(0.12f * delta);
            } else if (delta < 0.f) {
                m_camera.zoomBy(0.12f * delta);
            }
            return SessionEventResult::Handled;
        }
    } else if (event.type == sf::Event::MouseButtonPressed) {
        if (event.mouseButton.button == sf::Mouse::Left) {
            if (m_shootingMode == ShootingMode::Manual) {
                castSpell(worldMousePos);
            }
            return SessionEventResult::Handled;
        }
    }

    return SessionEventResult::None;
}

void GameSession::update(float dt, sf::Vector2f worldMousePos, bool autoplay,
                        std::optional<sf::Vector2f> uiMousePos)
{
    const sf::Vector2f uiPos = uiMousePos.value_or(worldMousePos);

    // Transient mode change notification timer
    if (m_modeNotificationTimer > 0.f) {
        m_modeNotificationTimer = std::max(0.f, m_modeNotificationTimer - dt);
    }

    // 1. If Game Over is active, gameplay simulation remains completely frozen
    if (m_gameOverOverlay.isOpen()) {
        m_gameOverOverlay.handleMouseMove(uiPos);
        m_gameOverOverlay.update(dt);
        return;
    }

    // 2. Check if player has died
    if (!m_player.isAlive() || m_player.health() <= 0.f) {
        triggerGameOver();
        return;
    }

    // 3. If Level-Up modal is open, gameplay simulation remains frozen
    if (m_levelUpOverlay.isOpen()) {
        m_levelUpOverlay.handleMouseMove(uiPos);
        m_levelUpOverlay.update(dt);

        if (autoplay && !m_levelUpOverlay.options().empty()) {
            if (m_levelUpOverlay.selectedIndex() < 0) {
                m_levelUpOverlay.selectCard(0);
            }
            m_overlayAutoTimer += dt;
            if (m_overlayAutoTimer >= 0.8f && m_levelUpOverlay.tryConfirm()) {
                m_overlayAutoTimer = 0.f;
                applySelectedLevelUpUpgrade();
            }
        }
        return;
    }

    // 4. Normal gameplay simulation tick
    m_matchElapsed += dt;

    // Update player movement, collision resolution, and bounds
    const sf::Vector2f oldPlayerPos = m_player.position();
    m_player.handleInput(dt);
    const sf::Vector2f resolvedPlayerPos = m_arena.resolveCollision(oldPlayerPos, m_player.position(), m_player.radius());
    m_player.setPosition(resolvedPlayerPos);
    m_player.constrainToBounds(m_arena.bounds());
    m_player.update(dt);

    // Camera smoothly follows player across world
    m_camera.update(m_player.position(), dt, 14.f);

    // Shooting execution based on active shooting mode
    if (autoplay) {
        // Bot autoplay mode: automatically acquires nearest enemy
        Enemy* target = findTargetEnemy();
        if (target) {
            m_player.setAimTarget(target->position());
            if (m_player.canCast()) {
                castSpell(target->position());
            }
        } else {
            m_player.setAimTarget(worldMousePos);
        }
    } else if (m_shootingMode == ShootingMode::Auto) {
        // Auto-shot mode: shooting direction moves according to the player's mouse, firing automatically
        m_player.setAimTarget(worldMousePos);
        if (m_player.canCast()) {
            castSpell(worldMousePos);
        }
    } else {
        // Manual shooting: aim follows cursor, continuous attack if LMB held
        m_player.setAimTarget(worldMousePos);
        handleContinuousInput(worldMousePos);
    }

    // Update enemy spawner
    m_spawner.update(dt, m_player.position(), m_enemies);

    // Update enemies: AI navigation, bounds, contact damage, ranged attacks
    for (auto& enemy : m_enemies) {
        const sf::Vector2f oldEnemyPos = enemy->position();
        enemy->updateAI(dt, m_player.position(), &m_arena);
        const sf::Vector2f resolvedEnemyPos = m_arena.resolveCollision(oldEnemyPos, enemy->position(), enemy->radius());
        enemy->setPosition(resolvedEnemyPos);
        enemy->constrainToBounds(m_arena.bounds());
        enemy->update(dt);

        if (m_player.isAlive() && enemy->tryAttack(m_player)) {
            m_effects.burst(m_player.position(), sf::Color(255, 60, 60), 8, 100.f, 0.25f);
        }

        // Ranged enemy attack
        if (auto* ranged = dynamic_cast<RangedEnemy*>(enemy.get())) {
            if (ranged->canShoot() && m_player.isAlive()) {
                auto enemyProj = ranged->shoot(m_player.position());
                if (enemyProj) {
                    m_effects.ring(ranged->position(), 15.f, sf::Color(215, 80, 245), 0.18f);
                    m_combatSystem.addProjectile(std::move(enemyProj));
                }
            }
        }

        // Boss enemy special volley attack
        if (auto* boss = dynamic_cast<BossEnemy*>(enemy.get())) {
            if (boss->canFireVolley() && m_player.isAlive()) {
                auto bossProj = boss->fireVolley(m_player.position());
                for (auto& p : bossProj) {
                    m_combatSystem.addProjectile(std::move(p));
                }
                m_effects.ring(boss->position(), 45.f, sf::Color(255, 60, 40), 0.35f);
            }
        }
    }

    // Update combat system
    m_combatSystem.update(dt, m_player, m_enemies);

    // Process defeated enemies: record statistics, spawn EXP pickups, visual effects
    for (const auto& enemy : m_enemies) {
        if (!enemy->isAlive()) {
            m_effects.burst(enemy->position(), sf::Color(210, 180, 255), 14, 150.f, 0.4f);
            if (!enemy->isRewardClaimed()) {
                enemy->claimReward();
                m_expSystem.spawnPickup(enemy->position(), enemy->expReward());
                ++m_enemiesDefeated;
            }
        }
    }
    m_enemies.erase(
        std::remove_if(m_enemies.begin(), m_enemies.end(), [](const std::unique_ptr<Enemy>& e) {
            return !e->isAlive();
        }),
        m_enemies.end()
    );

    // Update experience pickups
    m_expSystem.update(dt, m_player, m_progression, m_effects);

    // Update power-up pickups & active temporary ability
    m_powerUpManager.update(dt, m_player, m_effects);

    // Update visual particle effects
    m_effects.update(dt);

    // Check player death from combat
    if (!m_player.isAlive() || m_player.health() <= 0.f) {
        triggerGameOver();
        return;
    }

    // Check if level up occurred
    if (m_progression.hasPendingLevelUp()) {
        auto choices = m_upgradeManager.getAvailableUpgrades(3, m_rng);
        if (!choices.empty()) {
            m_levelUpOverlay.open(choices);
        } else {
            m_progression.consumeLevelUp();
        }
    }
}

void GameSession::render(sf::RenderTarget& target, const sf::Font* font, const ResourceManager* resources) const
{
    const ResourceManager* res = resources ? resources : m_resources;
    const sf::Font* bodyFont = font ? font : (res && res->hasFont(FontId::Body) ? res->getFont(FontId::Body) : nullptr);
    const sf::Font* boldFont = (res && res->hasFont(FontId::Bold)) ? res->getFont(FontId::Bold) : bodyFont;

    // 1. Set world view for game world rendering
    target.setView(m_camera.view());

    // 2. Arena floor, runes, terrain, or tilemap
    m_arena.draw(target, res, &m_camera);

    // 3. Active Enemies
    for (const auto& enemy : m_enemies) {
        enemy->draw(target, res);
    }

    // 4. In-flight Projectiles
    m_combatSystem.draw(target, res);

    // 5. Experience Pickups
    m_expSystem.draw(target, res);

    // 6. Power-Up Pickups
    m_powerUpManager.drawWorld(target, m_camera);

    // 7. Player & Visual Effects
    m_player.draw(target, res);
    m_effects.draw(target);

    // 7. Reset to default UI view for fixed HUD, Minimap, and Modals
    target.setView(target.getDefaultView());

    // 8. Minimap (if wide tilemap world is active)
    if (m_arena.hasTileMap() && m_arena.tileMap()) {
        std::vector<sf::Vector2f> enemyPositions;
        enemyPositions.reserve(m_enemies.size());
        for (const auto& e : m_enemies) {
            if (e && e->isAlive()) {
                enemyPositions.push_back(e->position());
            }
        }
        m_arena.tileMap()->drawMinimap(target, {1110.f, 545.f}, {150.f, 150.f},
                                       m_player.position(), enemyPositions, bodyFont);
    }

    // 6. HUD elements
    if (bodyFont) {
        // Title & Active Spell
        sf::Text titleText;
        titleText.setFont(boldFont ? *boldFont : *bodyFont);
        titleText.setCharacterSize(22);
        titleText.setFillColor(sf::Color::White);
        titleText.setPosition(75.f, 60.f);
        titleText.setString("Spell Storm Arena | Active: " + (m_currentSpell ? m_currentSpell->name() : "None"));
        target.draw(titleText);

        // Spell Description
        sf::Text descText;
        descText.setFont(*bodyFont);
        descText.setCharacterSize(16);
        descText.setFillColor(m_currentSpell ? m_currentSpell->primaryColor() : sf::Color::Yellow);
        descText.setPosition(75.f, 90.f);
        descText.setString(m_currentSpell ? m_currentSpell->description() : "");
        target.draw(descText);

        // Instruction Text
        sf::Text instrText;
        instrText.setFont(*bodyFont);
        instrText.setCharacterSize(14);
        instrText.setFillColor(sf::Color(170, 180, 200));
        instrText.setPosition(75.f, 630.f);
        instrText.setString("WASD: Move | Left Click/Hold: Cast | V: Toggle Auto-Shot | Scroll/+/-: Zoom | 1-5: Elements | Space: Dmg | H: Heal | L: +EXP");
        target.draw(instrText);

        // Health Bar
        const float barW = 260.f;
        const float barH = 18.f;
        const sf::Vector2f barPos(75.f, 120.f);
        draw::line(target, barPos, barPos + sf::Vector2f(barW, 0.f), barH, sf::Color(50, 50, 60));
        draw::line(target, barPos, barPos + sf::Vector2f(barW * m_player.healthRatio(), 0.f), barH, sf::Color(220, 50, 50));

        sf::Text hpText;
        hpText.setFont(*bodyFont);
        hpText.setCharacterSize(13);
        hpText.setFillColor(sf::Color::White);
        hpText.setPosition(barPos.x + 8.f, barPos.y - 8.f);
        hpText.setString("HP: " + std::to_string(static_cast<int>(m_player.health())) + "/" +
                         std::to_string(static_cast<int>(m_player.maxHealth())) +
                         " (Armor: " + std::to_string(static_cast<int>(m_player.armor())) +
                         ") | Enemies: " + std::to_string(m_enemies.size()) +
                         " | Defeated: " + std::to_string(m_enemiesDefeated));
        target.draw(hpText);

        // EXP Bar
        const float expBarW = 260.f;
        const float expBarH = 12.f;
        const sf::Vector2f expBarPos(75.f, 148.f);
        draw::line(target, expBarPos, expBarPos + sf::Vector2f(expBarW, 0.f), expBarH, sf::Color(35, 45, 60));
        draw::line(target, expBarPos, expBarPos + sf::Vector2f(expBarW * m_progression.progressRatio(), 0.f), expBarH, sf::Color(50, 215, 255));

        sf::Text expText;
        expText.setFont(*bodyFont);
        expText.setCharacterSize(12);
        expText.setFillColor(sf::Color(190, 235, 255));
        expText.setPosition(expBarPos.x + 6.f, expBarPos.y - 7.f);
        expText.setString("LVL " + std::to_string(m_progression.level()) + " | EXP: " +
                          std::to_string(m_progression.currentExp()) + "/" +
                          std::to_string(m_progression.expForNextLevel()));
        target.draw(expText);

        // Shooting Mode HUD Badge
        const float modeBadgeW = 204.f;
        const float modeBadgeH = 22.f;
        const sf::Vector2f modeBadgePos(75.f, 172.f);

        sf::RectangleShape badge({modeBadgeW, modeBadgeH});
        badge.setPosition(modeBadgePos);

        const bool isAuto = (m_shootingMode == ShootingMode::Auto);
        if (isAuto) {
            badge.setFillColor(sf::Color(18, 48, 30, 230));
            badge.setOutlineColor(sf::Color(65, 225, 115));
            badge.setOutlineThickness(1.5f);
        } else {
            badge.setFillColor(sf::Color(24, 32, 46, 230));
            badge.setOutlineColor(sf::Color(85, 120, 175));
            badge.setOutlineThickness(1.5f);
        }
        target.draw(badge);

        // Status indicator dot
        const sf::Vector2f dotPos(modeBadgePos.x + 12.f, modeBadgePos.y + modeBadgeH * 0.5f);
        draw::circle(target, dotPos, 4.f, isAuto ? sf::Color(60, 255, 120) : sf::Color(90, 180, 255));

        sf::Text modeText;
        modeText.setFont(boldFont ? *boldFont : *bodyFont);
        modeText.setCharacterSize(12);
        modeText.setStyle(sf::Text::Bold);
        modeText.setFillColor(isAuto ? sf::Color(170, 255, 200) : sf::Color(205, 225, 255));
        modeText.setPosition(modeBadgePos.x + 24.f, modeBadgePos.y + 3.f);
        modeText.setString(isAuto ? "SHOOTING: AUTO  [V]" : "SHOOTING: MANUAL  [V]");
        target.draw(modeText);

        // Active Temporary Ability HUD Badge
        m_powerUpManager.drawHUD(target, bodyFont, {75.f, 202.f});

        // Transient mode switch notification banner
        if (m_modeNotificationTimer > 0.f) {
            const float alphaFactor = std::clamp(m_modeNotificationTimer / 0.35f, 0.f, 1.f);
            const sf::Uint8 alpha = static_cast<sf::Uint8>(255.f * alphaFactor);

            sf::Text notifText;
            notifText.setFont(boldFont ? *boldFont : *bodyFont);
            notifText.setString(m_modeNotificationText);
            notifText.setCharacterSize(15);
            notifText.setStyle(sf::Text::Bold);

            sf::Color col = m_modeNotificationColor;
            col.a = alpha;
            notifText.setFillColor(col);

            const sf::FloatRect nb = notifText.getLocalBounds();
            const float viewW = target.getView().getSize().x;
            notifText.setOrigin(nb.left + nb.width * 0.5f, nb.top + nb.height * 0.5f);
            notifText.setPosition(viewW * 0.5f, 55.f);

            // Background pill for notification
            sf::RectangleShape notifBg({nb.width + 30.f, nb.height + 14.f});
            notifBg.setOrigin((nb.width + 30.f) * 0.5f, (nb.height + 14.f) * 0.5f);
            notifBg.setPosition(viewW * 0.5f, 55.f);
            notifBg.setFillColor(sf::Color(14, 18, 26, static_cast<sf::Uint8>(210.f * alphaFactor)));
            sf::Color borderCol = m_modeNotificationColor;
            borderCol.a = static_cast<sf::Uint8>(180.f * alphaFactor);
            notifBg.setOutlineColor(borderCol);
            notifBg.setOutlineThickness(1.5f);

            target.draw(notifBg);
            target.draw(notifText);
        }
    }

    // 7. Active Boss Health Bar (Top Center Screen Space HUD)
    const Enemy* activeBoss = nullptr;
    for (const auto& enemy : m_enemies) {
        if (enemy && enemy->isAlive() && enemy->isBoss()) {
            activeBoss = enemy.get();
            break;
        }
    }

    if (activeBoss && bodyFont) {
        const float viewW = target.getView().getSize().x;
        const float barW = 560.f;
        const float barH = 22.f;
        const float barX = (viewW - barW) * 0.5f;
        const float barY = 28.f;

        // Boss Name Header above health bar
        sf::Text bossNameText;
        bossNameText.setFont(boldFont ? *boldFont : *bodyFont);
        bossNameText.setCharacterSize(16);
        bossNameText.setStyle(sf::Text::Bold);
        bossNameText.setFillColor(sf::Color(255, 215, 60));
        bossNameText.setString(activeBoss->name());
        const sf::FloatRect bnBounds = bossNameText.getLocalBounds();
        bossNameText.setOrigin(bnBounds.left + bnBounds.width * 0.5f, bnBounds.top);
        bossNameText.setPosition(viewW * 0.5f, barY - 20.f);
        target.draw(bossNameText);

        // Health Bar Outer Frame
        sf::RectangleShape barBg({barW, barH});
        barBg.setPosition(barX, barY);
        barBg.setFillColor(sf::Color(18, 20, 28, 235));
        barBg.setOutlineColor(sf::Color(220, 50, 50));
        barBg.setOutlineThickness(2.2f);
        target.draw(barBg);

        // Health Bar Fill
        const float fillRatio = std::clamp(activeBoss->healthRatio(), 0.f, 1.f);
        if (fillRatio > 0.f) {
            sf::RectangleShape barFill({barW * fillRatio, barH});
            barFill.setPosition(barX, barY);
            barFill.setFillColor(sf::Color(220, 40, 50));
            target.draw(barFill);

            // Shading highlight strip on upper half
            sf::RectangleShape highlight({barW * fillRatio, barH * 0.45f});
            highlight.setPosition(barX, barY);
            highlight.setFillColor(sf::Color(255, 120, 120, 110));
            target.draw(highlight);
        }

        // Numerical HP details centered inside bar
        sf::Text hpText;
        hpText.setFont(*bodyFont);
        hpText.setCharacterSize(13);
        hpText.setStyle(sf::Text::Bold);
        hpText.setFillColor(sf::Color::White);
        const int curHp = static_cast<int>(std::ceil(activeBoss->health()));
        const int maxHp = static_cast<int>(activeBoss->maxHealth());
        const int pct = static_cast<int>(fillRatio * 100.f);
        hpText.setString(std::to_string(curHp) + " / " + std::to_string(maxHp) + " (" + std::to_string(pct) + "%)");
        const sf::FloatRect hpBounds = hpText.getLocalBounds();
        hpText.setOrigin(hpBounds.left + hpBounds.width * 0.5f, hpBounds.top + hpBounds.height * 0.5f);
        hpText.setPosition(viewW * 0.5f, barY + barH * 0.5f);
        target.draw(hpText);
    }

    // 8. Level Up Modal (if open)
    if (m_levelUpOverlay.isOpen()) {
        m_levelUpOverlay.draw(target, bodyFont, res);
    }

    // 9. Game Over Screen Modal (if open)
    if (m_gameOverOverlay.isOpen()) {
        m_gameOverOverlay.draw(target, bodyFont, res);
    }
}

} // namespace ss
