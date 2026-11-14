package com.spellstorm.p3;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.assets.AssetRegistry.TextureId;
import com.spellstorm.assets.FontManager;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.assets.TextureManager.CompleteSheetId;
import com.spellstorm.assets.TextureManager.ExtendedSheetId;
import com.spellstorm.assets.TextureManager.SpriteSheetId;
import com.spellstorm.combat.Element;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.core.EngineConfig;
import com.spellstorm.core.GameScreen;
import com.spellstorm.core.GameScreen.DepthSortedEntry;
import com.spellstorm.core.GameScreen.RenderableKind;
import com.spellstorm.core.GameSession;
import com.spellstorm.core.SpellStormGame;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.EnemyType;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Player;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.PowerUpType;
import com.spellstorm.ui.HudRenderer;
import com.spellstorm.world.DecoType;
import com.spellstorm.world.Obstacle;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileType;
import com.spellstorm.world.WorldGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(HeadlessTestRunner.class)
@DisplayName("Milestone 4 Complete Graphics Pipeline Integration Tests")
public class GraphicsPipelineCompleteTest {

    private static final Map<CompleteSheetId, int[]> EXPECTED_COMPLETE_SHEETS = Map.ofEntries(
            Map.entry(CompleteSheetId.PLAYER_HURT_DEATH_ANIM, new int[]{192, 32, 32, 32, 6}),
            Map.entry(CompleteSheetId.CATALYST_ORB_ANIM, new int[]{96, 16, 16, 16, 6}),
            Map.entry(CompleteSheetId.RANGED_CULTIST_ANIM, new int[]{256, 32, 32, 32, 8}),
            Map.entry(CompleteSheetId.TANK_GOLEM_ANIM, new int[]{384, 48, 48, 48, 8}),
            Map.entry(CompleteSheetId.BOSS_COLOSSUS_ANIM, new int[]{512, 64, 64, 64, 8}),
            Map.entry(CompleteSheetId.WATER_ANIM, new int[]{128, 32, 32, 32, 4}),
            Map.entry(CompleteSheetId.TERRAIN_VARIANTS, new int[]{256, 32, 32, 32, 8}),
            Map.entry(CompleteSheetId.TERRAIN_TRANSITIONS, new int[]{256, 32, 32, 32, 8}),
            Map.entry(CompleteSheetId.SPELL_ICE_ANIM, new int[]{144, 24, 24, 24, 6}),
            Map.entry(CompleteSheetId.SPELL_LIGHTNING_ANIM, new int[]{144, 24, 24, 24, 6}),
            Map.entry(CompleteSheetId.SPELL_WIND_ANIM, new int[]{144, 24, 24, 24, 6}),
            Map.entry(CompleteSheetId.SPELL_EARTH_ANIM, new int[]{144, 24, 24, 24, 6}),
            Map.entry(CompleteSheetId.PROJECTILE_PLAYER_ANIM, new int[]{144, 24, 24, 24, 6}),
            Map.entry(CompleteSheetId.PROJECTILE_ENEMY_ANIM, new int[]{144, 24, 24, 24, 6}),
            Map.entry(CompleteSheetId.VFX_ICE_IMPACT, new int[]{192, 32, 32, 32, 6}),
            Map.entry(CompleteSheetId.VFX_LIGHTNING_IMPACT, new int[]{192, 32, 32, 32, 6}),
            Map.entry(CompleteSheetId.VFX_WIND_IMPACT, new int[]{192, 32, 32, 32, 6}),
            Map.entry(CompleteSheetId.VFX_EARTH_IMPACT, new int[]{192, 32, 32, 32, 6}),
            Map.entry(CompleteSheetId.VFX_ARCANE_IMPACT, new int[]{192, 32, 32, 32, 6}),
            Map.entry(CompleteSheetId.GEMS_ANIM, new int[]{192, 16, 16, 16, 12}),
            Map.entry(CompleteSheetId.HUD_ELEMENTS, new int[]{96, 16, 16, 16, 6}),
            Map.entry(CompleteSheetId.POWERUP_ICONS, new int[]{48, 16, 16, 16, 3})
    );

