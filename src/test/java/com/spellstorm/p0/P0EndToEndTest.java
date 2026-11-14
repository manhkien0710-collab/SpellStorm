package com.spellstorm.p0;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.assets.FontManager;
import com.spellstorm.combat.*;
import com.spellstorm.core.GameScreen;
import com.spellstorm.core.GameSession;
import com.spellstorm.entities.*;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.PlayerProgression;
import com.spellstorm.systems.PowerUpManager;
import com.spellstorm.ui.HudRenderer;
import com.spellstorm.ui.MinimapRenderer;
import com.spellstorm.world.TileMap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive End-to-End Integration Test Suite for SpellStorm P0 Remediation.
 *
 * Verifies unified end-to-end assertions for all 7 P0 defects:
 * - D1: Player spell projectiles deal direct damage; continuous Firebolt at 0.28s cadence kills a 1000 HP dummy;
 *       burn refresh preserves tickTimer cadence; pierce falloff attenuates damage.
 * - D2: Player movement input in GameSession processes once per tick (3.8333 px/frame at 230 px/s, NOT 7.6666 px).
 * - D3: V key toggling ensures session and player/HUD shooting mode stay synchronized across frames without desync.
 * - D4: Contact damage single mitigation: 15 raw vs 30 armor = 11.5385 +/- 1e-4; boss 45 raw vs 30 armor = 34.6154 +/- 1e-4.
 * - D5: Boss health bar is positioned at top center (y = 28f in 1280x720 y-down viewport), rendered exactly once per frame,
 *       boss name and HP text are strictly inside viewport without clipping.
 * - D6: PowerUpManager with TileMap: 10,000 spawn position samples near the river yield 0 invalid/untraversable positions;
 *       graceful fallback when TileMap is null; GameSession wires TileMap to PowerUpManager.
 * - D7: Asset bounding box verification using ImageIO: gem textures >= 8x8 and centered; projectile textures >= 12x12
 *       and centered; authentic emerald color palette.
 */
@DisplayName("P0 End-to-End Unified Integration Test Suite (Defects D1 - D7)")
public class P0EndToEndTest {

    private Input originalInput;
    private MockInputController mockInput;

    @BeforeAll
    public static void setupAll() {
        HeadlessTestRunner.init();
    }

    @BeforeEach
    public void setupEach() {
        originalInput = Gdx.input;
        mockInput = new MockInputController();
        Gdx.input = mockInput.createProxy();
    }

    @AfterEach
    public void tearDownEach() {
        Gdx.input = originalInput;
    }

    // =========================================================================
    // Mock Input Controller for LibGDX Headless Testing
    // =========================================================================

    public static class MockInputController {
        public final Set<Integer> pressedKeys = new HashSet<>();
        public int mouseX = 640;
        public int mouseY = 360;
        public boolean leftMouseButton = false;
        public InputProcessor processor = null;

        public void pressKey(int key) {
            pressedKeys.add(key);
        }

        public void releaseKey(int key) {
            pressedKeys.remove(key);
        }

        public void clear() {
            pressedKeys.clear();
            leftMouseButton = false;
        }

