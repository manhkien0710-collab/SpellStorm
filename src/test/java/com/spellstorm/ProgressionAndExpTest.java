package com.spellstorm;

import com.spellstorm.assets.AssetRegistry;
import com.spellstorm.entities.ExperiencePickup;
import com.spellstorm.entities.Player;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.ExperienceSystem;
import com.spellstorm.systems.PlayerProgression;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 6: Experience Gems & Progression Curve Tests")
public class ProgressionAndExpTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("EXP Gem Tiers: Cyan (<15), Emerald (15-29), Gold (>=30)")
    public void testGemTiersAndValues() {
        // Cyan gems (< 15)
        ExperiencePickup p1 = new ExperiencePickup(100f, 100f, 10);
        assertEquals(10, p1.getExpValue());
        assertEquals(AssetRegistry.TextureId.GEM_CYAN, p1.getTextureId());
        assertEquals(60f / 255f, p1.getGemColor().r, 1e-3f);
        assertEquals(200f / 255f, p1.getGemColor().g, 1e-3f);
        assertEquals(255f / 255f, p1.getGemColor().b, 1e-3f);
        assertFalse(p1.isCollected());
        assertTrue(p1.isAlive());

        ExperiencePickup pCyanMax = new ExperiencePickup(100f, 100f, 14);
        assertEquals(AssetRegistry.TextureId.GEM_CYAN, pCyanMax.getTextureId());

        // Emerald gems (15 <= exp < 30)
        ExperiencePickup pEmeraldMin = new ExperiencePickup(150f, 150f, 15);
        assertEquals(15, pEmeraldMin.getExpValue());
        assertEquals(AssetRegistry.TextureId.GEM_EMERALD, pEmeraldMin.getTextureId());
        assertEquals(50f / 255f, pEmeraldMin.getGemColor().r, 1e-3f);
        assertEquals(240f / 255f, pEmeraldMin.getGemColor().g, 1e-3f);
        assertEquals(150f / 255f, pEmeraldMin.getGemColor().b, 1e-3f);

        ExperiencePickup pEmerald25 = new ExperiencePickup(150f, 150f, 25);
        assertEquals(25, pEmerald25.getExpValue());
        assertEquals(AssetRegistry.TextureId.GEM_EMERALD, pEmerald25.getTextureId());

        ExperiencePickup pEmeraldMax = new ExperiencePickup(150f, 150f, 29);
        assertEquals(AssetRegistry.TextureId.GEM_EMERALD, pEmeraldMax.getTextureId());

        // Gold gems (>= 30)
        ExperiencePickup pGoldMin = new ExperiencePickup(200f, 200f, 30);
        assertEquals(30, pGoldMin.getExpValue());
        assertEquals(AssetRegistry.TextureId.GEM_GOLD, pGoldMin.getTextureId());
        assertEquals(255f / 255f, pGoldMin.getGemColor().r, 1e-3f);
        assertEquals(210f / 255f, pGoldMin.getGemColor().g, 1e-3f);
        assertEquals(50f / 255f, pGoldMin.getGemColor().b, 1e-3f);

        ExperiencePickup pGold75 = new ExperiencePickup(200f, 200f, 75);
        assertEquals(75, pGold75.getExpValue());
        assertEquals(AssetRegistry.TextureId.GEM_GOLD, pGold75.getTextureId());

        ExperiencePickup pBoss250 = new ExperiencePickup(200f, 200f, 250);
        assertEquals(250, pBoss250.getExpValue());
        assertEquals(AssetRegistry.TextureId.GEM_GOLD, pBoss250.getTextureId());
    }

    @Test
    @DisplayName("Gem Dimensions, Idempotent Collection, and Sinusoidal Bobbing")
    public void testGemBobbingAndCollection() {
        ExperiencePickup gem = new ExperiencePickup(100f, 200f, 10);
        assertEquals(8.0f, gem.getRadius(), 1e-4f);
        assertEquals(200f, gem.getBaseY(), 1e-4f);

        // Sinusoidal bobbing formula: sin(t * 5.0) * 3.0
        assertEquals(0.0f, gem.getBobOffset(), 1e-4f);
        assertEquals(200.0f, gem.getRenderY(), 1e-4f);

        float quarterCycle = (float) (Math.PI / 10.0); // t = pi / (2 * 5)
        gem.update(quarterCycle);
        assertEquals(3.0f, gem.getBobOffset(), 1e-2f);
        assertEquals(203.0f, gem.getRenderY(), 1e-2f);
        // Base ground anchor does not bob
        assertEquals(200.0f, gem.getBaseY(), 1e-4f);

        // Idempotent collection
        assertFalse(gem.isCollected());
        assertTrue(gem.isAlive());
        gem.collect();
        assertTrue(gem.isCollected());
        assertFalse(gem.isAlive());

        // Second collection does not throw and remains collected
        gem.collect();
        assertTrue(gem.isCollected());
        assertFalse(gem.isAlive());
    }

    @Test
    @DisplayName("ExperienceSystem: Accelerating Magnet Pull & Collection Thresholds")
    public void testMagnetPullAndCollection() {
        ExperienceSystem expSystem = new ExperienceSystem();
        PlayerProgression progression = new PlayerProgression(1);
        Player player = new Player(new Vector2D(500f, 500f));
        player.setPickupRadius(45.0f);

        // 1. Gem outside magnet radius: 45 * 2.8 = 126px
        // Distance = 200px > 126px
        expSystem.spawnPickup(500f, 700f, 10);
        assertEquals(1, expSystem.getPickupCount());
        expSystem.update(0.1f, player, progression);
        assertEquals(1, expSystem.getPickupCount());
        assertEquals(700f, expSystem.getPickups().get(0).getPosition().y, 1e-4f);
        assertEquals(0, progression.getCurrentExp());

        // 2. Gem within magnet radius (distance = 100px < 126px)
        expSystem.clear();
        expSystem.spawnPickup(500f, 600f, 10); // dist = 100px
        float dist = 100.0f;
        float expectedPullSpeed = 240.0f + (126.0f - dist) * 2.5f; // 240 + 26 * 2.5 = 305.0 px/s
        expSystem.update(0.1f, player, progression);
        // Position moves towards player (Y decreases from 600 towards 500)
        float newY = expSystem.getPickups().get(0).getPosition().y;
        float expectedY = 600.0f - (expectedPullSpeed * 0.1f);
        assertEquals(expectedY, newY, 0.5f);
        assertTrue(newY < 600.0f);

        // 3. Gem within collection radius (distance = 30px <= 45px)
        expSystem.clear();
        expSystem.spawnPickup(500f, 530f, 25);
        assertEquals(1, expSystem.getPickupCount());
        expSystem.update(0.016f, player, progression);
        // Gem collected and awarded
        assertEquals(0, expSystem.getPickupCount());
        assertEquals(0, progression.getCurrentExp()); // 25 EXP consumed for level 1 -> 2
        assertEquals(2, progression.getLevel()); // 25 EXP leveled up 1 -> 2
    }

    @Test
    @DisplayName("Progression Curve: 25 + (level - 1) * 20 exact thresholds")
    public void testLevelCurveFormula() {
        assertEquals(25, PlayerProgression.expRequiredForLevel(1));
        assertEquals(45, PlayerProgression.expRequiredForLevel(2));
        assertEquals(65, PlayerProgression.expRequiredForLevel(3));
        assertEquals(85, PlayerProgression.expRequiredForLevel(4));
        assertEquals(105, PlayerProgression.expRequiredForLevel(5));
        assertEquals(125, PlayerProgression.expRequiredForLevel(6));
        assertEquals(205, PlayerProgression.expRequiredForLevel(10));
        // Non-positive clamped to level 1
        assertEquals(25, PlayerProgression.expRequiredForLevel(0));
        assertEquals(25, PlayerProgression.expRequiredForLevel(-5));
    }

    @Test
    @DisplayName("Progression Sub-threshold, Exact-threshold, and ProgressRatio")
    public void testSubThresholdAndProgressRatio() {
        PlayerProgression prog = new PlayerProgression(1);
        assertEquals(1, prog.getLevel());
        assertEquals(0, prog.getCurrentExp());
        assertEquals(25, prog.getExpForNextLevel());
        assertFalse(prog.hasPendingLevelUp());
        assertEquals(0.0f, prog.getProgressRatio(), 1e-4f);

        // Sub-threshold
        int gained = prog.addExp(20);
        assertEquals(0, gained);
        assertEquals(1, prog.getLevel());
        assertEquals(20, prog.getCurrentExp());
        assertEquals(25, prog.getExpForNextLevel());
        assertFalse(prog.hasPendingLevelUp());
        assertEquals(20.0f / 25.0f, prog.getProgressRatio(), 1e-4f);

        // Exact threshold to level up
        gained = prog.addExp(5);
        assertEquals(1, gained);
        assertEquals(2, prog.getLevel());
        assertEquals(0, prog.getCurrentExp());
        assertEquals(45, prog.getExpForNextLevel());
        assertTrue(prog.hasPendingLevelUp());
        assertEquals(1, prog.getPendingLevelUps());
        assertEquals(0.0f, prog.getProgressRatio(), 1e-4f);

        prog.consumeLevelUp();
        assertFalse(prog.hasPendingLevelUp());
        assertEquals(0, prog.getPendingLevelUps());
    }

    @Test
    @DisplayName("Excess EXP Carryover and Multi-Level Up Queue (145 EXP -> Level 4)")
    public void testExcessExpAndMultiLevelUp() {
        PlayerProgression prog = new PlayerProgression(1);

        // Level 1: 25 needed (rem: 120) -> Level 2
        // Level 2: 45 needed (rem: 75)  -> Level 3
        // Level 3: 65 needed (rem: 10)  -> Level 4
        // Level 4: 85 needed (curExp = 10 < 85)
        int gained = prog.addExp(145);
        assertEquals(3, gained);
        assertEquals(4, prog.getLevel());
        assertEquals(10, prog.getCurrentExp());
        assertEquals(85, prog.getExpForNextLevel());
        assertEquals(3, prog.getPendingLevelUps());
        assertTrue(prog.hasPendingLevelUp());

        // Consume queue one by one
        prog.consumeLevelUp();
        assertEquals(2, prog.getPendingLevelUps());
        assertTrue(prog.hasPendingLevelUp());

        prog.consumeLevelUp();
        assertEquals(1, prog.getPendingLevelUps());
        assertTrue(prog.hasPendingLevelUp());

        prog.consumeLevelUp();
        assertEquals(0, prog.getPendingLevelUps());
        assertFalse(prog.hasPendingLevelUp());

        // Extra consume does not go negative
        prog.consumeLevelUp();
        assertEquals(0, prog.getPendingLevelUps());
    }

    @Test
    @DisplayName("Defensive Behavior: Non-positive EXP and Reset")
    public void testDefensiveBehavior() {
        PlayerProgression prog = new PlayerProgression(2);
        assertEquals(2, prog.getLevel());

        assertEquals(0, prog.addExp(0));
        assertEquals(0, prog.addExp(-100));
        assertEquals(2, prog.getLevel());
        assertEquals(0, prog.getCurrentExp());

        prog.reset(1);
        assertEquals(1, prog.getLevel());
        assertEquals(0, prog.getCurrentExp());
        assertEquals(0, prog.getPendingLevelUps());
    }

    @Test
    @DisplayName("Tier 1 (Feature 27): Gem texture and RGB color mapping per tier")
    public void testGemTextureAndColorMapping() {
        // Cyan Gem (< 15)
        ExperiencePickup cyan = new ExperiencePickup(new Vector2D(0, 0), 10);
        assertEquals(AssetRegistry.TextureId.GEM_CYAN, cyan.getTextureId());
        assertEquals(ExperiencePickup.CYAN_GEM_COLOR.r, cyan.getGemColor().r, 1e-4f);
        assertEquals(ExperiencePickup.CYAN_GEM_COLOR.g, cyan.getGemColor().g, 1e-4f);
        assertEquals(ExperiencePickup.CYAN_GEM_COLOR.b, cyan.getGemColor().b, 1e-4f);

        // Emerald Gem (15-29)
        ExperiencePickup emerald = new ExperiencePickup(new Vector2D(0, 0), 25);
        assertEquals(AssetRegistry.TextureId.GEM_EMERALD, emerald.getTextureId());
        assertEquals(ExperiencePickup.EMERALD_GEM_COLOR.r, emerald.getGemColor().r, 1e-4f);
        assertEquals(ExperiencePickup.EMERALD_GEM_COLOR.g, emerald.getGemColor().g, 1e-4f);
        assertEquals(ExperiencePickup.EMERALD_GEM_COLOR.b, emerald.getGemColor().b, 1e-4f);

        // Gold Gem (>= 30)
        ExperiencePickup gold = new ExperiencePickup(new Vector2D(0, 0), 100);
        assertEquals(AssetRegistry.TextureId.GEM_GOLD, gold.getTextureId());
        assertEquals(ExperiencePickup.GOLD_GEM_COLOR.r, gold.getGemColor().r, 1e-4f);
        assertEquals(ExperiencePickup.GOLD_GEM_COLOR.g, gold.getGemColor().g, 1e-4f);
        assertEquals(ExperiencePickup.GOLD_GEM_COLOR.b, gold.getGemColor().b, 1e-4f);
    }

    @Test
    @DisplayName("Tier 2 (Feature 27): Magnet pull distance threshold boundary and collection threshold")
    public void testMagnetPullDistanceThresholdBoundary() {
        ExperienceSystem expSystem = new ExperienceSystem();
        Player player = new Player(new Vector2D(1000.0f, 1000.0f), 250f, 30f, 230f, 45f);
        PlayerProgression prog = new PlayerProgression(1);

        // Player pickup radius is 45.0f, magnet radius is 45.0f * 2.80f = 126.0f
        float magnetRadius = 45.0f * ExperienceSystem.MAGNET_RADIUS_MULTIPLIER; // 126.0f

        // Case 1: Exact magnet boundary (126.0f) -> magnet is ACTIVE, gem position moves towards player
        expSystem.spawnPickup(1000.0f + magnetRadius, 1000.0f, 10);
        expSystem.update(0.016f, player, prog);
        assertEquals(1, expSystem.getPickupCount());
        ExperiencePickup gemActive = expSystem.getPickups().get(0);
        assertTrue(gemActive.getPosition().x < 1000.0f + magnetRadius, "Gem at 126.0f must be pulled inward by magnet");

        // Case 2: Just outside magnet boundary (126.1f) -> magnet is INACTIVE, gem stays in place
        expSystem.clear();
        expSystem.spawnPickup(1000.0f + magnetRadius + 0.1f, 1000.0f, 10);
        expSystem.update(0.016f, player, prog);
        assertEquals(1, expSystem.getPickupCount());
        ExperiencePickup gemInactive = expSystem.getPickups().get(0);
        assertEquals(1000.0f + magnetRadius + 0.1f, gemInactive.getPosition().x, 1e-4f,
                "Gem at 126.1f must remain still (outside magnet radius)");

        // Case 3: Collection boundary (45.0f) -> collected and exp awarded
        expSystem.clear();
        expSystem.spawnPickup(1000.0f + 45.0f, 1000.0f, 10);
        expSystem.update(0.016f, player, prog);
        assertEquals(0, expSystem.getPickupCount(), "Gem at 45.0f must be collected and pruned");
        assertEquals(10, prog.getCurrentExp(), "Collected gem must award EXP to progression");
    }
}