    @Test
    @DisplayName("All 36 TextureId and all 31 SpriteSheetId sheets load with Nearest filtering and exact slicing")
    void testAllTexturesAnd31SpriteSheetsLoadCleanly() {
        assertEquals(36, TextureId.values().length, "Canonical TextureId count must stay strictly at 36");
        assertEquals(9, ExtendedSheetId.values().length, "M2 ExtendedSheetId count must remain 9");
        assertEquals(22, CompleteSheetId.values().length, "M3/M4 CompleteSheetId count must be 22");
        assertEquals(31, TextureManager.getAllSheetIds().size(), "Combined SpriteSheetId count must be 31");

        TextureManager tm = new TextureManager();
        try {
            tm.loadAll();

            for (TextureId texId : TextureId.values()) {
                Texture tex = tm.getTexture(texId);
                assertNotNull(tex, "Canonical texture must load: " + texId.name());
                assertEquals(TextureFilter.Nearest, tex.getMinFilter(), "MinFilter Nearest required for " + texId);
                assertEquals(TextureFilter.Nearest, tex.getMagFilter(), "MagFilter Nearest required for " + texId);
            }

            for (CompleteSheetId id : CompleteSheetId.values()) {
                int[] spec = EXPECTED_COMPLETE_SHEETS.get(id);
                assertNotNull(spec, "Expected spec missing for " + id);

                assertTrue(tm.hasExtendedSheet(id), "Complete sheet must load: " + id.name());
                assertTrue(tm.hasSheet(id), "Unified hasSheet must match for " + id.name());

                Texture tex = tm.getExtendedTexture(id);
                assertNotNull(tex, "Texture must not be null for " + id.name());
                assertSame(tex, tm.getSheetTexture(id));
                assertEquals(TextureFilter.Nearest, tex.getMinFilter(), "MinFilter Nearest required for " + id.name());
                assertEquals(TextureFilter.Nearest, tex.getMagFilter(), "MagFilter Nearest required for " + id.name());

                assertEquals(spec[0], tex.getWidth(), "Width mismatch for " + id.name());
                assertEquals(spec[1], tex.getHeight(), "Height mismatch for " + id.name());
                assertEquals(spec[2], id.getFrameWidth(), "Frame width mismatch for " + id.name());
                assertEquals(spec[3], id.getFrameHeight(), "Frame height mismatch for " + id.name());
                assertEquals(spec[4], id.getTotalFrames(), "Total frames mismatch for " + id.name());

                for (int f = 0; f < spec[4]; f++) {
                    TextureRegion region = tm.getExtendedFrame(id, f);
                    assertNotNull(region, "Frame " + f + " must not be null for " + id.name());
                    assertSame(region, tm.getSheetFrame(id, f));
                    assertEquals(f * spec[2], region.getRegionX(), "Frame " + f + " X offset mismatch for " + id.name());
                    assertEquals(0, region.getRegionY(), "Frame " + f + " Y offset mismatch for " + id.name());
                    assertEquals(spec[2], region.getRegionWidth(), "Frame width mismatch for " + id.name());
                    assertEquals(spec[3], region.getRegionHeight(), "Frame height mismatch for " + id.name());
                }

                assertSame(tm.getExtendedFrame(id, 0), tm.getExtendedFrame(id, -4));
                assertSame(tm.getExtendedFrame(id, 1), tm.getExtendedFrame(id, spec[4] + 1));
            }

            // Null safety on unified API
            assertFalse(tm.hasSheet((SpriteSheetId) null));
            assertNull(tm.getSheetTexture(null));
            assertNull(tm.getSheetFrame(null, 0));
            assertFalse(tm.hasExtendedSheet((CompleteSheetId) null));
            assertNull(tm.getExtendedTexture((CompleteSheetId) null));
            assertNull(tm.getExtendedFrame((CompleteSheetId) null, 0));
        } finally {
            tm.dispose();
            assertFalse(tm.hasExtendedSheet(CompleteSheetId.BOSS_COLOSSUS_ANIM));
            assertFalse(tm.hasSheet(ExtendedSheetId.PLAYER_DOWN_ANIM));
            assertDoesNotThrow(tm::dispose);
        }
    }