        public Input createProxy() {
            return (Input) Proxy.newProxyInstance(
                    Input.class.getClassLoader(),
                    new Class<?>[]{Input.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("isKeyPressed") && args != null && args.length == 1) {
                            return pressedKeys.contains((Integer) args[0]);
                        }
                        if (name.equals("isKeyJustPressed") && args != null && args.length == 1) {
                            return pressedKeys.contains((Integer) args[0]);
                        }
                        if (name.equals("isButtonPressed") && args != null && args.length == 1) {
                            int button = (Integer) args[0];
                            return button == Input.Buttons.LEFT && leftMouseButton;
                        }
                        if (name.equals("getX")) return mouseX;
                        if (name.equals("getY")) return mouseY;
                        if (name.equals("setInputProcessor") && args != null && args.length == 1) {
                            processor = (InputProcessor) args[0];
                            return null;
                        }
                        if (name.equals("getInputProcessor")) {
                            return processor;
                        }
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) return false;
                        if (rt == int.class) return 0;
                        if (rt == float.class) return 0.0f;
                        if (rt == long.class) return 0L;
                        return null;
                    }
            );
        }
    }

    private static class DummyEnemy extends MeleeEnemy {
        public DummyEnemy(Vector2D pos, float health, float armor) {
            super(pos, health, 0.0f, 0.0f, 16.0f, 1.0f, 10);
            setArmor(armor);
        }
    }

    // =========================================================================
    // DEFECT D1: Combat & Spell Damage E2E Tests
    // =========================================================================

    @Nested
    @DisplayName("D1: Combat & Direct Spell Damage End-to-End")
    class DefectD1CombatTests {

        @ParameterizedTest(name = "Direct spell damage for {0}")
        @EnumSource(value = Element.class, names = {"FIRE", "ICE", "LIGHTNING", "WIND", "EARTH"})
        @DisplayName("D1: Each of the 5 base spell projectiles deals direct damage to target on hit")
        public void testBaseSpellProjectilesDealDirectDamage(Element element) {
            SpellRegistry registry = SpellRegistry.createDefaultRegistry();
            SpellDefinition spell = registry.baseSpell(element);
            assertNotNull(spell, "Base spell for " + element + " must exist in registry");

            Rectangle arena = new Rectangle(0, 0, 4096, 4096);
            CombatSystem combat = new CombatSystem(arena);

            Player player = new Player(new Vector2D(500, 500));
            DummyEnemy dummy = new DummyEnemy(new Vector2D(550, 500), 500.0f, 0.0f);
            List<Enemy> enemies = new ArrayList<>(List.of(dummy));

            ProjectileProfile profile = spell.getProfile();
            float baseDamage = 35.0f * (profile != null ? profile.getDamageMultiplier() : 1.0f);
            int pierce = profile != null ? profile.getPierce() : 0;

            Projectile proj = new Projectile(
                    new Vector2D(545, 500), new Vector2D(1, 0), 400.0f, 10.0f, baseDamage, 1.0f, pierce,
                    ProjectileFaction.PLAYER, spell.getPrimaryColor(), spell.getSecondaryColor(), spell
            );
            combat.addProjectile(proj);

            float initialHp = dummy.getHealth();
            combat.update(0.016f, player, enemies);

            float damageDealt = initialHp - dummy.getHealth();
            assertTrue(damageDealt > 0.0f, "Spell " + spell.getName() + " must deal direct damage on hit");
            assertEquals(baseDamage, damageDealt, 0.01f, "Direct hit damage must match configured baseDamage");
        }

        @Test
        @DisplayName("D1: Continuous Firebolt at 0.28s cadence kills a 1000 HP dummy within 12 seconds")
        public void testContinuousFireboltKills1000HpDummy() {
            SpellRegistry registry = SpellRegistry.createDefaultRegistry();
            SpellDefinition firebolt = registry.baseSpell(Element.FIRE);
            assertNotNull(firebolt);

            Rectangle arena = new Rectangle(0, 0, 4096, 4096);
            CombatSystem combat = new CombatSystem(arena);

            Player player = new Player(new Vector2D(500, 500));
            DummyEnemy dummy = new DummyEnemy(new Vector2D(600, 500), 1000.0f, 0.0f);
            List<Enemy> enemies = new ArrayList<>(List.of(dummy));

            float fireCadence = 0.28f;
            float shotTimer = fireCadence;
            float totalTime = 0.0f;
            float dt = 1.0f / 60.0f;
            float timeout = 12.0f;
            int shotsFired = 0;

            while (dummy.isAlive() && totalTime < timeout) {
                shotTimer += dt;
                if (shotTimer >= fireCadence) {
                    shotTimer -= fireCadence;
                    Projectile p = new Projectile(
                            new Vector2D(590, 500), new Vector2D(1, 0), 400.0f, 9.0f, 35.0f, 1.2f, 0,
                            ProjectileFaction.PLAYER, firebolt.getPrimaryColor(), firebolt.getSecondaryColor(), firebolt
                    );
                    combat.addProjectile(p);
                    shotsFired++;
                }

                combat.update(dt, player, enemies);
                dummy.update(dt);
                totalTime += dt;
            }

            assertFalse(dummy.isAlive(),
                    "Continuous Firebolt must kill 1000 HP dummy within 12s! Remaining HP: " + dummy.getHealth() + " after " + shotsFired + " shots");
            assertTrue(dummy.getHealth() <= 0.0f, "Dummy health must be <= 0");
            assertTrue(totalTime < timeout, "Dummy should be eliminated well before timeout");
        }

        @Test
        @DisplayName("D1: BurnStatus refresh preserves tickTimer cadence instead of resetting timer to 0.5s")
        public void testBurnStatusRefreshPreservesTickCadence() {
            StatusEffectList statusList = new StatusEffectList();
            DummyEnemy dummy = new DummyEnemy(new Vector2D(100, 100), 200.0f, 0.0f);

            // Initial burn: 20 DPS, 3.0s duration -> 10 damage per 0.5s tick
            BurnStatus burn1 = new BurnStatus(20.0f, 3.0f);
            statusList.add(burn1);

            // Step 0.3s -> tickTimer drops from 0.5s to 0.2s. No tick yet.
            statusList.update(0.3f, dummy);
            assertEquals(200.0f, dummy.getHealth(), 0.001f, "No damage should be dealt before first 0.5s tick");

            // Refresh burn at t=0.3s (simulates Firebolt re-hit)
            BurnStatus burn2 = new BurnStatus(20.0f, 3.0f);
            statusList.add(burn2);

            // Step another 0.25s (total elapsed time 0.55s).
            // Cadence preserved: tick fires at t=0.50s (0.2s after refresh), dealing 10 damage.
            // If timer was incorrectly reset to 0.5s, remaining time would be 0.25s and damage would be 0!
            statusList.update(0.25f, dummy);

            assertEquals(190.0f, dummy.getHealth(), 0.01f,
                    "Burn tick must have fired at t=0.50s dealing 10 true damage even with refresh at t=0.30s");
        }

        @Test
        @DisplayName("D1: Pierce falloff (20%) attenuates direct spell damage on multi-target piercing projectiles")
        public void testPierceFalloffAttenuatesDirectSpellDamage() {
            SpellRegistry registry = SpellRegistry.createDefaultRegistry();
            SpellDefinition frostShard = registry.baseSpell(Element.ICE); // Pierce = 1

            Rectangle arena = new Rectangle(0, 0, 4096, 4096);
            CombatSystem combat = new CombatSystem(arena);

            Player player = new Player(new Vector2D(500, 500));
            DummyEnemy enemy1 = new DummyEnemy(new Vector2D(550, 500), 500.0f, 0.0f);
            DummyEnemy enemy2 = new DummyEnemy(new Vector2D(650, 500), 500.0f, 0.0f);
            List<Enemy> enemies = new ArrayList<>(List.of(enemy1, enemy2));

            float initialDamage = 35.0f * frostShard.getProfile().getDamageMultiplier(); // 35 * 0.8 = 28.0f
            Projectile proj = new Projectile(
                    new Vector2D(540, 500), new Vector2D(1, 0), 500.0f, 10.0f, initialDamage, 1.0f, 1,
                    ProjectileFaction.PLAYER, frostShard.getPrimaryColor(), frostShard.getSecondaryColor(), frostShard
            );
            combat.addProjectile(proj);

            // Hit enemy 1
            combat.update(0.016f, player, enemies);
            float damage1 = 500.0f - enemy1.getHealth();
            assertEquals(initialDamage, damage1, 0.01f, "First target receives full direct spell damage");

            // Move projectile to enemy 2
            proj.getPosition().set(640, 500);
            combatSystemUpdate(combat, 0.016f, player, enemies);
            float damage2 = 500.0f - enemy2.getHealth();

            float expectedDamage2 = initialDamage * Projectile.PIERCE_DAMAGE_FALLOFF;
            assertEquals(expectedDamage2, damage2, 0.01f, "Second target receives 20% attenuated direct damage");
            assertEquals(0.80f, damage2 / damage1, 0.01f);
        }

        @Test
        @DisplayName("D1: Firestorm combination spell deals direct hit damage plus intentional AoE explosion damage")
        public void testFirestormDirectHitPlusAoEExplosion() {
            SpellRegistry registry = SpellRegistry.createDefaultRegistry();
            SpellDefinition firestorm = registry.resolve(List.of(Element.FIRE, Element.WIND));
            assertNotNull(firestorm);

            Rectangle arena = new Rectangle(0, 0, 4096, 4096);
            CombatSystem combat = new CombatSystem(arena);

            Player player = new Player(new Vector2D(500, 500));
            DummyEnemy directTarget = new DummyEnemy(new Vector2D(550, 500), 1000.0f, 0.0f);
            DummyEnemy aoeBystander = new DummyEnemy(new Vector2D(580, 500), 1000.0f, 0.0f);
            List<Enemy> enemies = new ArrayList<>(List.of(directTarget, aoeBystander));

            float baseDamage = 35.0f * firestorm.getProfile().getDamageMultiplier(); // 35 * 1.5 = 52.5f
            Projectile proj = new Projectile(
                    new Vector2D(545, 500), new Vector2D(1, 0), 400.0f, 12.0f, baseDamage, 1.0f, 0,
                    ProjectileFaction.PLAYER, firestorm.getPrimaryColor(), firestorm.getSecondaryColor(), firestorm
            );
            combat.addProjectile(proj);

            combat.update(0.016f, player, enemies);

            // Direct target: direct damage (52.5) + AreaEffect explosion (52.5 * 1.2 = 63.0) = 115.5
            float directTargetDamage = 1000.0f - directTarget.getHealth();
            float expectedDirectDamage = baseDamage + (baseDamage * 1.2f);
            assertEquals(expectedDirectDamage, directTargetDamage, 0.01f,
                    "Direct target takes direct projectile damage + AoE explosion");

            // Bystander: only AoE explosion (63.0)
            float bystanderDamage = 1000.0f - aoeBystander.getHealth();
            assertEquals(baseDamage * 1.2f, bystanderDamage, 0.01f,
                    "Bystander within AoE radius takes explosion damage");
        }

        private void combatSystemUpdate(CombatSystem combat, float dt, Player player, List<Enemy> enemies) {
            combat.update(dt, player, enemies);
        }
    }

    // =========================================================================
    // DEFECT D2: Player Movement Single Processing E2E Tests
    // =========================================================================

    @Nested
    @DisplayName("D2: Player Movement Single Processing End-to-End")
    class DefectD2MovementTests {

        @Test
        @DisplayName("D2: Player movement input in GameScreen/GameSession processes once per tick (3.8333 px/frame at 230 px/s)")
        public void testPlayerMovementSingleProcessingPerFrame() {
            GameSession session = new GameSession();
            GameScreen screen = new GameScreen(null, session, null, null);

            Player player = session.getPlayer();
            float startX = player.getPosition().x;
            float startY = player.getPosition().y;

            // Press 'D' (move right)
            mockInput.pressKey(Keys.D);

            float dt = 1.0f / 60.0f;
            float expectedDx = player.getBaseMoveSpeed() * dt; // 230 * (1/60) = 3.8333333f

            screen.render(dt);

            float actualDx = player.getPosition().x - startX;
            float actualDy = player.getPosition().y - startY;

            assertEquals(0.0f, actualDy, 1e-4f, "No vertical movement when pressing only D");
            assertEquals(expectedDx, actualDx, 0.01f,
                    String.format("Expected single displacement %.4f px/frame, but got %.4f px/frame (duplicate input processing)",
                            expectedDx, actualDx));
            assertNotEquals(expectedDx * 2.0f, actualDx, 0.05f, "Displacement must NOT be duplicate (7.6666 px)");
        }

        @Test
        @DisplayName("D2: Diagonal movement magnitude is strictly 3.8333 px/frame (normalized single processing)")
        public void testDiagonalMovementMagnitudeSingleProcessing() {
            GameSession session = new GameSession();
            GameScreen screen = new GameScreen(null, session, null, null);

            Player player = session.getPlayer();
            Vector2D startPos = player.getPosition().cpy();

            // Press 'W' and 'D' (up-right)
            mockInput.pressKey(Keys.W);
            mockInput.pressKey(Keys.D);

            float dt = 1.0f / 60.0f;
            screen.render(dt);

            float displacement = player.getPosition().cpy().sub(startPos).len();
            float expectedDisplacement = player.getBaseMoveSpeed() * dt; // 3.8333333f

            assertEquals(expectedDisplacement, displacement, 0.01f,
                    String.format("Expected diagonal displacement %.4f px, but got %.4f px",
                            expectedDisplacement, displacement));
        }

        @Test
        @DisplayName("D2: Opposing movement keys (A + D) cancel to strictly zero displacement in GameScreen.render")
        public void testOpposingMovementKeysCancelDisplacement() {
            GameSession session = new GameSession();
            GameScreen screen = new GameScreen(null, session, null, null);

            Player player = session.getPlayer();
            Vector2D startPos = player.getPosition().cpy();

            mockInput.pressKey(Keys.A);
            mockInput.pressKey(Keys.D);

            float dt = 1.0f / 60.0f;
            screen.render(dt);

            assertEquals(startPos.x, player.getPosition().x, 1e-4f, "Opposing X keys must cancel out");
            assertEquals(startPos.y, player.getPosition().y, 1e-4f, "Opposing Y keys must cancel out");
        }
    }

    // =========================================================================
    // DEFECT D3: Shooting Mode Synchronization E2E Tests
    // =========================================================================

    @Nested
    @DisplayName("D3: Shooting Mode V Key Synchronization End-to-End")
    class DefectD3ShootingModeTests {

        @Test
        @DisplayName("D3: V key toggling ensures session and player/HUD shooting mode stay synchronized across frames without desync")
        public void testShootingModeSyncAcrossFramesOnVKeyPress() {
            GameSession session = new GameSession();
            GameScreen screen = new GameScreen(null, session, null, null);
            Player player = session.getPlayer();

            assertEquals(ShootingMode.MANUAL, session.shootingMode(), "Initial session mode must be MANUAL");
            assertEquals(ShootingMode.MANUAL, player.getShootingMode(), "Initial player mode must be MANUAL");

            // 1. Press V key
            screen.keyDown(Keys.V);
            mockInput.pressKey(Keys.V);

            // Both must immediately transition to AUTO
            assertEquals(ShootingMode.AUTO, session.shootingMode(), "Session must switch to AUTO on V keyDown");
            assertEquals(ShootingMode.AUTO, player.getShootingMode(), "Player must switch to AUTO on V keyDown");
            assertEquals(session.shootingMode(), player.getShootingMode(), "Session and Player modes must match");

            // 2. Render multiple frames with V held down: NO desynchronization must happen
            float dt = 1.0f / 60.0f;
            for (int frame = 1; frame <= 10; frame++) {
                screen.render(dt);
                assertEquals(ShootingMode.AUTO, session.shootingMode(), "Session must remain AUTO on frame " + frame);
                assertEquals(ShootingMode.AUTO, player.getShootingMode(),
                        "Player mode must NOT revert to MANUAL during continuous handleInput on frame " + frame);
                assertEquals(session.shootingMode(), player.getShootingMode(),
                        "Modes must remain identical across frame " + frame);
            }

            // 3. Release V
            screen.keyUp(Keys.V);
            mockInput.releaseKey(Keys.V);
            screen.render(dt);

            assertEquals(ShootingMode.AUTO, session.shootingMode());
            assertEquals(ShootingMode.AUTO, player.getShootingMode());

            // 4. Press V again to toggle back to MANUAL
            screen.keyDown(Keys.V);
            mockInput.pressKey(Keys.V);
            screen.render(dt);

            assertEquals(ShootingMode.MANUAL, session.shootingMode(), "Session must switch back to MANUAL");
            assertEquals(ShootingMode.MANUAL, player.getShootingMode(), "Player must switch back to MANUAL");
            assertEquals(session.shootingMode(), player.getShootingMode(), "Modes must match after returning to MANUAL");
        }
    }

    // =========================================================================
    // DEFECT D4: Contact Damage Single Mitigation E2E Tests
    // =========================================================================

    @Nested
    @DisplayName("D4: Contact Damage Single Mitigation End-to-End")
    class DefectD4ContactDamageTests {

        @Test
        @DisplayName("D4: Melee enemy 15 raw contact damage vs player armor 30 results in 11.5385 +/- 1e-4 in GameSession")
        public void testPlayerContactDamage15VsArmor30() {
            Rectangle bounds = new Rectangle(0, 0, 4096, 4096);
            SpellRegistry registry = SpellRegistry.createDefaultRegistry();
            GameSession session = new GameSession(bounds, registry, 42L);

            Player player = session.getPlayer();
            player.setHealth(100.0f);
            player.setArmor(30.0f);

            // Overlap melee enemy with player
            Vector2D pPos = player.getPosition();
            MeleeEnemy enemy = new MeleeEnemy(pPos.cpy());
            enemy.setContactDamage(15.0f);
            session.addEnemy(enemy);

            // Run 1 simulation frame (0.016s)
            session.update(0.016f, new Vector2D(pPos.x + 100, pPos.y), new Vector2D(0, 0), false);

            // Expected single mitigation: 15 * (100 / 130) = 11.5384615...
            float expectedDamage = 15.0f * (100.0f / (100.0f + 30.0f));
            float actualDamage = 100.0f - player.getHealth();

            assertEquals(11.5385f, actualDamage, 1e-4f,
                    "Contact damage 15 against armor 30 must be mitigated once to 11.5385, not twice (8.8757)");
            assertEquals(expectedDamage, actualDamage, 1e-4f);
        }

        @Test
        @DisplayName("D4: Boss 45 raw contact damage vs player armor 30 results in 34.6154 +/- 1e-4 in GameSession")
        public void testBossContactDamage45VsArmor30() {
            Rectangle bounds = new Rectangle(0, 0, 4096, 4096);
            SpellRegistry registry = SpellRegistry.createDefaultRegistry();
            GameSession session = new GameSession(bounds, registry, 42L);

            Player player = session.getPlayer();
            player.setHealth(100.0f);
            player.setArmor(30.0f);

            Vector2D pPos = player.getPosition();
            BossEnemy boss = new BossEnemy(pPos.cpy());
            assertEquals(45.0f, boss.getContactDamage(), 0.001f);
            session.addEnemy(boss);

            session.update(0.016f, new Vector2D(pPos.x + 100, pPos.y), new Vector2D(0, 0), false);

            // Expected single mitigation: 45 * (100 / 130) = 34.6153846...
            float expectedDamage = 45.0f * (100.0f / (100.0f + 30.0f));
            float actualDamage = 100.0f - player.getHealth();

            assertEquals(34.6154f, actualDamage, 1e-4f,
                    "Boss contact damage 45 against armor 30 must be mitigated once to 34.6154, not twice (26.6272)");
            assertEquals(expectedDamage, actualDamage, 1e-4f);
        }

        @Test
        @DisplayName("D4: Player takeDamage direct call matches DamageMitigation calculation exactly")
        public void testDirectDamageMitigationEquivalence() {
            Player player = new Player(new Vector2D(500, 500));
            player.setHealth(100.0f);
            player.setArmor(30.0f);

            float rawDamage = 15.0f;
            float directMitigated = DamageMitigation.calculateDamage(rawDamage, player.getArmor());
            float actualTaken = player.takeDamage(rawDamage);

            assertEquals(directMitigated, actualTaken, 1e-4f);
            assertEquals(11.5385f, actualTaken, 1e-4f);
        }
    }

    // =========================================================================
    // DEFECT D5: Boss Health Bar Layout & Rendering E2E Tests
    // =========================================================================

    @Nested
    @DisplayName("D5: Boss Health Bar Layout & Rendering End-to-End")
    class DefectD5BossHealthBarTests {

        @Test
        @DisplayName("D5: Boss health bar is positioned at top center (y = 28f in 1280x720 y-down viewport)")
        public void testBossHealthBarTopCenterPosition() {
            // Default 560px width
            Rectangle bounds560 = HudRenderer.getBossBarBounds(1280f, 720f, true);
            assertEquals(360.0f, bounds560.x, 1e-4f, "Bar X must be centered: (1280 - 560)/2 = 360");
            assertEquals(28.0f, bounds560.y, 1e-4f, "Bar Y in y-down viewport must be top offset 28f (not 670f)");
            assertEquals(560.0f, bounds560.width, 1e-4f);
            assertEquals(22.0f, bounds560.height, 1e-4f);

            // Custom 480px width
            Rectangle bounds480 = HudRenderer.getBossBarBounds(1280f, 720f, 480f, 22f, true);
            assertEquals(400.0f, bounds480.x, 1e-4f, "Bar X must be centered: (1280 - 480)/2 = 400");
            assertEquals(28.0f, bounds480.y, 1e-4f);
            assertEquals(480.0f, bounds480.width, 1e-4f);
            assertEquals(22.0f, bounds480.height, 1e-4f);
        }

        @Test
        @DisplayName("D5: Boss name, HP text, and health bar are strictly inside 1280x720 viewport without clipping")
        public void testBossNameAndHpTextStrictlyInsideViewport() {
            FontManager fm = new FontManager();
            fm.initialize();
            try {
                BitmapFont bold = fm.fontBoldRegular;
                BitmapFont body = fm.fontVitals;
                assertNotNull(bold);
                assertNotNull(body);

                BossEnemy boss = new BossEnemy(new Vector2D(0, 0));
                String bossName = boss.getName();
                String hpText = HudRenderer.formatHpText(boss);

                GlyphLayout layoutName = new GlyphLayout(bold, bossName);
                GlyphLayout layoutHp = new GlyphLayout(body, hpText);

                Rectangle barBounds = HudRenderer.getBossBarBounds(1280f, 720f, true);
                Rectangle nameBounds = HudRenderer.getBossNameBounds(1280f, 720f, layoutName.width, layoutName.height, true);
                Rectangle hpBounds = HudRenderer.getBossHpTextBounds(1280f, 720f, layoutHp.width, layoutHp.height, true);

                // Viewport boundaries: [0, 1280] x [0, 720]
                assertTrue(barBounds.x >= 0.0f && barBounds.x + barBounds.width <= 1280.0f, "Bar within X bounds");
                assertTrue(barBounds.y >= 0.0f && barBounds.y + barBounds.height <= 720.0f, "Bar within Y bounds");

                assertTrue(nameBounds.x >= 0.0f && nameBounds.x + nameBounds.width <= 1280.0f, "Name within X bounds");
                assertTrue(nameBounds.y >= 0.0f && nameBounds.y + nameBounds.height <= 720.0f, "Name within Y bounds");

                assertTrue(hpBounds.x >= 0.0f && hpBounds.x + hpBounds.width <= 1280.0f, "HP within X bounds");
                assertTrue(hpBounds.y >= 0.0f && hpBounds.y + hpBounds.height <= 720.0f, "HP within Y bounds");

                // Boss name must be positioned above the health bar in y-down space
                assertTrue(nameBounds.y + nameBounds.height <= barBounds.y,
                        "Boss name must be above health bar in y-down space: nameBottom="
                                + (nameBounds.y + nameBounds.height) + " <= barTop=" + barBounds.y);

                // HP text must be positioned inside the health bar vertically
                assertTrue(hpBounds.y >= barBounds.y && hpBounds.y + hpBounds.height <= barBounds.y + barBounds.height,
                        "HP text must be vertically centered inside the health bar");
            } finally {
                fm.dispose();
            }
        }

        @Test
        @DisplayName("D5: Boss health bar is rendered exactly once per frame from renderHUD")
        public void testBossBarRenderedExactlyOncePerFrame() {
            HudRenderer hud = new HudRenderer();
            Player player = new Player();
            PlayerProgression prog = new PlayerProgression();
            PowerUpManager powerUp = new PowerUpManager();
            BossEnemy boss = new BossEnemy(new Vector2D(0, 0));
            List<Enemy> enemies = List.of(boss);

            hud.resetRenderCounts();
            assertEquals(0, hud.getBossBarRenderCount());

            // Frame 1
            hud.renderHUD(null, null, null, player, prog, powerUp, enemies, 0, null, true);
            assertEquals(1, hud.getBossBarRenderCount(), "Frame 1 must render boss bar exactly once");

            // Frame 2
            hud.renderHUD(null, null, null, player, prog, powerUp, enemies, 0, null, true);
            assertEquals(2, hud.getBossBarRenderCount(), "Frame 2 must increment render count to 2");

            // Dead boss does not render boss bar
            boss.takeDamage(99999.0f, true);
            assertFalse(boss.isAlive());
            hud.renderHUD(null, null, null, player, prog, powerUp, enemies, 0, null, true);
            assertEquals(2, hud.getBossBarRenderCount(), "Dead boss must NOT render boss bar");

            // Empty enemies list does not render boss bar
            hud.renderHUD(null, null, null, player, prog, powerUp, Collections.emptyList(), 0, null, true);
            assertEquals(2, hud.getBossBarRenderCount());
        }

        @Test
        @DisplayName("D5: Boss health bar (top-center) and Minimap (bottom-right) do not overlap")
        public void testBossBarAndMinimapDisjoint() {
            Rectangle bossBar = HudRenderer.getBossBarBounds(1280f, 720f, true);
            Rectangle minimap = MinimapRenderer.getMinimapBounds(true);

            assertFalse(bossBar.overlaps(minimap), "Boss bar and Minimap widgets must be completely disjoint");
            assertTrue(bossBar.y + bossBar.height < minimap.y, "Boss bar top offset must precede minimap Y");
        }
    }

    // =========================================================================
    // DEFECT D6: PowerUp Spawning Traversability & Safety E2E Tests
    // =========================================================================

    @Nested
    @DisplayName("D6: PowerUp Spawning Traversability End-to-End")
    class DefectD6PowerUpSpawnTests {

        @Test
        @DisplayName("D6: PowerUpManager with TileMap: 10,000 spawn position samples near river yield exactly 0 invalid positions")
        public void testTenThousandRiverSpawnsProduceZeroUntraversablePositions() {
            TileMap tileMap = new TileMap(42);
            Rectangle arenaBounds = new Rectangle(0, 0, TileMap.WORLD_WIDTH, TileMap.WORLD_HEIGHT);
            Random rng = new Random(20261112);
            PowerUpManager manager = new PowerUpManager(arenaBounds, rng, tileMap);

            assertEquals(tileMap, manager.getTileMap(), "manager.getTileMap() must return configured TileMap");

            final int sampleCount = 10_000;
            int untraversableCount = 0;

            for (int i = 0; i < sampleCount; i++) {
                // Focus samples closely flanking the river corridor [1000, 1750]
                float px = 1000.0f + rng.nextFloat() * 750.0f;
                float py = 150.0f + rng.nextFloat() * (TileMap.WORLD_HEIGHT - 300.0f);
                Vector2D playerPos = new Vector2D(px, py);

                Vector2D spawn = manager.findValidSpawnPosition(playerPos);
                assertNotNull(spawn, "Spawn position must not be null");

                // Verify arena bounds
                assertTrue(spawn.x >= 50.0f && spawn.x <= TileMap.WORLD_WIDTH - 50.0f);
                assertTrue(spawn.y >= 50.0f && spawn.y <= TileMap.WORLD_HEIGHT - 50.0f);

                // Verify traversability with power-up collision radius 18.0f
                if (!tileMap.isTraversable(spawn.x, spawn.y, 18.0f)) {
                    untraversableCount++;
                }
            }

            assertEquals(0, untraversableCount,
                    "Expected exactly 0 untraversable power-up spawns out of 10,000 near the river");
        }

        @Test
        @DisplayName("D6: Graceful fallback when TileMap is null (no NPE, valid bounded positions)")
        public void testNullTileMapFallbackGraceful() {
            Rectangle arenaBounds = new Rectangle(0, 0, TileMap.WORLD_WIDTH, TileMap.WORLD_HEIGHT);
            Random rng = new Random(42);
            PowerUpManager manager = new PowerUpManager(arenaBounds, rng, null);

            assertNull(manager.getTileMap());

            // Center player
            Vector2D pCenter = manager.findValidSpawnPosition(new Vector2D(2048f, 2048f));
            assertNotNull(pCenter);
            assertTrue(pCenter.x >= 50f && pCenter.x <= TileMap.WORLD_WIDTH - 50f);
            assertTrue(pCenter.y >= 50f && pCenter.y <= TileMap.WORLD_HEIGHT - 50f);

            // Null player
            Vector2D pNull = manager.findValidSpawnPosition(null);
            assertNotNull(pNull);
            assertTrue(pNull.x >= 50f && pNull.x <= TileMap.WORLD_WIDTH - 50f);
            assertTrue(pNull.y >= 50f && pNull.y <= TileMap.WORLD_HEIGHT - 50f);

            // Lifecycle execution without NPE
            Player player = new Player(new Vector2D(2048f, 2048f));
            assertDoesNotThrow(() -> {
                manager.spawnRandomPickup(player.getPosition());
                manager.update(0.016f, player, false);
            });
            assertEquals(1, manager.getPickups().size());
        }

        @Test
        @DisplayName("D6: GameSession automatically wires its TileMap to PowerUpManager")
        public void testGameSessionWiresTileMapToPowerUpManager() {
            GameSession session = new GameSession();
            assertNotNull(session.getTileMap(), "Session TileMap must not be null");
            assertNotNull(session.getPowerUpManager(), "Session PowerUpManager must not be null");
            assertSame(session.getTileMap(), session.getPowerUpManager().getTileMap(),
                    "GameSession must set its TileMap onto PowerUpManager");
        }
    }

    // =========================================================================
    // DEFECT D7: Asset Bounding Box Verification E2E Tests
    // =========================================================================

    @Nested
    @DisplayName("D7: Asset Bounding Box Verification End-to-End")
    class DefectD7AssetBBoxTests {

        private File resolveAsset(String relativePath) {
            String[] candidates = {
                    relativePath,
                    "assets/" + relativePath,
                    "../assets/" + relativePath
            };
            for (String c : candidates) {
                File f = new File(c);
                if (f.exists()) {
                    return f;
                }
            }
            return new File("assets/" + relativePath);
        }

        private int[] computeNonZeroAlphaBBox(BufferedImage img) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = -1;
            int maxY = -1;

            int width = img.getWidth();
            int height = img.getHeight();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = img.getRGB(x, y);
                    int alpha = (pixel >> 24) & 0xFF;
                    if (alpha > 0) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }

            if (maxX < 0) {
                return new int[]{0, 0, 0, 0}; // Empty
            }
            int w = maxX - minX + 1;
            int h = maxY - minY + 1;
            return new int[]{minX, minY, maxX, maxY, w, h};
        }

        @ParameterizedTest(name = "EXP Gem BBox: {0}")
        @ValueSource(strings = {
                "textures/pickups/gem_cyan.png",
                "textures/pickups/gem_gold.png",
                "textures/pickups/gem_emerald.png"
        })
        @DisplayName("D7: Gem textures are 16x16, non-zero alpha bbox >= 8x8 and centered within 2.0px")
        public void testGemBoundingBox(String path) throws IOException {
            File f = resolveAsset(path);
            assertTrue(f.exists(), "Asset file must exist: " + path);

            BufferedImage img = ImageIO.read(f);
            assertNotNull(img, "ImageIO must decode " + path);
            assertEquals(16, img.getWidth(), "Gem canvas width must be 16");
            assertEquals(16, img.getHeight(), "Gem canvas height must be 16");

            int[] bbox = computeNonZeroAlphaBBox(img);
            int minX = bbox[0], minY = bbox[1], maxX = bbox[2], maxY = bbox[3];
            int w = bbox[4], h = bbox[5];
            double cx = (minX + maxX) / 2.0;
            double cy = (minY + maxY) / 2.0;

            assertTrue(w >= 8, "Gem " + path + " bbox width must be >= 8, was: " + w);
            assertTrue(h >= 8, "Gem " + path + " bbox height must be >= 8, was: " + h);
            assertTrue(Math.abs(cx - 8.0) <= 2.0, "Gem center X must be within 2px of 8.0, was: " + cx);
            assertTrue(Math.abs(cy - 8.0) <= 2.0, "Gem center Y must be within 2px of 8.0, was: " + cy);

            // Border columns (0 and 15) must be completely transparent (not split across canvas)
            for (int y = 0; y < 16; y++) {
                int aLeft = (img.getRGB(0, y) >> 24) & 0xFF;
                int aRight = (img.getRGB(15, y) >> 24) & 0xFF;
                assertEquals(0, aLeft, "Gem col 0 must be transparent at y=" + y);
                assertEquals(0, aRight, "Gem col 15 must be transparent at y=" + y);
            }
        }

        @ParameterizedTest(name = "Spell Projectile BBox: {0}")
        @ValueSource(strings = {
                "textures/projectiles/spell_fire.png",
                "textures/projectiles/spell_lightning.png",
                "textures/projectiles/spell_wind.png"
        })
        @DisplayName("D7: Projectile textures are 24x24, non-zero alpha bbox >= 12x12 and centered within 2.5px")
        public void testProjectileBoundingBox(String path) throws IOException {
            File f = resolveAsset(path);
            assertTrue(f.exists(), "Asset file must exist: " + path);

            BufferedImage img = ImageIO.read(f);
            assertNotNull(img, "ImageIO must decode " + path);
            assertEquals(24, img.getWidth(), "Projectile canvas width must be 24");
            assertEquals(24, img.getHeight(), "Projectile canvas height must be 24");

            int[] bbox = computeNonZeroAlphaBBox(img);
            int minX = bbox[0], minY = bbox[1], maxX = bbox[2], maxY = bbox[3];
            int w = bbox[4], h = bbox[5];
            double cx = (minX + maxX) / 2.0;
            double cy = (minY + maxY) / 2.0;

            assertTrue(w >= 12, "Projectile " + path + " bbox width must be >= 12, was: " + w);
            assertTrue(h >= 12, "Projectile " + path + " bbox height must be >= 12, was: " + h);
            assertTrue(Math.abs(cx - 12.0) <= 2.5, "Projectile center X must be within 2.5px of 12.0, was: " + cx);
            assertTrue(Math.abs(cy - 12.0) <= 2.5, "Projectile center Y must be within 2.5px of 12.0, was: " + cy);

            // Border columns (0 and 23) must be completely transparent
            for (int y = 0; y < 24; y++) {
                int aLeft = (img.getRGB(0, y) >> 24) & 0xFF;
                int aRight = (img.getRGB(23, y) >> 24) & 0xFF;
                assertEquals(0, aLeft, "Projectile col 0 must be transparent at y=" + y);
                assertEquals(0, aRight, "Projectile col 23 must be transparent at y=" + y);
            }
        }

        @Test
        @DisplayName("D7: Emerald gem has authentic green palette distinct from cyan and gold")
        public void testEmeraldGemGreenPalette() throws IOException {
            File f = resolveAsset("textures/pickups/gem_emerald.png");
            BufferedImage img = ImageIO.read(f);

            long totalR = 0, totalG = 0, totalB = 0;
            int count = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int p = img.getRGB(x, y);
                    int a = (p >> 24) & 0xFF;
                    if (a > 64) {
                        totalR += (p >> 16) & 0xFF;
                        totalG += (p >> 8) & 0xFF;
                        totalB += p & 0xFF;
                        count++;
                    }
                }
            }

            assertTrue(count > 0, "Emerald gem must contain visible pixels");
            double avgR = (double) totalR / count;
            double avgG = (double) totalG / count;
            double avgB = (double) totalB / count;

            assertTrue(avgG > avgR * 1.5, "Green must dominate red in emerald: G=" + avgG + ", R=" + avgR);
            assertTrue(avgG > avgB * 1.2, "Green must dominate blue in emerald: G=" + avgG + ", B=" + avgB);
        }
    }

    // =========================================================================
    // UNIFIED FULL GAMEPLAY CYCLE INTEGRATION TEST
    // =========================================================================

    @Test
    @DisplayName("P0 Full Lifecycle E2E: Unified multi-system gameplay simulation exercising D1-D6 together")
    public void testUnifiedFullGameplaySimulation() {
        // Initialize complete GameSession with deterministic seed
        GameSession session = new GameSession(new Rectangle(0, 0, 4096, 4096), SpellRegistry.createDefaultRegistry(), 12345L);
        GameScreen screen = new GameScreen(null, session, null, null);

        Player player = session.getPlayer();
        player.setArmor(30.0f);
        player.setHealth(100.0f);

        // 1. Verify TileMap wired to PowerUpManager (D6)
        assertNotNull(session.getPowerUpManager().getTileMap());

        // 2. Spawn Boss Enemy with top-center health bar check (D5)
        BossEnemy boss = new BossEnemy(new Vector2D(player.getPosition().x + 200, player.getPosition().y));
        session.addEnemy(boss);
        Rectangle bossBar = HudRenderer.getBossBarBounds(1280f, 720f, true);
        assertEquals(28.0f, bossBar.y, 1e-4f);

        // 3. Move Player via Input (D2)
        mockInput.pressKey(Keys.D);
        float dt = 1.0f / 60.0f;
        float startX = player.getPosition().x;
        screen.render(dt);
        float movedX = player.getPosition().x - startX;
        assertEquals(player.getBaseMoveSpeed() * dt, movedX, 0.01f);
        mockInput.releaseKey(Keys.D);

        // 4. Toggle Shooting Mode (D3)
        screen.keyDown(Keys.V);
        mockInput.pressKey(Keys.V);
        screen.render(dt);
        assertEquals(ShootingMode.AUTO, session.shootingMode());
        assertEquals(ShootingMode.AUTO, player.getShootingMode());
        screen.keyUp(Keys.V);
        mockInput.releaseKey(Keys.V);

        // 5. Spawn Melee Enemy overlapping player to verify Single Contact Mitigation (D4)
        MeleeEnemy melee = new MeleeEnemy(player.getPosition().cpy());
        melee.setContactDamage(15.0f);
        session.addEnemy(melee);

        float preContactHp = player.getHealth();
        session.update(dt, new Vector2D(player.getPosition().x + 50, player.getPosition().y), new Vector2D(0, 0), false);
        float damageTaken = preContactHp - player.getHealth();
        assertEquals(11.5385f, damageTaken, 1e-4f);

        // 6. Cast Spell Projectile hitting Boss to verify Direct Spell Damage (D1)
        SpellDefinition firebolt = SpellRegistry.createDefaultRegistry().baseSpell(Element.FIRE);
        Projectile p = new Projectile(
                new Vector2D(boss.getPosition().x - 10, boss.getPosition().y),
                new Vector2D(1, 0),
                400.0f, 10.0f, 35.0f, 1.0f, 0,
                ProjectileFaction.PLAYER, firebolt.getPrimaryColor(), firebolt.getSecondaryColor(), firebolt
        );
        session.getCombatSystem().addProjectile(p);

        float preSpellHp = boss.getHealth();
        session.getCombatSystem().update(dt, player, session.getEnemies());
        float bossDamage = preSpellHp - boss.getHealth();
        assertTrue(bossDamage > 0.0f, "Boss must take direct spell projectile damage");

        // 7. Verify PowerUp Spawning produces a traversable position on the TileMap (D6)
        Vector2D pUpPos = session.getPowerUpManager().findValidSpawnPosition(player.getPosition());
        assertNotNull(pUpPos);
        assertTrue(session.getTileMap().isTraversable(pUpPos.x, pUpPos.y, 18.0f),
                "PowerUp spawn position in session must be traversable on TileMap");
    }
}
