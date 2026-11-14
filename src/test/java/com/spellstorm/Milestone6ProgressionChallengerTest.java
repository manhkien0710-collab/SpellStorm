package com.spellstorm;

import com.spellstorm.assets.AssetRegistry;
import com.spellstorm.entities.ExperiencePickup;
import com.spellstorm.entities.Player;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.ExperienceSystem;
import com.spellstorm.systems.PlayerProgression;
import com.spellstorm.systems.UpgradeInfo;
import com.spellstorm.systems.UpgradeManager;
import com.spellstorm.systems.UpgradeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenger Test Suite for Milestone 6:
 * Progression Curve, Experience Gems, and Upgrade Perks.
 *
 * Authored by m6_challenger_1_gen3.
 */
@DisplayName("Milestone 6: Progression, EXP Drops & Perk Caps Challenger Stress Suite")
public class Milestone6ProgressionChallengerTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // 1. EXP Values Boundary Transitions (14, 15, 29, 30, 0, negatives, 10000)
    // =========================================================================

    @Test
    @DisplayName("Challenge 1.1: EXP Pickup Boundaries (0, negatives, 14, 15, 29, 30, 10000)")
    public void testExpPickupBoundaryTransitions() {
        // Non-positive EXP values must clamp to at least 1 and be Cyan tier
        ExperiencePickup pZero = new ExperiencePickup(0f, 0f, 0);
        assertEquals(1, pZero.getExpValue(), "Zero EXP value must clamp to at least 1");
        assertEquals(AssetRegistry.TextureId.GEM_CYAN, pZero.getTextureId());

        ExperiencePickup pNeg1 = new ExperiencePickup(0f, 0f, -1);
        assertEquals(1, pNeg1.getExpValue(), "Negative EXP must clamp to 1");
        assertEquals(AssetRegistry.TextureId.GEM_CYAN, pNeg1.getTextureId());

        ExperiencePickup pNeg1000 = new ExperiencePickup(0f, 0f, -1000);
        assertEquals(1, pNeg1000.getExpValue(), "Extreme negative EXP must clamp to 1");
        assertEquals(AssetRegistry.TextureId.GEM_CYAN, pNeg1000.getTextureId());

        // Boundary: 14 -> Cyan gem (< 15)
        ExperiencePickup p14 = new ExperiencePickup(10f, 20f, 14);
        assertEquals(14, p14.getExpValue());
        assertEquals(AssetRegistry.TextureId.GEM_CYAN, p14.getTextureId(), "14 EXP must be Cyan tier");
        assertEquals(ExperiencePickup.CYAN_GEM_COLOR.r, p14.getGemColor().r, 1e-4f);
        assertEquals(ExperiencePickup.CYAN_GEM_COLOR.g, p14.getGemColor().g, 1e-4f);
        assertEquals(ExperiencePickup.CYAN_GEM_COLOR.b, p14.getGemColor().b, 1e-4f);

        // Boundary: 15 -> Emerald gem (15 <= exp < 30)
        ExperiencePickup p15 = new ExperiencePickup(10f, 20f, 15);
        assertEquals(15, p15.getExpValue());
        assertEquals(AssetRegistry.TextureId.GEM_EMERALD, p15.getTextureId(), "15 EXP must transition to Emerald tier");
        assertEquals(ExperiencePickup.EMERALD_GEM_COLOR.r, p15.getGemColor().r, 1e-4f);
        assertEquals(ExperiencePickup.EMERALD_GEM_COLOR.g, p15.getGemColor().g, 1e-4f);
        assertEquals(ExperiencePickup.EMERALD_GEM_COLOR.b, p15.getGemColor().b, 1e-4f);

        // Boundary: 29 -> Emerald gem (highest Emerald tier)
        ExperiencePickup p29 = new ExperiencePickup(10f, 20f, 29);
        assertEquals(29, p29.getExpValue());
        assertEquals(AssetRegistry.TextureId.GEM_EMERALD, p29.getTextureId(), "29 EXP must still be Emerald tier");

        // Boundary: 30 -> Gold gem (exp >= 30)
        ExperiencePickup p30 = new ExperiencePickup(10f, 20f, 30);
        assertEquals(30, p30.getExpValue());
        assertEquals(AssetRegistry.TextureId.GEM_GOLD, p30.getTextureId(), "30 EXP must transition to Gold tier");
        assertEquals(ExperiencePickup.GOLD_GEM_COLOR.r, p30.getGemColor().r, 1e-4f);
        assertEquals(ExperiencePickup.GOLD_GEM_COLOR.g, p30.getGemColor().g, 1e-4f);
        assertEquals(ExperiencePickup.GOLD_GEM_COLOR.b, p30.getGemColor().b, 1e-4f);

        // Extreme value: 10000 -> Gold gem
        ExperiencePickup p10k = new ExperiencePickup(10f, 20f, 10000);
        assertEquals(10000, p10k.getExpValue());
        assertEquals(AssetRegistry.TextureId.GEM_GOLD, p10k.getTextureId(), "10000 EXP must remain Gold tier");
    }

    @Test
    @DisplayName("Challenge 1.2: PlayerProgression Rejection of Non-Positive EXP Awards")
    public void testPlayerProgressionNonPositiveExpRejection() {
        PlayerProgression prog = new PlayerProgression(1);
        assertEquals(1, prog.getLevel());
        assertEquals(0, prog.getCurrentExp());
        assertEquals(0, prog.getPendingLevelUps());

        // Zero award rejected
        int gained0 = prog.addExp(0);
        assertEquals(0, gained0);
        assertEquals(1, prog.getLevel());
        assertEquals(0, prog.getCurrentExp());
        assertEquals(0, prog.getPendingLevelUps());

        // Negative awards rejected without decrementing or corrupting state
        int gainedNeg = prog.addExp(-25);
        assertEquals(0, gainedNeg);
        assertEquals(1, prog.getLevel());
        assertEquals(0, prog.getCurrentExp());

        int gainedExtremeNeg = prog.addExp(Integer.MIN_VALUE);
        assertEquals(0, gainedExtremeNeg);
        assertEquals(1, prog.getLevel());
        assertEquals(0, prog.getCurrentExp());
    }

    // =========================================================================
    // 2. Exact Level Curve Evaluation for Levels 1 to 100 (25 + (lvl-1)*20)
    // =========================================================================

    @Test
    @DisplayName("Challenge 2.1: Mathematical Invariance of Level Curve from Level 1 to 100")
    public void testLevelCurveFormulaLevels1To100() {
        for (int lvl = 1; lvl <= 100; lvl++) {
            int expected = 25 + (lvl - 1) * 20;
            int actual = PlayerProgression.expRequiredForLevel(lvl);
            assertEquals(expected, actual, "Mismatch at level " + lvl);
        }

        // Spot check key landmarks
        assertEquals(25, PlayerProgression.expRequiredForLevel(1));
        assertEquals(45, PlayerProgression.expRequiredForLevel(2));
        assertEquals(65, PlayerProgression.expRequiredForLevel(3));
        assertEquals(1005, PlayerProgression.expRequiredForLevel(50));
        assertEquals(2005, PlayerProgression.expRequiredForLevel(100));

        // Non-positive clamped levels must strictly return Level 1 requirement (25)
        assertEquals(25, PlayerProgression.expRequiredForLevel(0));
        assertEquals(25, PlayerProgression.expRequiredForLevel(-1));
        assertEquals(25, PlayerProgression.expRequiredForLevel(-999));
        assertEquals(25, PlayerProgression.expRequiredForLevel(Integer.MIN_VALUE));
    }

    @Test
    @DisplayName("Challenge 2.2: 100 Sequential Single-Level Transitions")
    public void test100SequentialSingleLevelTransitions() {
        PlayerProgression prog = new PlayerProgression(1);

        for (int targetLevel = 2; targetLevel <= 101; targetLevel++) {
            int currentLevel = prog.getLevel();
            int required = prog.getExpForNextLevel();
            int expectedReq = 25 + (currentLevel - 1) * 20;
            assertEquals(expectedReq, required);

            // Award exact threshold for next level
            int gained = prog.addExp(required);
            assertEquals(1, gained, "Must gain exactly 1 level per threshold");
            assertEquals(targetLevel, prog.getLevel());
            assertEquals(0, prog.getCurrentExp(), "Current EXP must be exactly 0 after exact award");
            assertEquals(1, prog.getPendingLevelUps());

            // Consume level up
            prog.consumeLevelUp();
            assertEquals(0, prog.getPendingLevelUps());
            assertFalse(prog.hasPendingLevelUp());
        }
    }

    // =========================================================================
    // 3. Massive Single EXP Awards & Multi-Level Jump Simulation
    // =========================================================================

    /**
     * Independent mathematical oracle for EXP awards.
     */
    private static class ProgressionOracle {
        int level;
        int currentExp;
        int pendingLevelUps;

        ProgressionOracle(int startLvl, int startExp) {
            this.level = startLvl;
            this.currentExp = startExp;
            this.pendingLevelUps = 0;
        }

        void award(int amount) {
            if (amount <= 0) return;
            currentExp += amount;
            while (true) {
                int needed = 25 + (level - 1) * 20;
                if (currentExp >= needed) {
                    currentExp -= needed;
                    level++;
                    pendingLevelUps++;
                } else {
                    break;
                }
            }
        }
    }

    @Test
    @DisplayName("Challenge 3.1: Massive 5000 EXP Single Award Analytical Verification")
    public void testMassive5000ExpAward() {
        PlayerProgression prog = new PlayerProgression(1);

        // Analytical derivation:
        // sum_{i=1}^{K} (25 + (i-1)*20) = 10*K^2 + 15*K
        // For K = 21: 10*(441) + 15*21 = 4410 + 315 = 4725 EXP
        // For K = 22: 4725 + (25 + 21*20) = 4725 + 445 = 5170 EXP
        // 5000 EXP results in K = 21 levels gained -> level 22!
        // Remaining EXP: 5000 - 4725 = 275 EXP.
        // Pending level-ups: 21.

        int gained = prog.addExp(5000);
        assertEquals(21, gained);
        assertEquals(22, prog.getLevel());
        assertEquals(275, prog.getCurrentExp());
        assertEquals(21, prog.getPendingLevelUps());
        assertEquals(445, prog.getExpForNextLevel());
        assertEquals(275f / 445f, prog.getProgressRatio(), 1e-4f);

        // Consume all pending level ups
        for (int i = 0; i < 21; i++) {
            assertTrue(prog.hasPendingLevelUp());
            prog.consumeLevelUp();
        }
        assertEquals(0, prog.getPendingLevelUps());
        assertFalse(prog.hasPendingLevelUp());
    }

    @Test
    @DisplayName("Challenge 3.2: Multi-Scale Stress Awards against Independent Oracle (100 to 1,000,000 EXP)")
    public void testMassiveAwardsAgainstOracle() {
        int[] testAwards = {100, 250, 1000, 3333, 5000, 10000, 75000, 250000, 1000000};

        for (int award : testAwards) {
            PlayerProgression prog = new PlayerProgression(1);
            ProgressionOracle oracle = new ProgressionOracle(1, 0);

            int gained = prog.addExp(award);
            oracle.award(award);

            assertEquals(oracle.level, prog.getLevel(), "Level mismatch for award " + award);
            assertEquals(oracle.currentExp, prog.getCurrentExp(), "CurrentExp mismatch for award " + award);
            assertEquals(oracle.pendingLevelUps, prog.getPendingLevelUps(), "Pending mismatch for award " + award);
            assertEquals(oracle.pendingLevelUps, gained, "Gained mismatch for award " + award);
        }
    }

    @Test
    @DisplayName("Challenge 3.3: Boundary Jumps (Exact sum - 1, Exact sum, Exact sum + 1)")
    public void testExactMultiLevelBoundaryThresholds() {
        // First 3 levels cost: 25 + 45 + 65 = 135 EXP.
        // 134 EXP: level 3, currentExp 64, pending 2.
        PlayerProgression progMinus = new PlayerProgression(1);
        progMinus.addExp(134);
        assertEquals(3, progMinus.getLevel());
        assertEquals(64, progMinus.getCurrentExp());
        assertEquals(2, progMinus.getPendingLevelUps());

        // 135 EXP: level 4, currentExp 0, pending 3.
        PlayerProgression progExact = new PlayerProgression(1);
        progExact.addExp(135);
        assertEquals(4, progExact.getLevel());
        assertEquals(0, progExact.getCurrentExp());
        assertEquals(3, progExact.getPendingLevelUps());

        // 136 EXP: level 4, currentExp 1, pending 3.
        PlayerProgression progPlus = new PlayerProgression(1);
        progPlus.addExp(136);
        assertEquals(4, progPlus.getLevel());
        assertEquals(1, progPlus.getCurrentExp());
        assertEquals(3, progPlus.getPendingLevelUps());
    }

    @Test
    @DisplayName("Challenge 3.4: Integer Overflow Protection on Extreme Awards")
    public void testIntegerOverflowProtection() {
        PlayerProgression prog = new PlayerProgression(1);
        prog.addExp(100);
        // Adding Integer.MAX_VALUE should not overflow into negative numbers
        assertDoesNotThrow(() -> prog.addExp(Integer.MAX_VALUE));
        assertTrue(prog.getLevel() > 1000, "Level must advance massively without overflow error");
        assertTrue(prog.getCurrentExp() >= 0, "Current EXP must never be negative");
    }

    // =========================================================================
    // 4. Magnet Pull Physics: Zero Distance, Boundary, Beyond Radius & Acceleration
    // =========================================================================

    @Test
    @DisplayName("Challenge 4.1: Magnet Physics Zero Distance Handling (No NaN or Inf)")
    public void testMagnetPullZeroDistance() {
        ExperienceSystem expSystem = new ExperienceSystem();
        Player player = new Player(new Vector2D(400f, 400f));
        player.setPickupRadius(45.0f);
        PlayerProgression prog = new PlayerProgression(1);

        // Spawn pickup exactly at player center (dist == 0)
        expSystem.spawnPickup(400f, 400f, 25);
        assertEquals(1, expSystem.getPickupCount());

        // Updating must safely collect without producing NaN coordinates or division-by-zero
        assertDoesNotThrow(() -> expSystem.update(0.016f, player, prog));

        // Must be collected immediately because dist (0) <= collectRadius (45)
        assertEquals(0, expSystem.getPickupCount(), "Zero-distance pickup must be collected immediately");
        assertEquals(2, prog.getLevel(), "Player must level up from 25 EXP");
    }

    @Test
    @DisplayName("Challenge 4.2: Exact Boundary Distance (dist == magnetRadius) Pull Activation")
    public void testMagnetPullExactBoundary() {
        ExperienceSystem expSystem = new ExperienceSystem();
        Player player = new Player(new Vector2D(500f, 500f));
        player.setPickupRadius(45.0f);
        final float magnetRadius = 45.0f * 2.80f; // 126.0f
        PlayerProgression prog = new PlayerProgression(1);

        // Spawn gem at exactly magnetRadius distance: (500, 500 + 126)
        expSystem.spawnPickup(500f, 500f + magnetRadius, 10);
        assertEquals(1, expSystem.getPickupCount());

        // At boundary (dist == magnetRadius):
        // pullSpeed = BASE_PULL_SPEED + (magnetRadius - dist) * 2.5 = 240.0 + 0 = 240.0f
        float dt = 0.05f;
        expSystem.update(dt, player, prog);

        ExperiencePickup gem = expSystem.getPickups().get(0);
        float expectedY = (500f + magnetRadius) - (240.0f * dt); // 626.0 - 12.0 = 614.0
        assertEquals(expectedY, gem.getPosition().y, 0.5f, "Gem at exact boundary must begin pulling at 240 px/s");
        assertEquals(500f, gem.getPosition().x, 1e-4f);
    }

    @Test
    @DisplayName("Challenge 4.3: Beyond Magnet Radius (dist > magnetRadius) Strictly No Pull")
    public void testMagnetBeyondRadiusNoPull() {
        ExperienceSystem expSystem = new ExperienceSystem();
        Player player = new Player(new Vector2D(500f, 500f));
        player.setPickupRadius(45.0f);
        final float magnetRadius = 45.0f * 2.80f; // 126.0f
        PlayerProgression prog = new PlayerProgression(1);

        // Gem placed 0.5px beyond magnet radius (126.5px)
        expSystem.spawnPickup(500f, 500f + magnetRadius + 0.5f, 10);
        // Gem placed far away (350px)
        expSystem.spawnPickup(500f, 850f, 10);

        expSystem.update(0.1f, player, prog);

        List<ExperiencePickup> pickups = expSystem.getPickups();
        assertEquals(2, pickups.size());
        assertEquals(500f + magnetRadius + 0.5f, pickups.get(0).getPosition().y, 1e-4f, "Just beyond boundary must NOT move");
        assertEquals(850f, pickups.get(1).getPosition().y, 1e-4f, "Far beyond boundary must NOT move");
    }

    @Test
    @DisplayName("Challenge 4.4: Pull Acceleration Monotonicity (Closer Distance = Higher Velocity)")
    public void testPullAccelerationMonotonicity() {
        final float magnetRadius = 45.0f * 2.80f; // 126.0f

        float[] distances = {120f, 100f, 80f, 60f, 46f};
        float previousSpeed = 0.0f;

        for (float dist : distances) {
            float pullSpeed = ExperienceSystem.BASE_PULL_SPEED + (magnetRadius - dist) * ExperienceSystem.PULL_ACCELERATION_FACTOR;
            assertTrue(pullSpeed > previousSpeed, "Pull speed must strictly increase as distance decreases. dist=" + dist);
            previousSpeed = pullSpeed;
        }

        // Verify multi-frame acceleration trajectory towards player
        ExperienceSystem expSystem = new ExperienceSystem();
        Player player = new Player(new Vector2D(500f, 500f));
        player.setPickupRadius(45.0f);
        PlayerProgression prog = new PlayerProgression(1);

        expSystem.spawnPickup(500f, 620f, 10); // dist = 120px < 126px

        float lastY = 620f;
        float lastStepSize = 0f;
        int frames = 0;

        while (expSystem.getPickupCount() > 0 && frames < 50) {
            float currentY = expSystem.getPickups().get(0).getPosition().y;
            float step = lastY - currentY;
            if (frames > 1) {
                // Step size must increase frame-by-frame due to acceleration
                assertTrue(step >= lastStepSize - 0.05f, "Step size must accelerate towards player");
            }
            lastStepSize = step;
            lastY = currentY;
            frames++;
            expSystem.update(0.016f, player, prog);
        }

        // Must be successfully collected
        assertEquals(0, expSystem.getPickupCount());
        assertEquals(10, prog.getCurrentExp());
    }

    @Test
    @DisplayName("Challenge 4.5: Dead Player Does Not Attract Pickups")
    public void testDeadPlayerNoMagnetPull() {
        ExperienceSystem expSystem = new ExperienceSystem();
        Player player = new Player(new Vector2D(500f, 500f));
        player.setPickupRadius(45.0f);
        player.takeDamage(1000f); // Kill player
        assertFalse(player.isAlive());

        PlayerProgression prog = new PlayerProgression(1);
        expSystem.spawnPickup(500f, 560f, 10); // dist = 60px < 126px

        expSystem.update(0.1f, player, prog);

        assertEquals(1, expSystem.getPickupCount());
        assertEquals(560f, expSystem.getPickups().get(0).getPosition().y, 1e-4f, "Dead player must not pull pickups");
        assertEquals(0, prog.getCurrentExp());
    }

    // =========================================================================
    // 5. Upgrade Perks: Multipliers, Accumulation, and Strict Caps (Piercing=2, Others=5)
    // =========================================================================

    @Test
    @DisplayName("Challenge 5.1: Piercing Strict Cap at 2 and Stat Clamping")
    public void testPiercingStrictCap() {
        UpgradeManager manager = new UpgradeManager();
        Player player = new Player(new Vector2D(0, 0));

        assertEquals(0, player.getBonusPierce());
        assertEquals(0, manager.getRank(UpgradeType.PIERCING));
        assertEquals(2, manager.getMaxRank(UpgradeType.PIERCING));
        assertFalse(manager.isMaxRank(UpgradeType.PIERCING));

        // Rank 1
        assertTrue(manager.applyUpgrade(UpgradeType.PIERCING, player));
        assertEquals(1, player.getBonusPierce());
        assertEquals(1, manager.getRank(UpgradeType.PIERCING));
        assertFalse(manager.isMaxRank(UpgradeType.PIERCING));

        // Rank 2 (Max)
        assertTrue(manager.applyUpgrade(UpgradeType.PIERCING, player));
        assertEquals(2, player.getBonusPierce());
        assertEquals(2, manager.getRank(UpgradeType.PIERCING));
        assertTrue(manager.isMaxRank(UpgradeType.PIERCING));

        // 3rd attempt: MUST be rejected
        assertFalse(manager.applyUpgrade(UpgradeType.PIERCING, player), "3rd Piercing upgrade must be rejected");
        assertEquals(2, player.getBonusPierce(), "Player bonus pierce must stay clamped at 2");
        assertEquals(2, manager.getRank(UpgradeType.PIERCING));

        // 4th and 5th attempts: MUST also be rejected
        assertFalse(manager.applyUpgrade(UpgradeType.PIERCING, player));
        assertFalse(manager.applyUpgrade(UpgradeType.PIERCING, player));
        assertEquals(2, player.getBonusPierce());
    }

    @Test
    @DisplayName("Challenge 5.2: 6 Other Perks Max Rank 5 Caps and Mathematical Accumulation")
    public void testOtherSixPerksMaxRank5CapsAndMultipliers() {
        UpgradeManager manager = new UpgradeManager();
        Player player = new Player(new Vector2D(0, 0));

        // 1. MAX_HEALTH: +40 each, 5 ranks -> +200 total (250 -> 450)
        float initialHp = player.getMaxHealth();
        for (int i = 1; i <= 5; i++) {
            assertTrue(manager.applyUpgrade(UpgradeType.MAX_HEALTH, player));
            assertEquals(i, manager.getRank(UpgradeType.MAX_HEALTH));
            assertEquals(initialHp + i * 40.0f, player.getMaxHealth(), 1e-4f);
        }
        assertTrue(manager.isMaxRank(UpgradeType.MAX_HEALTH));
        assertFalse(manager.applyUpgrade(UpgradeType.MAX_HEALTH, player), "6th MAX_HEALTH must be rejected");
        assertEquals(450.0f, player.getMaxHealth(), 1e-4f);

        // 2. MOVE_SPEED: *1.12 each, 5 ranks -> 230 * (1.12)^5 = 405.3387
        float initialSpeed = player.getBaseMoveSpeed();
        for (int i = 1; i <= 5; i++) {
            assertTrue(manager.applyUpgrade(UpgradeType.MOVE_SPEED, player));
            assertEquals(i, manager.getRank(UpgradeType.MOVE_SPEED));
            float expectedSpeed = (float) (initialSpeed * Math.pow(1.12, i));
            assertEquals(expectedSpeed, player.getBaseMoveSpeed(), 1e-2f);
        }
        assertTrue(manager.isMaxRank(UpgradeType.MOVE_SPEED));
        assertFalse(manager.applyUpgrade(UpgradeType.MOVE_SPEED, player), "6th MOVE_SPEED must be rejected");

        // 3. DAMAGE: *1.20 each, 5 ranks -> 1.0 * (1.20)^5 = 2.48832
        for (int i = 1; i <= 5; i++) {
            assertTrue(manager.applyUpgrade(UpgradeType.DAMAGE, player));
            assertEquals(i, manager.getRank(UpgradeType.DAMAGE));
            float expectedDmg = (float) Math.pow(1.20, i);
            assertEquals(expectedDmg, player.getDamageMultiplier(), 1e-4f);
        }
        assertTrue(manager.isMaxRank(UpgradeType.DAMAGE));
        assertFalse(manager.applyUpgrade(UpgradeType.DAMAGE, player), "6th DAMAGE must be rejected");

        // 4. CAST_COOLDOWN: *0.88 each, 5 ranks -> 0.28 * (0.88)^5 = 0.147765
        float initialCd = player.getBaseCastCooldown();
        for (int i = 1; i <= 5; i++) {
            assertTrue(manager.applyUpgrade(UpgradeType.CAST_COOLDOWN, player));
            assertEquals(i, manager.getRank(UpgradeType.CAST_COOLDOWN));
            float expectedCd = (float) (initialCd * Math.pow(0.88, i));
            assertEquals(expectedCd, player.getBaseCastCooldown(), 1e-4f);
        }
        assertTrue(manager.isMaxRank(UpgradeType.CAST_COOLDOWN));
        assertFalse(manager.applyUpgrade(UpgradeType.CAST_COOLDOWN, player), "6th CAST_COOLDOWN must be rejected");

        // 5. PROJECTILE_SPEED: *1.20 each, 5 ranks -> 1.0 * (1.20)^5 = 2.48832
        for (int i = 1; i <= 5; i++) {
            assertTrue(manager.applyUpgrade(UpgradeType.PROJECTILE_SPEED, player));
            assertEquals(i, manager.getRank(UpgradeType.PROJECTILE_SPEED));
            float expectedSpeed = (float) Math.pow(1.20, i);
            assertEquals(expectedSpeed, player.getProjectileSpeedMultiplier(), 1e-4f);
        }
        assertTrue(manager.isMaxRank(UpgradeType.PROJECTILE_SPEED));
        assertFalse(manager.applyUpgrade(UpgradeType.PROJECTILE_SPEED, player), "6th PROJECTILE_SPEED must be rejected");

        // 6. PROJECTILE_LIFETIME: *1.25 each, 5 ranks -> 1.0 * (1.25)^5 = 3.0517578
        for (int i = 1; i <= 5; i++) {
            assertTrue(manager.applyUpgrade(UpgradeType.PROJECTILE_LIFETIME, player));
            assertEquals(i, manager.getRank(UpgradeType.PROJECTILE_LIFETIME));
            float expectedLifetime = (float) Math.pow(1.25, i);
            assertEquals(expectedLifetime, player.getProjectileLifetimeMultiplier(), 1e-4f);
        }
        assertTrue(manager.isMaxRank(UpgradeType.PROJECTILE_LIFETIME));
        assertFalse(manager.applyUpgrade(UpgradeType.PROJECTILE_LIFETIME, player), "6th PROJECTILE_LIFETIME must be rejected");
    }

    @Test
    @DisplayName("Challenge 5.3: Defensive Null Handling and State Isolation in UpgradeManager")
    public void testUpgradeManagerDefensiveBehavior() {
        UpgradeManager manager = new UpgradeManager();
        Player player = new Player(new Vector2D(0, 0));

        assertFalse(manager.applyUpgrade(null, player));
        assertFalse(manager.applyUpgrade(UpgradeType.DAMAGE, null));
        assertFalse(manager.applyUpgrade(null, null));

        assertEquals(0, manager.getRank(null));
        assertEquals(0, manager.getMaxRank(null));
        assertTrue(manager.isMaxRank(null));
        assertNull(manager.getInfo(null));

        // Reset resets all perks to rank 0
        manager.applyUpgrade(UpgradeType.DAMAGE, player);
        assertEquals(1, manager.getRank(UpgradeType.DAMAGE));
        manager.reset();
        assertEquals(0, manager.getRank(UpgradeType.DAMAGE));
        assertFalse(manager.isMaxRank(UpgradeType.DAMAGE));
    }

    // =========================================================================
    // 6. Fisher-Yates Draw Under Exhausted Conditions (2 unmaxed, 1 unmaxed, all maxed)
    // =========================================================================

    @Test
    @DisplayName("Challenge 6.1: Draw with Exactly 2 Unmaxed Perks (Returns exactly 2 cards, No Duplicates)")
    public void testDrawWithTwoUnmaxedPerks() {
        UpgradeManager manager = new UpgradeManager();
        Player dummy = new Player();

        // Max out 5 perks, leaving only MOVE_SPEED and PIERCING
        UpgradeType keep1 = UpgradeType.MOVE_SPEED;
        UpgradeType keep2 = UpgradeType.PIERCING;

        for (UpgradeType t : UpgradeType.values()) {
            if (t != keep1 && t != keep2) {
                for (int i = 0; i < 5; i++) {
                    manager.applyUpgrade(t, dummy);
                }
                assertTrue(manager.isMaxRank(t));
            }
        }

        Random rng = new Random(42);
        // Draw 500 times: must ALWAYS return exactly 2 cards, containing only keep1 and keep2
        for (int trial = 0; trial < 500; trial++) {
            List<UpgradeInfo> cards = manager.getAvailableUpgrades(3, rng);
            assertEquals(2, cards.size(), "When only 2 perks remain, exactly 2 cards must be returned");

            Set<UpgradeType> typesInHand = new HashSet<>();
            for (UpgradeInfo info : cards) {
                assertTrue(typesInHand.add(info.type), "Hand must contain mutually unique perks");
                assertTrue(info.type == keep1 || info.type == keep2, "Hand must only contain unmaxed perks");
            }
            assertEquals(2, typesInHand.size());
        }
    }

    @Test
    @DisplayName("Challenge 6.2: Draw with Exactly 1 Unmaxed Perk (Returns exactly 1 card)")
    public void testDrawWithOneUnmaxedPerk() {
        UpgradeManager manager = new UpgradeManager();
        Player dummy = new Player();

        // Max out 6 perks, leaving only PIERCING (rank 0 of 2)
        UpgradeType lonePerk = UpgradeType.PIERCING;

        for (UpgradeType t : UpgradeType.values()) {
            if (t != lonePerk) {
                for (int i = 0; i < 5; i++) {
                    manager.applyUpgrade(t, dummy);
                }
                assertTrue(manager.isMaxRank(t));
            }
        }

        Random rng = new Random(999);
        for (int trial = 0; trial < 500; trial++) {
            List<UpgradeInfo> cards = manager.getAvailableUpgrades(3, rng);
            assertEquals(1, cards.size(), "When only 1 perk remains, exactly 1 card must be returned");
            assertEquals(lonePerk, cards.get(0).type);
        }
    }

    @Test
    @DisplayName("Challenge 6.3: Draw When All 7 Perks Are Fully Maxed (Returns Empty Cleanly, Zero Errors)")
    public void testDrawWhenAllPerksMaxed() {
        UpgradeManager manager = new UpgradeManager();
        Player dummy = new Player();

        // Max out all 7 perks: 6 perks * 5 ranks + 1 perk * 2 ranks = 32 upgrades
        for (UpgradeType t : UpgradeType.values()) {
            int max = (t == UpgradeType.PIERCING) ? 2 : 5;
            for (int i = 0; i < max; i++) {
                assertTrue(manager.applyUpgrade(t, dummy));
            }
            assertTrue(manager.isMaxRank(t));
        }

        Random rng = new Random(777);
        // Repeated draws requesting 3, 1, or 7 cards must cleanly return an empty list
        for (int trial = 0; trial < 500; trial++) {
            List<UpgradeInfo> cards3 = manager.getAvailableUpgrades(3, rng);
            assertNotNull(cards3);
            assertTrue(cards3.isEmpty(), "Draw under complete exhaustion must return empty list");

            List<UpgradeInfo> cards1 = manager.getAvailableUpgrades(1, rng);
            assertNotNull(cards1);
            assertTrue(cards1.isEmpty());

            List<UpgradeInfo> cards7 = manager.getAvailableUpgrades(7, rng);
            assertNotNull(cards7);
            assertTrue(cards7.isEmpty());
        }
    }

    @Test
    @DisplayName("Challenge 6.4: Drawn Cards Immutability Against Manager Internal State")
    public void testDrawnCardsImmutability() {
        UpgradeManager manager = new UpgradeManager();
        List<UpgradeInfo> drawn = manager.getAvailableUpgrades(3);
        assertFalse(drawn.isEmpty());

        UpgradeInfo card = drawn.get(0);
        UpgradeType type = card.type;
        assertEquals(0, manager.getRank(type));

        // Mutating the returned copy must not change internal manager rank
        card.currentRank = 99;
        assertEquals(0, manager.getRank(type), "Manager internal rank must not be corrupted by returned copy mutation");
    }

    // =========================================================================
    // 7. Full Integration Stress: High-Volume Upgrade & Progression Lifecycle
    // =========================================================================

    @Test
    @DisplayName("Challenge 7.1: High-Volume E2E Progression & Perk Exhaustion Loop")
    public void testFullProgressionAndPerkExhaustionLoop() {
        ExperienceSystem expSystem = new ExperienceSystem();
        PlayerProgression progression = new PlayerProgression(1);
        UpgradeManager upgradeManager = new UpgradeManager();
        Player player = new Player(new Vector2D(500f, 500f));
        player.setPickupRadius(50.0f);

        Random rng = new Random(1337);
        int totalUpgradesApplied = 0;

        // Loop until all 32 perk upgrades are collected
        // Total perks: 6 * 5 + 2 = 32 ranks
        while (totalUpgradesApplied < 32) {
            // Spawn high value gem near player
            expSystem.spawnPickup(500f, 520f, 150);
            expSystem.update(0.016f, player, progression);

            while (progression.hasPendingLevelUp()) {
                List<UpgradeInfo> choices = upgradeManager.getAvailableUpgrades(3, rng);
                if (choices.isEmpty()) {
                    break;
                }
                // Pick a random unmaxed choice
                UpgradeInfo pick = choices.get(rng.nextInt(choices.size()));
                boolean applied = upgradeManager.applyUpgrade(pick.type, player);
                assertTrue(applied, "Available perk choice must be applicable");
                totalUpgradesApplied++;
                progression.consumeLevelUp();
            }
        }

        assertEquals(32, totalUpgradesApplied, "All 32 perks must be successfully applied");
        for (UpgradeType t : UpgradeType.values()) {
            assertTrue(upgradeManager.isMaxRank(t), "Perk must be maxed: " + t);
        }

        // Verify player final stats
        assertEquals(2, player.getBonusPierce());
        assertEquals(450.0f, player.getMaxHealth(), 1e-4f);
        assertTrue(player.getDamageMultiplier() > 2.4f);
        assertTrue(player.getBaseMoveSpeed() > 400.0f);
        assertTrue(player.getBaseCastCooldown() < 0.16f);

        // Subsequent draws return empty
        List<UpgradeInfo> emptyChoices = upgradeManager.getAvailableUpgrades(3, rng);
        assertTrue(emptyChoices.isEmpty(), "Subsequent draws after exhaustion must be empty");
    }
}