    @Test
    @DisplayName("TileMap variant selection is deterministic and biome transitions never overlap bridges")
    void testTileMapVariantsAndTransitionsRespectBridges() {
        // Deterministic terrain variant hash coverage
        Set<Integer> grassVariants = new HashSet<>();
        Set<Integer> dirtVariants = new HashSet<>();
        Set<Integer> stoneVariants = new HashSet<>();
        Set<Integer> sandVariants = new HashSet<>();

        for (int ty = 0; ty < 12; ty++) {
            for (int tx = 0; tx < 12; tx++) {
                int g = TileMap.selectTerrainVariantFrame(TileType.GRASS, tx, ty);
                int d = TileMap.selectTerrainVariantFrame(TileType.DIRT, tx, ty);
                int s = TileMap.selectTerrainVariantFrame(TileType.STONE, tx, ty);
                int sa = TileMap.selectTerrainVariantFrame(TileType.SAND, tx, ty);

                assertEquals(g, TileMap.selectTerrainVariantFrame(TileType.GRASS, tx, ty));
                assertEquals(d, TileMap.selectTerrainVariantFrame(TileType.DIRT, tx, ty));
                assertTrue(g == -1 || (g >= 0 && g <= 2));
                assertTrue(d == -1 || (d >= 3 && d <= 5));
                assertTrue(s == -1 || s == 6);
                assertTrue(sa == -1 || sa == 7);
                assertEquals(-1, TileMap.selectTerrainVariantFrame(TileType.WATER, tx, ty));
                assertEquals(-1, TileMap.selectTerrainVariantFrame(TileType.BRIDGE_H, tx, ty));
                assertEquals(-1, TileMap.selectTerrainVariantFrame(TileType.BRIDGE_V, tx, ty));

                grassVariants.add(g);
                dirtVariants.add(d);
                stoneVariants.add(s);
                sandVariants.add(sa);
            }
        }
        assertTrue(grassVariants.containsAll(List.of(-1, 0, 1, 2)), "Must emit base and Grass variants 0..2");
        assertTrue(dirtVariants.containsAll(List.of(-1, 3, 4, 5)), "Must emit base and Dirt variants 3..5");
        assertTrue(stoneVariants.containsAll(List.of(-1, 6)), "Must emit base and Stone variant 6");
        assertTrue(sandVariants.containsAll(List.of(-1, 7)), "Must emit base and Sand variant 7");

        // Transition mask isolation + bridge non-overlap
        TileMap map = new TileMap();
        for (int ty = 0; ty < TileMap.HEIGHT; ty++) {
            for (int tx = 0; tx < TileMap.WIDTH; tx++) {
                map.setTile(tx, ty, TileType.WATER);
            }
        }
        // Create 3x3 grass/dirt + bridge strip across river
        map.setTile(10, 10, TileType.DIRT);
        map.setTile(10, 9, TileType.GRASS);   // N
        map.setTile(11, 10, TileType.GRASS);  // E
        map.setTile(10, 11, TileType.SAND);   // S (non-grass -> no overhang)
        map.setTile(9, 10, TileType.GRASS);   // W

        assertEquals(TileMap.DIR_N | TileMap.DIR_E | TileMap.DIR_W, map.getGrassOverhangMask(10, 10));
        assertEquals(0, map.getGrassOverhangMask(10, 9), "GRASS tiles never receive grass overhang overlays");
        assertEquals(0, map.getShoreTransitionMask(10, 10), "Non-WATER tiles never receive shore overlays");

        // Water bordering non-water non-bridge gets shore fringe, whereas water bordering bridge gets ZERO shore on that edge
        map.setTile(20, 20, TileType.WATER);
        map.setTile(20, 19, TileType.SAND);      // N land -> shore N
        map.setTile(21, 20, TileType.BRIDGE_H);  // E bridge -> NO shore E
        map.setTile(20, 21, TileType.WATER);     // S water -> NO shore S
        map.setTile(19, 20, TileType.BRIDGE_V);  // W bridge -> NO shore W
        assertEquals(TileMap.DIR_N, map.getShoreTransitionMask(20, 20),
                "Shoreline fringe must appear on SAND border only and NEVER beside BRIDGE_H/BRIDGE_V");

        // Bridge tiles themselves must NEVER get grass overhang or shore masks
        assertEquals(0, map.getGrassOverhangMask(21, 20));
        assertEquals(0, map.getShoreTransitionMask(21, 20));
        assertEquals(0, map.getGrassOverhangMask(19, 20));
        assertEquals(0, map.getShoreTransitionMask(19, 20));

        // Verify full WorldGenerator river bridges have zero shoreline masks on any bridge tile or water edge directly bordering bridges
        TileMap worldMap = new TileMap();
        WorldGenerator.generate(worldMap, 42L);
        int verifiedBridges = 0;
        for (int ty = 1; ty < TileMap.HEIGHT - 1; ty++) {
            for (int tx = 1; tx < TileMap.WIDTH - 1; tx++) {
                TileType t = worldMap.tileAt(tx, ty);
                if (t == TileType.BRIDGE_H || t == TileType.BRIDGE_V) {
                    verifiedBridges++;
                    assertEquals(0, worldMap.getGrassOverhangMask(tx, ty));
                    assertEquals(0, worldMap.getShoreTransitionMask(tx, ty));
                    if (worldMap.tileAt(tx, ty - 1) == TileType.WATER) {
                        assertEquals(0, worldMap.getShoreTransitionMask(tx, ty - 1) & TileMap.DIR_S);
                    }
                    if (worldMap.tileAt(tx, ty + 1) == TileType.WATER) {
                        assertEquals(0, worldMap.getShoreTransitionMask(tx, ty + 1) & TileMap.DIR_N);
                    }
                }
            }
        }
        assertTrue(verifiedBridges > 0, "Generated world must contain bridges to verify");
    }

