package com.spellstorm.p3;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.assets.AssetRegistry.TextureId;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.assets.TextureManager.ExtendedSheetId;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.core.EngineConfig;
import com.spellstorm.core.GameSession;
import com.spellstorm.core.SpellStormGame;
import com.spellstorm.entities.EnemyType;
import com.spellstorm.entities.Player;
import com.spellstorm.math.Vector2D;
import com.spellstorm.world.DecoType;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(HeadlessTestRunner.class)
@DisplayName("Milestone 2 Visual Prototype Integration Tests")
public class Milestone2PrototypeTest {

    private static final Map<ExtendedSheetId, int[]> EXPECTED_SHEETS = Map.of(
            ExtendedSheetId.PLAYER_DOWN_ANIM, new int[]{256, 32, 32, 32, 8},
            ExtendedSheetId.PLAYER_UP_ANIM, new int[]{256, 32, 32, 32, 8},
            ExtendedSheetId.PLAYER_LEFT_ANIM, new int[]{256, 32, 32, 32, 8},
            ExtendedSheetId.PLAYER_RIGHT_ANIM, new int[]{256, 32, 32, 32, 8},
            ExtendedSheetId.MELEE_FIEND_ANIM, new int[]{256, 32, 32, 32, 8},
            ExtendedSheetId.CAMPFIRE_ANIM, new int[]{96, 24, 24, 24, 4},
            ExtendedSheetId.SPELL_FIRE_ANIM, new int[]{144, 24, 24, 24, 6},
            ExtendedSheetId.VFX_FIRE_IMPACT, new int[]{192, 32, 32, 32, 6},
            ExtendedSheetId.VFX_FIRE_CAST, new int[]{96, 24, 24, 24, 4}
    );

    @Test
    @DisplayName("All 9 ExtendedSheetId sheets load with Nearest filtering, exact dimensions, and exact frame counts")
    void testExtendedSheetsLoadAndSlice() {
        assertEquals(9, ExtendedSheetId.values().length, "Must have exactly 9 ExtendedSheetId entries");
        assertEquals(36, TextureId.values().length, "Canonical AssetRegistry.TextureId must remain 36 constants");

        TextureManager tm = new TextureManager();
        try {
            tm.loadAll();

            for (ExtendedSheetId id : ExtendedSheetId.values()) {
                int[] spec = EXPECTED_SHEETS.get(id);
                assertNotNull(spec, "Expected spec missing for " + id);

                assertTrue(tm.hasExtendedSheet(id), "Extended sheet must load: " + id.name());
                Texture tex = tm.getExtendedTexture(id);
                assertNotNull(tex, "Texture must not be null for " + id.name());
                assertEquals(TextureFilter.Nearest, tex.getMinFilter(), "MinFilter must be Nearest for " + id.name());
                assertEquals(TextureFilter.Nearest, tex.getMagFilter(), "MagFilter must be Nearest for " + id.name());

                assertEquals(spec[0], tex.getWidth(), "Width mismatch for " + id.name());
                assertEquals(spec[1], tex.getHeight(), "Height mismatch for " + id.name());
                assertEquals(spec[2], id.getFrameWidth(), "Frame width mismatch for " + id.name());
                assertEquals(spec[3], id.getFrameHeight(), "Frame height mismatch for " + id.name());
                assertEquals(spec[4], id.getTotalFrames(), "Total frames mismatch for " + id.name());

                for (int f = 0; f < spec[4]; f++) {
                    TextureRegion region = tm.getExtendedFrame(id, f);
                    assertNotNull(region, "Frame " + f + " must not be null for " + id.name());
                    assertEquals(f * spec[2], region.getRegionX(), "Frame " + f + " X offset mismatch for " + id.name());
                    assertEquals(0, region.getRegionY(), "Frame " + f + " Y offset mismatch for " + id.name());
                    assertEquals(spec[2], region.getRegionWidth(), "Frame " + f + " width mismatch for " + id.name());
                    assertEquals(spec[3], region.getRegionHeight(), "Frame " + f + " height mismatch for " + id.name());
                }

                // Negative clamp & modulo wrap
                assertSame(tm.getExtendedFrame(id, 0), tm.getExtendedFrame(id, -3),
                        "Negative frame index must clamp to 0 for " + id.name());
                assertSame(tm.getExtendedFrame(id, 1), tm.getExtendedFrame(id, spec[4] + 1),
                        "Frame index must wrap modulo totalFrames for " + id.name());
            }

            // Null safety
            assertFalse(tm.hasExtendedSheet(null));
            assertNull(tm.getExtendedTexture(null));
            assertNull(tm.getExtendedFrame(null, 0));
        } finally {
            tm.dispose();
            assertFalse(tm.hasExtendedSheet(ExtendedSheetId.PLAYER_DOWN_ANIM));
            assertDoesNotThrow(tm::dispose);
        }
    }

    @Test
    @DisplayName("Player.idleTimer advances in update() and resets in resetPlayer() without altering singleFrameWalk")
    void testPlayerIdleTimerAndWalkInvariant() {
        Player player = new Player(new Vector2D(2048.0f, 2048.0f));
        assertEquals(0.0f, player.getIdleTimer(), 1e-6f);
        assertTrue(player.isSingleFrameWalk(), "singleFrameWalk default must remain true");

        player.update(0.25f);
        player.update(0.15f);
        assertEquals(0.40f, player.getIdleTimer(), 1e-4f);

        player.handleMovementInput(false, false, false, true, 0.05f, null);
        assertEquals(0, player.getAnimationFrame(), "getAnimationFrame() must remain 0 by default");

        player.resetPlayer(new Vector2D(2048.0f, 2048.0f));
        assertEquals(0.0f, player.getIdleTimer(), 1e-6f);
    }

    @Test
    @DisplayName("m2-prototype CLI scenario configures terrain transition, props, and 3 MeleeEnemies")
    void testM2PrototypeScenarioConfiguration() {
        EngineConfig cfg = EngineConfig.fromArgs(new String[]{
                "--seed", "42", "--autoplay", "--scenario", "m2-prototype"
        });
        assertTrue(cfg.isScenarioM2Prototype());
        assertTrue(cfg.isAutoplay());

        GameSession session = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 42L, false);
        SpellStormGame.applyConfiguredScenarios(session, cfg);

        assertTrue(session.isPlaying(), "m2-prototype scenario must start match in PLAYING state");
        long meleeCount = session.getEnemies().stream()
                .filter(e -> e.isAlive() && e.getType() == EnemyType.MELEE)
                .count();
        assertEquals(3, meleeCount, "m2-prototype must spawn 3 MeleeEnemy instances");

        TileMap map = session.getTileMap();
        assertEquals(TileType.DIRT, map.tileAt(79, 64));
        assertEquals(TileType.STONE, map.tileAt(75, 61));
        assertEquals(TileType.GRASS, map.tileAt(74, 68));

        assertTrue(map.getObstacles().stream().anyMatch(o -> o.getType() == DecoType.CAMPFIRE));
        assertTrue(map.getObstacles().stream().anyMatch(o -> o.getType() == DecoType.TREE_OAK));
        assertTrue(map.getObstacles().stream().anyMatch(o -> o.getType() == DecoType.ROCK));
        assertTrue(map.getObstacles().stream().anyMatch(o -> o.getType() == DecoType.BUSH));
    }
}
