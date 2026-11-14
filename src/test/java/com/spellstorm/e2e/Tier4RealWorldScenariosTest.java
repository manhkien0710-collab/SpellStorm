package com.spellstorm.e2e;

import com.badlogic.gdx.Input.Keys;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.combat.DamageInfo;
import com.spellstorm.combat.DamageMitigation;
import com.spellstorm.combat.Element;
import com.spellstorm.core.GameSession;
import com.spellstorm.core.GameState;
import com.spellstorm.entities.*;
import com.spellstorm.math.Vector2D;
import com.spellstorm.navigation.AStarPathfinder;
import com.spellstorm.systems.*;
import com.spellstorm.ui.GameOverOverlay;
import com.spellstorm.world.TileMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 4: Real-World Application Scenarios (E2E Integration)")
public class Tier4RealWorldScenariosTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // Scenario 1: Full Match Simulation (0 to 120s)
    // =========================================================================
    @Test
    @DisplayName("Scenario 1: Full Match Simulation (0 to 120s Timeline)")
    void testScenario1_FullMatchSimulation120s() {
        GameSession session = new GameSession(new TileMap(42L), null, 42L);
        assertTrue(session.isPlaying());
        assertEquals(0.0f, session.getMatchElapsed(), 1e-4f);

        float dt = 0.05f; // 20 simulation ticks per second
        boolean bossObservedAt60s = false;
        int maxEnemiesObserved = 0;
        int totalTicks = 0;

        // Run until match elapsed reaches 120s (accounting for pause during level-up modals)
        while (session.getMatchElapsed() < 120.0f && totalTicks < 4000) {
            // Autoplay mode: auto-aims nearest enemy, fires, auto-confirms level ups
            session.update(dt, session.getPlayer().getPosition(), session.getPlayer().getPosition(), true);
            totalTicks++;

            // Handle level up immediately to avoid wasting ticks in modal
            if (session.isLevelUpOpen()) {
                session.getLevelUpOverlay().selectCard(0);
                session.applySelectedLevelUpUpgrade();
            }

            // Keep player healthy during long endurance test
            if (session.getPlayer().getHealth() < 100.0f) {
                session.getPlayer().heal(100.0f);
            }

            float elapsed = session.getMatchElapsed();

            // Track living enemies
            int currentEnemies = session.getEnemies().size();
            if (currentEnemies > maxEnemiesObserved) {
                maxEnemiesObserved = currentEnemies;
            }

            // Check Boss presence around 60s
            if (elapsed >= 60.0f && !bossObservedAt60s) {
                for (Enemy e : session.getEnemies()) {
                    if (e.isBoss()) {
                        bossObservedAt60s = true;
                        assertEquals(1800.0f, e.getMaxHealth(), 0.1f);
                        assertEquals(45.0f, e.getArmor(), 0.1f);
                        assertEquals(50.0f, e.getRadius(), 0.1f);
                        break;
                    }
                }
            }
        }

        // Assertions verifying full match lifecycle
        assertTrue(session.getMatchElapsed() >= 120.0f, "Match elapsed must reach 120s");
        assertTrue(session.getPlayer().isAlive(), "Player should survive under autoplay");
        assertTrue(session.getProgression().getLevel() >= 2, "Player must level up during 120s match");
        assertTrue(session.getEnemiesDefeated() > 0, "Enemies must be defeated during match");
        assertTrue(bossObservedAt60s, "Boss Colossus must spawn at 60s milestone");
        assertTrue(session.getSpawner().getSpawnInterval() <= 0.60f, "Spawn interval must scale down towards 0.55s");
        assertTrue(session.getSpawner().getMaxEnemies() >= 34, "Enemy cap must scale up towards 36");
    }

    // =========================================================================
    // Scenario 2: Boss Battle & Recovery
    // =========================================================================
    @Test
    @DisplayName("Scenario 2: Boss Battle & Recovery (Volley Dodging, Armor Mitigation, Burst & Heal)")
    void testScenario2_BossBattleAndRecovery() {
        GameSession session = new GameSession();
        Player player = session.getPlayer();
        player.setPosition(new Vector2D(2048.0f, 2048.0f));

        // Spawn Boss 250px above player
        BossEnemy boss = new BossEnemy(new Vector2D(2048.0f, 1798.0f));
        session.addEnemy(boss);

        // 1. Boss fires 3-way spread volley
        assertTrue(boss.canFireVolley());
        List<Projectile> volley = boss.fireVolley(player.getPosition());
        assertEquals(3, volley.size());

        // 2. Player dodges perpendicular to volley trajectory
        player.setPosition(new Vector2D(2048.0f + 160.0f, 2048.0f));
        for (Projectile p : volley) {
            float distSq = p.getPosition().dist2(player.getPosition());
            float hitRadius = p.getRadius() + player.getRadius();
            // Volley missiles should miss the dodged player
            assertTrue(distSq > hitRadius * hitRadius);
        }

        // 3. Player collects Elemental Burst power-up
        session.getPowerUpManager().getActiveAbility().activate(PowerUpType.ELEMENTAL_BURST);
        assertTrue(session.getPowerUpManager().isElementalBurstActive());

        // 4. Retaliate: Elemental projectile hits boss
        float initialBossHp = boss.getHealth();
        DamageInfo burstDamage = new DamageInfo(250.0f, false);
        float actualDamage = boss.takeDamage(burstDamage);
        // Formula: 250 * 100 / (100 + 45) = 172.41 damage
        assertEquals(172.41f, actualDamage, 0.5f);
        assertEquals(initialBossHp - actualDamage, boss.getHealth(), 0.5f);

        // 5. Boss lands contact damage; Player mitigates and recovers with perk
        float initialPlayerHp = player.getHealth();
        float contactDmg = boss.getContactDamage(); // 45
        player.takeDamage(DamageMitigation.calculateDamage(contactDmg, player.getArmor()));
        assertTrue(player.getHealth() < initialPlayerHp);

        // Recovery: Vitality Surge perk (+40 max HP, +40 heal)
        float hpBeforePerk = player.getHealth();
        session.getUpgradeManager().applyUpgrade(UpgradeType.MAX_HEALTH, player);
        assertEquals(290.0f, player.getMaxHealth(), 1e-4f);
        assertEquals(hpBeforePerk + 40.0f, player.getHealth(), 1e-4f);

        // 6. Finishing blow and boss reward
        boss.takeDamage(new DamageInfo(2000.0f, true));
        assertFalse(boss.isAlive());
        int expReward = boss.claimReward();
        assertEquals(250, expReward);
        assertEquals(0, boss.claimReward(), "Claiming reward again must be 0 (idempotent)");
    }

    // =========================================================================
    // Scenario 3: River & Bridge Crossing Gauntlet
    // =========================================================================
    @Test
    @DisplayName("Scenario 3: River & Bridge Crossing Gauntlet (5 Bridges & Anti-Corner-Cutting)")
    void testScenario3_RiverAndBridgeCrossingGauntlet() {
        TileMap map = new TileMap(42L);
        AStarPathfinder pf = new AStarPathfinder(map);

        // Verify across all 5 procedural bridges
        int[] bridgeRows = {10, 36, 64, 92, 118};

        for (int row : bridgeRows) {
            float y = row * 32.0f + 16.0f;
            Vector2D westBank = new Vector2D(800.0f, y);
            Vector2D eastBank = new Vector2D(2200.0f, y);

            assertTrue(map.isTraversable(westBank, 16.0f));
            assertTrue(map.isTraversable(eastBank, 16.0f));

            // Line of sight directly across unbridged river water must be blocked
            float offBridgeY = (row > 20) ? (row - 10) * 32.0f + 16.0f : (row + 10) * 32.0f + 16.0f;
            assertFalse(pf.hasLineOfSight(new Vector2D(800.0f, offBridgeY), new Vector2D(2200.0f, offBridgeY), 16.0f));

            // Test Melee (r=16), Tank (r=26), and Boss (r=50) traversal
            for (float radius : new float[]{16.0f, 26.0f, 50.0f}) {
                List<Vector2D> path = pf.findPath(westBank.x, westBank.y, eastBank.x, eastBank.y, radius, 1000);
                assertFalse(path.isEmpty(), "Path must be found for radius " + radius + " at row " + row);

                // Verify every waypoint in the path:
                for (Vector2D waypoint : path) {
                    // 1. Must never be on a water tile
                    int[] tile = map.worldToTile(waypoint);
                    assertFalse(map.isWater(tile[0], tile[1]), "Waypoint cannot be water tile: " + waypoint);

                    // 2. Traversal clearance check
                    assertTrue(map.isTraversable(waypoint, radius), "Waypoint must have collision clearance for r=" + radius);
                }

                // Verify the path trajectory traverses across at least one bridge tile
                boolean touchedBridge = false;
                Vector2D prev = westBank;
                for (Vector2D wp : path) {
                    float dist = (float) Math.hypot(wp.x - prev.x, wp.y - prev.y);
                    int steps = Math.max(1, (int) Math.ceil(dist / 8.0f));
                    for (int s = 0; s <= steps; s++) {
                        float t = (float) s / steps;
                        float px = prev.x + (wp.x - prev.x) * t;
                        float py = prev.y + (wp.y - prev.y) * t;
                        int[] tile = map.worldToTile(px, py);
                        if (map.isBridgeTile(tile[0], tile[1])) {
                            touchedBridge = true;
                            break;
                        }
                    }
                    if (touchedBridge) break;
                    prev = wp;
                }
                assertTrue(touchedBridge, "Path trajectory must cross bridge deck for radius " + radius);
            }
        }
    }

    // =========================================================================
    // Scenario 4: Power-Up Juggling & Pause Invariance
    // =========================================================================
    @Test
    @DisplayName("Scenario 4: Power-Up Juggling & Pause Invariance during Modals")
    void testScenario4_PowerUpJugglingAndPauseInvariance() {
        GameSession session = new GameSession();
        Player player = session.getPlayer();
        PowerUpManager powerUps = session.getPowerUpManager();

        // Step 1: Collect Arcane Overdrive
        powerUps.spawnPickup(player.getPosition().x, player.getPosition().y, PowerUpType.ARCANE_OVERDRIVE);
        powerUps.update(0.016f, player, false);
        assertTrue(powerUps.hasActiveAbility());
        assertEquals(PowerUpType.ARCANE_OVERDRIVE, powerUps.getActiveAbility().getType());
        assertEquals(5.0f, powerUps.getActiveAbility().getRemainingDuration(), 0.05f);
        assertEquals(1.0f / 1.8f, powerUps.getCastCooldownMultiplier(), 1e-4f);

        // Step 2: Tick 2.5s unpaused
        powerUps.update(2.5f, player, false);
        assertEquals(2.5f, powerUps.getActiveAbility().getRemainingDuration(), 0.05f);

        // Step 3: Collect Piercing Surge -> REPLACES active ability & resets to 5.0s
        powerUps.spawnPickup(player.getPosition().x, player.getPosition().y, PowerUpType.PIERCING_SURGE);
        powerUps.update(0.016f, player, false);
        assertEquals(PowerUpType.PIERCING_SURGE, powerUps.getActiveAbility().getType());
        assertEquals(5.0f, powerUps.getActiveAbility().getRemainingDuration(), 0.05f);
        assertEquals(1.0f, powerUps.getCastCooldownMultiplier(), 1e-4f);
        assertEquals(2, powerUps.getBonusPierce());

        // Step 4: Advance 2.0s -> 3.0s remaining
        session.update(2.0f);
        assertEquals(3.0f, powerUps.getActiveAbility().getRemainingDuration(), 0.05f);

        // Step 5: Trigger Level-Up modal
        session.getProgression().addExp(50);
        session.update(0.016f);
        assertTrue(session.isLevelUpOpen());
        assertEquals(GameState.LEVEL_UP, session.getGameState());

        // Step 6: Advance 5.0 simulated seconds while modal is open
        for (int i = 0; i < 50; i++) {
            session.update(0.1f);
        }

        // POWER-UP TIMER MUST REMAIN FROZEN AT EXACTLY ~3.0s!
        assertEquals(3.0f, powerUps.getActiveAbility().getRemainingDuration(), 0.05f);
        assertTrue(session.isLevelUpOpen());

        // Step 7: Confirm level up card and close modal
        session.getLevelUpOverlay().selectCard(0);
        session.applySelectedLevelUpUpgrade();
        assertFalse(session.isLevelUpOpen());
        assertEquals(GameState.PLAYING, session.getGameState());

        // Step 8: Resume simulation -> ticks remaining 3.0s down to clean expiration
        session.update(3.2f);
        assertFalse(powerUps.hasActiveAbility());
        assertEquals(0, powerUps.getBonusPierce());
    }

    // =========================================================================
    // Scenario 5: Death, Game Over & Clean Restart
    // =========================================================================
    @Test
    @DisplayName("Scenario 5: Death, Game Over & Clean Restart")
    void testScenario5_DeathGameOverAndCleanRestart() {
        GameSession session = new GameSession();

        // Advance 15s in active play
        for (int i = 0; i < 150; i++) {
            session.update(0.1f);
            if (session.getPlayer().getHealth() < 100.0f) {
                session.getPlayer().heal(100.0f);
            }
        }
        assertTrue(session.getMatchElapsed() >= 14.9f);
        session.getProgression().addExp(100); // Level up

        // Inflict lethal damage to player
        session.getPlayer().takeDamage(1000.0f);
        session.update(0.016f);

        // 1. Verify Game Over state and simulation freeze
        assertTrue(session.isGameOver());
        assertEquals(GameState.GAME_OVER, session.getGameState());
        GameOverOverlay overlay = session.getGameOverOverlay();
        assertTrue(overlay.isOpen());
        assertEquals(session.getMatchElapsed(), overlay.getSurvivalTime(), 1e-4f);
        assertEquals(session.getEnemiesDefeated(), overlay.getEnemiesDefeated());
        assertEquals(session.getProgression().getLevel(), overlay.getLevelReached());

        // 2. Input Grace Period: 0.1s in (< 0.20s), restart click is blocked
        overlay.update(0.10f);
        Vector2D btnPos = new Vector2D(overlay.getRestartButtonBounds().x + 5f, overlay.getRestartButtonBounds().y + 5f);
        assertEquals(GameOverOverlay.Action.NONE, overlay.handleMouseClick(btnPos));

        // 3. Expire grace timer -> restart click succeeds
        overlay.update(0.15f);
        assertEquals(GameOverOverlay.Action.RESTART, overlay.handleMouseClick(btnPos));
        assertEquals(GameOverOverlay.Action.RESTART, overlay.handleKeyPress(Keys.R));

        // 4. Deterministic match restart
        session.restartMatch();

        // 5. Verify 100% complete state reset
        assertTrue(session.isPlaying());
        assertFalse(session.isGameOver());
        assertFalse(session.isLevelUpOpen());
        assertEquals(0.0f, session.getMatchElapsed(), 1e-4f);
        assertEquals(0, session.getEnemiesDefeated());
        assertEquals(1, session.getProgression().getLevel());
        assertEquals(0, session.getProgression().getCurrentExp());
        assertEquals(0, session.getProgression().getPendingLevelUps());
        assertEquals(0, session.getEnemies().size());
        assertEquals(0, session.getCombatSystem().getProjectileCount());
        assertEquals(0, session.getExperienceSystem().getPickupCount());
        assertFalse(session.getPowerUpManager().hasActiveAbility());
        assertEquals(250.0f, session.getPlayer().getHealth(), 1e-4f);
        assertEquals(250.0f, session.getPlayer().getMaxHealth(), 1e-4f);
        assertEquals(ShootingMode.MANUAL, session.getShootingMode());
        assertEquals(1, session.getActiveMix().size());
        assertEquals(Element.FIRE, session.getActiveMix().get(0));

        // 6. Post-restart tick runs cleanly
        assertDoesNotThrow(() -> session.update(0.1f));
        assertTrue(session.getMatchElapsed() >= 0.09f);
    }
}