    @Test
    @DisplayName("Unified Y-depth sorting places player behind tree canopy when north and in front when south")
    void testYDepthSortOrderWithTreeAndActors() {
        Obstacle oak = new Obstacle(DecoType.TREE_OAK, new Vector2D(300.0f, 300.0f), true);
        Enemy fiend = new MeleeEnemy(new Vector2D(320.0f, 295.0f));
        Player player = new Player(new Vector2D(300.0f, 260.0f), 100f, 0f, 200f, 16f);

        // Player north of tree trunk (260 + 16*0.85 = 273.6 < 300.0): draws BEFORE tree
        List<DepthSortedEntry> northOrder = GameScreen.buildYDepthSortedEntries(
                List.of(oak), List.of(fiend), player, null);
        assertEquals(3, northOrder.size());
        assertEquals(RenderableKind.PLAYER, northOrder.get(0).getKind(),
                "Player north of tree trunk must render before tree so canopy overlaps player");
        assertEquals(RenderableKind.OBSTACLE, northOrder.get(1).getKind());
        assertEquals(RenderableKind.ENEMY, northOrder.get(2).getKind(),
                "Enemy at anchor 295 + 13.6 = 308.6 > 300.0 must render in front of tree");

        // Move player south of tree base (310 + 13.6 = 323.6 > 300.0): draws AFTER tree & fiend
        player.setPosition(300.0f, 310.0f);
        List<DepthSortedEntry> southOrder = GameScreen.buildYDepthSortedEntries(
                List.of(oak), List.of(fiend), player, null);
        assertEquals(RenderableKind.OBSTACLE, southOrder.get(0).getKind());
        assertEquals(RenderableKind.ENEMY, southOrder.get(1).getKind());
        assertEquals(RenderableKind.PLAYER, southOrder.get(2).getKind(),
                "Player south of tree trunk must render after tree so player walks in front");
    }

    @Test
    @DisplayName("HudRenderer icon mapping, null-safe fallback, and real TextureManager icon rendering execute cleanly")
    void testHudRendererIconPathAndNullFallback() {
        HudRenderer hud = new HudRenderer();
        assertNull(hud.getTextureManager());

        // Element & PowerUp frame mapping parity
        assertEquals(0, HudRenderer.getElementIconFrameIndex(Element.FIRE));
        assertEquals(1, HudRenderer.getElementIconFrameIndex(Element.ICE));
        assertEquals(2, HudRenderer.getElementIconFrameIndex(Element.LIGHTNING));
        assertEquals(3, HudRenderer.getElementIconFrameIndex(Element.WIND));
        assertEquals(4, HudRenderer.getElementIconFrameIndex(Element.EARTH));
        assertEquals(5, HudRenderer.getElementIconFrameIndex(null));

        assertEquals(0, HudRenderer.getPowerUpIconFrameIndex(PowerUpType.ARCANE_OVERDRIVE));
        assertEquals(1, HudRenderer.getPowerUpIconFrameIndex(PowerUpType.PIERCING_SURGE));
        assertEquals(2, HudRenderer.getPowerUpIconFrameIndex(PowerUpType.ELEMENTAL_BURST));

        GameSession session = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 42L, false);
        session.startMatch();
        session.infuseElement(Element.FIRE);
        session.infuseElement(Element.ICE);
        session.getPowerUpManager().getActiveAbility().activate(PowerUpType.ARCANE_OVERDRIVE);

        // Null TextureManager fallback path
        assertDoesNotThrow(() -> hud.renderHUD(
                null, null, null,
                session.getPlayer(), session.getProgression(),
                session.getPowerUpManager(), session.getEnemies(), session.getEnemiesDefeated(),
                session.getCurrentSpell(), session.getActiveMix(),
                session.getMatchElapsed(), 42L, false, true
        ));

        TextureManager tm = new TextureManager();
        FontManager fm = new FontManager();
        try {
            tm.loadAll();
            fm.initialize();
            hud.setTextureManager(tm);
            assertSame(tm, hud.getTextureManager());

            for (Element elem : Element.values()) {
                TextureRegion r = tm.getExtendedFrame(CompleteSheetId.HUD_ELEMENTS,
                        HudRenderer.getElementIconFrameIndex(elem));
                assertNotNull(r);
                assertEquals(16, r.getRegionWidth());
                assertEquals(16, r.getRegionHeight());
            }
            TextureRegion empty = tm.getExtendedFrame(CompleteSheetId.HUD_ELEMENTS,
                    HudRenderer.getElementIconFrameIndex(null));
            assertNotNull(empty);

            for (PowerUpType type : PowerUpType.values()) {
                TextureRegion r = tm.getExtendedFrame(CompleteSheetId.POWERUP_ICONS,
                        HudRenderer.getPowerUpIconFrameIndex(type));
                assertNotNull(r);
                assertEquals(16, r.getRegionWidth());
                assertEquals(16, r.getRegionHeight());
            }

            // Real TextureManager attached, headless null batch/shapes path
            assertDoesNotThrow(() -> hud.renderHUD(
                    null, null, fm,
                    session.getPlayer(), session.getProgression(),
                    session.getPowerUpManager(), session.getEnemies(), session.getEnemiesDefeated(),
                    session.getCurrentSpell(), session.getActiveMix(),
                    session.getMatchElapsed(), 42L, true, true
            ));
        } finally {
            tm.dispose();
            fm.dispose();
        }
    }

    @Test
    @DisplayName("m5-boss and m5-elements scenarios initialize valid gameplay states and simulate cleanly")
    void testM5ScenariosInitializeAndSimulateCleanly() {
        assertTrue(new Player(new Vector2D(0, 0)).isSingleFrameWalk(),
                "BalanceP2Test invariant: Player.singleFrameWalk must default true");

        EngineConfig bossCfg = EngineConfig.fromArgs(new String[]{"--seed", "42", "--scenario", "m5-boss"});
        assertTrue(bossCfg.isScenarioM5Boss());
        assertFalse(bossCfg.isScenarioM5Elements());

        GameSession bossSession = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 42L, false);
        SpellStormGame.applyConfiguredScenarios(bossSession, bossCfg);
        assertTrue(bossSession.isPlaying());
        assertTrue(bossSession.getPowerUpManager().getActiveAbility().isActive());
        assertTrue(bossSession.getEnemies().stream().anyMatch(e -> e.getType() == EnemyType.BOSS));
        assertTrue(bossSession.getEnemies().stream().anyMatch(e -> e.getType() == EnemyType.TANK));
        assertTrue(bossSession.getEnemies().stream().anyMatch(e -> e.getType() == EnemyType.RANGED));
        for (int i = 0; i < 30; i++) {
            bossSession.update(1f / 60f, bossSession.getPlayer().getPosition().cpy().add(100f, 0f),
                    new Vector2D(640f, 360f), true);
        }
        assertTrue(bossSession.getMatchElapsed() > 0.4f);

        EngineConfig elemCfg = new EngineConfig.Builder()
                .seed(42L)
                .scenarioM5Elements(true)
                .build();
        assertTrue(elemCfg.isScenarioM5Elements());

        GameSession elemSession = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 42L, false);
        SpellStormGame.applyConfiguredScenarios(elemSession, elemCfg);
        assertTrue(elemSession.isPlaying());
        assertTrue(elemSession.getCombatSystem().getProjectileCount() >= 6,
                "m5-elements must launch all elemental + arcane/enemy projectiles");
        assertTrue(elemSession.getExperienceSystem().getPickupCount() >= 3,
                "m5-elements must spawn Cyan, Emerald, and Gold gems");
        for (int i = 0; i < 30; i++) {
            elemSession.update(1f / 60f, elemSession.getPlayer().getPosition().cpy().add(150f, 0f),
                    new Vector2D(640f, 360f), false);
        }
        assertTrue(elemSession.getMatchElapsed() > 0.4f);
    }
}
