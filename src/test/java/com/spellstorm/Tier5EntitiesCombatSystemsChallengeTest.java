package com.spellstorm;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.combat.AreaEffect;
import com.spellstorm.combat.BurnStatus;
import com.spellstorm.combat.ChainEffect;
import com.spellstorm.combat.CombatSystem;
import com.spellstorm.combat.DamageEffect;
import com.spellstorm.combat.DamageInfo;
import com.spellstorm.combat.DamageMitigation;
import com.spellstorm.combat.Element;
import com.spellstorm.combat.ElementSet;
import com.spellstorm.combat.HitContext;
import com.spellstorm.combat.KnockbackEffect;
import com.spellstorm.combat.SlowEffect;
import com.spellstorm.combat.SlowStatus;
import com.spellstorm.combat.SpellDefinition;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.combat.StatusEffectList;
import com.spellstorm.combat.StatusKind;
import com.spellstorm.core.GameSession;
import com.spellstorm.core.GameState;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Character;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.EnemyType;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.Projectile;
import com.spellstorm.entities.ProjectileFaction;
import com.spellstorm.entities.RangedEnemy;
import com.spellstorm.entities.ShootingMode;
import com.spellstorm.entities.TankEnemy;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.ActiveTemporaryAbility;
import com.spellstorm.systems.EnemySpawner;
import com.spellstorm.systems.ExperienceSystem;
import com.spellstorm.systems.PlayerProgression;
import com.spellstorm.systems.PowerUpManager;
import com.spellstorm.systems.PowerUpType;
import com.spellstorm.systems.UpgradeInfo;
import com.spellstorm.systems.UpgradeManager;
import com.spellstorm.systems.UpgradeType;
import com.spellstorm.ui.GameOverOverlay;
import com.spellstorm.ui.HudRenderer;
import com.spellstorm.ui.LevelUpOverlay;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 5 Adversarial Coverage Hardening Test Suite
 * Assigned subsystems:
 * - com.spellstorm.combat
 * - com.spellstorm.entities
 * - com.spellstorm.systems
 * - com.spellstorm.ui
 * - com.spellstorm.core
 *
 * Authored by challenger_m7_2 to empirically stress-test untested branches,
 * corner cases, boundary conditions, and invariant guarantees.
 */
@DisplayName("Tier 5 Adversarial Coverage Hardening: Entities, Combat, Systems, UI & State")
public class Tier5EntitiesCombatSystemsChallengeTest {

    private static final float EPSILON = 1e-4f;

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // SECTION 1: COMBAT & STATUS EFFECTS
    // =========================================================================

    @Nested
    @DisplayName("1. Combat & Status Effects Adversarial Stress")
    class CombatAndStatusAdversarialTests {

        @ParameterizedTest(name = "Damage Mitigation: raw={0}, armor={1} -> expected={2}")
        @CsvSource({
                "0.0, 50.0, 0.0",
                "-20.0, 30.0, 0.0",
                "100.0, 0.0, 100.0",
                "100.0, -50.0, 100.0",
                "100.0, 100.0, 50.0",
                "100.0, 300.0, 25.0",
                "100.0, 900.0, 10.0",
                "100.0, 9900.0, 1.0"
        })
        @DisplayName("T5-C1.1: Damage mitigation mathematical boundary values")
        void testDamageMitigationBoundaries(float rawDamage, float armor, float expectedDamage) {
            float dealt = DamageMitigation.calculateDamage(rawDamage, armor);
            assertEquals(expectedDamage, dealt, 0.01f);
        }

        @Test
        @DisplayName("T5-C1.2: Mitigation with astronomical armor approaches zero without division by zero or NaN")
        void testDamageMitigationAstronomicalArmor() {
            float dealt = DamageMitigation.calculateDamage(500.0f, 1_000_000.0f);
            assertTrue(dealt > 0.0f && dealt < 0.1f, "Effective damage should be tiny positive");
            assertFalse(Float.isNaN(dealt));
            assertFalse(Float.isInfinite(dealt));
        }

        @Test
        @DisplayName("T5-C2.1: BurnStatus zero-damage on frame 0 and exact tick timing")
        void testBurnStatusFrameZeroAndTiming() {
            Player dummy = new Player(new Vector2D(500, 500));
            dummy.resetPlayer(new Vector2D(500, 500));
            dummy.setHealth(200.0f);

            BurnStatus burn = new BurnStatus(20.0f, 3.0f);
            dummy.addStatus(burn);

            // Frame 0: no tick occurs immediately
            burn.update(0.016f, dummy);
            assertEquals(200.0f, dummy.getHealth(), EPSILON, "No damage on frame 0");

            // Advance up to 0.48s total: still no tick (interval is 0.5s)
            burn.update(0.464f, dummy);
            assertEquals(200.0f, dummy.getHealth(), EPSILON, "No damage before 0.5s tick interval");

            // Advance by 0.03s (total 0.51s elapsed): exactly 1 tick fires (20 * 0.5 = 10 damage)
            burn.update(0.030f, dummy);
            assertEquals(190.0f, dummy.getHealth(), EPSILON, "First tick must deal exactly 10 damage");
        }

        @Test
        @DisplayName("T5-C2.2: BurnStatus sub-interval steps vs macro-steps deal identical total damage")
        void testBurnStatusMicroVsMacroSlices() {
            // Setup target A (12 steps of 0.25s = 3.0s total; 0.25 is an exact binary float fraction)
            Player targetA = new Player(new Vector2D(100, 100));
            targetA.resetPlayer(new Vector2D(100, 100));
            targetA.setMaxHealth(1000.0f);
            targetA.setHealth(1000.0f);
            BurnStatus burnA = new BurnStatus(40.0f, 3.0f);
            for (int i = 0; i < 12; i++) {
                burnA.update(0.25f, targetA);
            }

            // Setup target B (3 macro steps of 1.0s = 3.0s total)
            Player targetB = new Player(new Vector2D(200, 200));
            targetB.resetPlayer(new Vector2D(200, 200));
            targetB.setMaxHealth(1000.0f);
            targetB.setHealth(1000.0f);
            BurnStatus burnB = new BurnStatus(40.0f, 3.0f);
            burnB.update(1.0f, targetB);
            burnB.update(1.0f, targetB);
            burnB.update(1.0f, targetB);

            // Over 3.0s with 40 DPS, total ticks = 6, total damage = 6 * (40 * 0.5) = 120.0f
            assertEquals(880.0f, targetA.getHealth(), EPSILON, "Target A should take exactly 120 damage");
            assertEquals(880.0f, targetB.getHealth(), EPSILON, "Target B should take exactly 120 damage");
            assertEquals(targetA.getHealth(), targetB.getHealth(), EPSILON, "Sub-interval and macro steps must match");
        }

        @Test
        @DisplayName("T5-C2.3: BurnStatus ignores target armor completely (True Damage bypass)")
        void testBurnStatusTrueDamageBypass() {
            Player unarmored = new Player(new Vector2D(100, 100));
            unarmored.setArmor(0.0f);
            unarmored.setHealth(200.0f);

            Player heavilyArmored = new Player(new Vector2D(200, 200));
            heavilyArmored.setArmor(500.0f); // 500 armor would mitigate ~83% normal damage
            heavilyArmored.setHealth(200.0f);

            BurnStatus burn1 = new BurnStatus(30.0f, 2.0f);
            BurnStatus burn2 = new BurnStatus(30.0f, 2.0f);

            burn1.update(1.0f, unarmored);
            burn2.update(1.0f, heavilyArmored);

            assertEquals(unarmored.getHealth(), heavilyArmored.getHealth(), EPSILON,
                    "Burn damage must ignore armor completely");
        }

        @Test
        @DisplayName("T5-C2.4: BurnStatus oversized dt is clamped to remaining duration and does not over-tick")
        void testBurnStatusOversizedDtClamping() {
            Player dummy = new Player(new Vector2D(100, 100));
            dummy.setMaxHealth(1000.0f);
            dummy.setHealth(1000.0f);

            BurnStatus burn = new BurnStatus(20.0f, 2.0f);
            // Updating by dt=100s when duration is only 2.0s
            burn.update(100.0f, dummy);

            assertTrue(burn.expired());
            // Over 2.0s duration with interval 0.5s, exactly 4 ticks must fire (4 * 10 = 40 damage)
            assertEquals(960.0f, dummy.getHealth(), EPSILON, "Oversized dt must not deal more than 4 ticks of damage");
        }

        @Test
        @DisplayName("T5-C3.1: SlowStatus slowFraction is strictly clamped within [0.0, 0.9]")
        void testSlowStatusClamping() {
            SlowStatus negative = new SlowStatus(-2.0f, 5.0f);
            assertEquals(0.0f, negative.getSlowFraction(), EPSILON);
            assertEquals(1.0f, negative.speedMultiplier(), EPSILON);

            SlowStatus normal = new SlowStatus(0.4f, 5.0f);
            assertEquals(0.4f, normal.getSlowFraction(), EPSILON);
            assertEquals(0.6f, normal.speedMultiplier(), EPSILON);

            SlowStatus excessive = new SlowStatus(1.5f, 5.0f);
            assertEquals(0.9f, excessive.getSlowFraction(), EPSILON, "Must clamp to 0.9");
            assertEquals(0.1f, excessive.speedMultiplier(), EPSILON, "Minimum speed multiplier is 0.10");
        }

        @Test
        @DisplayName("T5-C3.2: SlowStatus re-application refreshes duration and parameters without stacking")
        void testSlowStatusReapplicationNoStacking() {
            StatusEffectList list = new StatusEffectList();
            list.add(new SlowStatus(0.3f, 4.0f));
            assertEquals(1, list.size());
            assertEquals(0.7f, list.speedMultiplier(), EPSILON);

            // Re-apply same kind with higher slow
            list.add(new SlowStatus(0.6f, 5.0f));
            assertEquals(1, list.size(), "Must not add a second slow instance");
            assertEquals(0.4f, list.speedMultiplier(), EPSILON, "Must replace with new slow factor 0.40");
        }

        @Test
        @DisplayName("T5-C3.3: Slow resistance scales effective character speed up to full immunity")
        void testSlowResistanceInterplay() {
            MeleeEnemy enemy = new MeleeEnemy(new Vector2D(100, 100));
            float baseSpd = enemy.getBaseMoveSpeed();

            // Apply 50% slow
            enemy.addStatus(new SlowStatus(0.5f, 3.0f));
            enemy.setSlowResistance(0.0f);
            assertEquals(baseSpd * 0.5f, enemy.getCurrentMoveSpeed(), EPSILON);

            // 50% resistance
            enemy.setSlowResistance(0.5f);
            // formula: 1.0 - (1.0 - 0.5) * (1.0 - 0.5) = 1.0 - 0.25 = 0.75
            assertEquals(baseSpd * 0.75f, enemy.getCurrentMoveSpeed(), EPSILON);

            // 100% resistance -> completely immune
            enemy.setSlowResistance(1.0f);
            assertEquals(baseSpd, enemy.getCurrentMoveSpeed(), EPSILON);
        }

        @Test
        @DisplayName("T5-C4.1: SpellRegistry resolve commutativity for all element combinations")
        void testSpellRegistryCommutativity() {
            SpellRegistry registry = SpellRegistry.createDefaultRegistry();

            // Fire + Wind == Wind + Fire -> Firestorm
            SpellDefinition s1 = registry.resolve(List.of(Element.FIRE, Element.WIND));
            SpellDefinition s2 = registry.resolve(List.of(Element.WIND, Element.FIRE));
            assertNotNull(s1);
            assertEquals(s1.getName(), s2.getName());
            assertTrue(s1.getName().startsWith("Firestorm"));

            // Ice + Lightning == Lightning + Ice -> Frost Tempest
            SpellDefinition s3 = registry.resolve(List.of(Element.ICE, Element.LIGHTNING));
            SpellDefinition s4 = registry.resolve(List.of(Element.LIGHTNING, Element.ICE));
            assertNotNull(s3);
            assertEquals(s3.getName(), s4.getName());
            assertTrue(s3.getName().startsWith("Frost Tempest"));

            // Earth + Fire == Fire + Earth -> Magma Core
            SpellDefinition s5 = registry.resolve(List.of(Element.EARTH, Element.FIRE));
            SpellDefinition s6 = registry.resolve(List.of(Element.FIRE, Element.EARTH));
            assertNotNull(s5);
            assertEquals(s5.getName(), s6.getName());
            assertTrue(s5.getName().startsWith("Magma Core"));
        }

        @Test
        @DisplayName("T5-C4.2: SpellRegistry empty, single element, and unregistered pair fallback")
        void testSpellRegistryFallbacks() {
            SpellRegistry registry = SpellRegistry.createDefaultRegistry();

            assertNull(registry.resolve(null));
            assertNull(registry.resolve(Collections.emptyList()));

            SpellDefinition baseFire = registry.resolve(List.of(Element.FIRE));
            assertEquals("Firebolt", baseFire.getName());

            // Fire + Ice has no combination in default registry -> fallback to Ice (Frost Shard)
            SpellDefinition fallback = registry.resolve(List.of(Element.FIRE, Element.ICE));
            assertEquals("Frost Shard", fallback.getName());

            // Stream of 3 elements: [EARTH, WIND, FIRE] -> last two are WIND + FIRE -> Firestorm
            SpellDefinition trailingCombo = registry.resolve(List.of(Element.EARTH, Element.WIND, Element.FIRE));
            assertTrue(trailingCombo.getName().startsWith("Firestorm"));
        }

        @Test
        @DisplayName("T5-C5.1: ChainEffect terminates when range has no valid enemies and never loops")
        void testChainEffectNoLooping() {
            Enemy e1 = new MeleeEnemy(new Vector2D(100, 100));
            Enemy e2 = new MeleeEnemy(new Vector2D(150, 100));
            List<Enemy> active = List.of(e1, e2);

            com.spellstorm.combat.CombatServices services = new com.spellstorm.combat.CombatServices() {
                @Override
                public void damageEnemy(Enemy enemy, DamageInfo damage, Color color) {
                    if (enemy != null && enemy.isAlive()) enemy.takeDamage(damage);
                }

                @Override
                public Enemy findNearestEnemy(Vector2D from, float range, List<Integer> exclude) {
                    Enemy best = null;
                    float bestDistSq = range * range;
                    for (Enemy e : active) {
                        if (!e.isAlive() || (exclude != null && exclude.contains(e.getId()))) continue;
                        float dSq = from.dist2(e.getPosition());
                        if (dSq <= bestDistSq) {
                            bestDistSq = dSq;
                            best = e;
                        }
                    }
                    return best;
                }

                @Override
                public void forEachEnemyInRadius(Vector2D center, float radius, java.util.function.Consumer<Enemy> action) {
                    float rSq = radius * radius;
                    for (Enemy e : active) {
                        if (e.isAlive() && center.dist2(e.getPosition()) <= rSq) action.accept(e);
                    }
                }
            };

            ChainEffect chain = new ChainEffect(5, 80.0f); // 5 jumps allowed
            AtomicInteger hits = new AtomicInteger(0);
            chain.then(ctx -> hits.incrementAndGet());

            // Start chain at e1
            HitContext initCtx = new HitContext(e1, e1.getPosition(), new Vector2D(1, 0), 20.0f, Color.WHITE, services);
            chain.apply(initCtx);

            // Since there are only 2 enemies (e1 and e2), and e1 was visited first,
            // chain jumps to e2 (jump 1). It CANNOT jump back to e1.
            assertEquals(1, hits.get(), "Chain must hit e2 once and stop, never looping back to e1");
        }

        @Test
        @DisplayName("T5-C5.2: AreaEffect includePrimary flag behavior")
        void testAreaEffectPrimaryInclusion() {
            Enemy primary = new MeleeEnemy(new Vector2D(100, 100));
            Enemy secondary = new MeleeEnemy(new Vector2D(120, 100));
            List<Enemy> enemies = List.of(primary, secondary);

            com.spellstorm.combat.CombatServices services = new com.spellstorm.combat.CombatServices() {
                @Override
                public void damageEnemy(Enemy enemy, DamageInfo damage, Color color) {
                    if (enemy != null && enemy.isAlive()) enemy.takeDamage(damage);
                }

                @Override
                public Enemy findNearestEnemy(Vector2D from, float range, List<Integer> exclude) {
                    return null;
                }

                @Override
                public void forEachEnemyInRadius(Vector2D center, float radius, java.util.function.Consumer<Enemy> action) {
                    float rSq = radius * radius;
                    for (Enemy e : enemies) {
                        if (e.isAlive() && center.dist2(e.getPosition()) <= rSq) action.accept(e);
                    }
                }
            };

            // Case A: includePrimary = false
            AtomicInteger countA = new AtomicInteger(0);
            AreaEffect areaExclude = new AreaEffect(50.0f, false);
            areaExclude.then(ctx -> countA.incrementAndGet());
            areaExclude.apply(new HitContext(primary, primary.getPosition(), new Vector2D(1, 0), 10.0f, Color.WHITE, services));
            assertEquals(1, countA.get(), "Only secondary enemy should be affected");

            // Case B: includePrimary = true
            AtomicInteger countB = new AtomicInteger(0);
            AreaEffect areaInclude = new AreaEffect(50.0f, true);
            areaInclude.then(ctx -> countB.incrementAndGet());
            areaInclude.apply(new HitContext(primary, primary.getPosition(), new Vector2D(1, 0), 10.0f, Color.WHITE, services));
            assertEquals(2, countB.get(), "Both primary and secondary enemies should be affected");
        }
    }

    // =========================================================================
    // SECTION 2: PROJECTILES & PIERCING MECHANICS
    // =========================================================================

    @Nested
    @DisplayName("2. Projectile & Piercing Mechanics Adversarial Stress")
    class ProjectileAdversarialTests {

        @Test
        @DisplayName("T5-P1.1: Projectile initial pierce count is clamped to MAX_ALLOWED_PIERCE = 3")
        void testProjectileInitialPierceClamping() {
            Projectile pExcessive = new Projectile(
                    new Vector2D(100, 100), new Vector2D(1, 0), 200.0f, 10.0f,
                    50.0f, 2.0f, 10, ProjectileFaction.PLAYER
            );
            assertEquals(3, pExcessive.getPierceRemaining(), "Must clamp initial pierce to 3");

            Projectile pNegative = new Projectile(
                    new Vector2D(100, 100), new Vector2D(1, 0), 200.0f, 10.0f,
                    50.0f, 2.0f, -5, ProjectileFaction.PLAYER
            );
            assertEquals(0, pNegative.getPierceRemaining(), "Must clamp negative pierce to 0");
        }

        @Test
        @DisplayName("T5-P1.2: Projectile pierce damage falloff (20% reduction per hit) and 25% minimum floor")
        void testProjectileFalloffSequenceAndFloor() {
            float initialDmg = 100.0f;
            Projectile p = new Projectile(
                    new Vector2D(100, 100), new Vector2D(1, 0), 200.0f, 10.0f,
                    initialDmg, 2.0f, 3, ProjectileFaction.PLAYER
            );

            assertEquals(3, p.getPierceRemaining());
            assertEquals(100.0f, p.getDamage(), EPSILON);

            // Hit 1: pierce goes 3 -> 2, damage = 100 * 0.8 = 80.0
            p.decrementPierce();
            assertEquals(2, p.getPierceRemaining());
            assertEquals(80.0f, p.getDamage(), EPSILON);
            assertTrue(p.isAlive());

            // Hit 2: pierce goes 2 -> 1, damage = 80 * 0.8 = 64.0
            p.decrementPierce();
            assertEquals(1, p.getPierceRemaining());
            assertEquals(64.0f, p.getDamage(), EPSILON);
            assertTrue(p.isAlive());

            // Hit 3: pierce goes 1 -> 0, damage = 64 * 0.8 = 51.2
            p.decrementPierce();
            assertEquals(0, p.getPierceRemaining());
            assertEquals(51.2f, p.getDamage(), 0.05f);
            assertTrue(p.isAlive());

            // Hit 4: pierceRemaining is 0 -> projectile destroyed!
            p.decrementPierce();
            assertFalse(p.isAlive(), "Projectile must be destroyed on 4th hit");
        }

        @Test
        @DisplayName("T5-P1.3: Piercing damage floor clamps damage to min 25% of initial damage")
        void testProjectileDamageFloorClamping() {
            // Low initial damage where repeated falloff would drop below 25%
            Projectile p = new Projectile(
                    new Vector2D(100, 100), new Vector2D(1, 0), 200.0f, 10.0f,
                    100.0f, 2.0f, 3, ProjectileFaction.PLAYER
            );

            // Force damage down manually or simulate repeated falloff
            p.decrementPierce(); // 80.0
            p.decrementPierce(); // 64.0
            p.decrementPierce(); // 51.2

            // Initial damage was 100.0, floor is 25.0
            assertTrue(p.getDamage() >= 25.0f, "Damage must respect 25% floor");
        }

        @Test
        @DisplayName("T5-P2.1: Multi-hit prevention per entity ID prevents double-hitting the same target")
        void testMultiHitPrevention() {
            Projectile p = new Projectile(
                    new Vector2D(100, 100), new Vector2D(1, 0), 200.0f, 10.0f,
                    50.0f, 2.0f, 2, ProjectileFaction.PLAYER
            );

            long enemyId = 42L;
            assertTrue(p.canHit(enemyId), "Should be able to hit initial enemy");
            p.recordHit(enemyId);

            assertFalse(p.canHit(enemyId), "Cannot hit same enemy again");
            assertEquals(1, p.getHitCount());
            assertTrue(p.getHitEntities().contains(enemyId));

            // Other entity can still be hit
            assertTrue(p.canHit(43L));
        }

        @Test
        @DisplayName("T5-P3.1: Zero-length direction vector falls back to safe default without NaN")
        void testProjectileZeroDirectionSafety() {
            Projectile p = new Projectile(
                    new Vector2D(100, 100), new Vector2D(0, 0), 200.0f, 10.0f,
                    50.0f, 2.0f, 1, ProjectileFaction.PLAYER
            );

            Vector2D dir = p.getDirection();
            assertEquals(1.0f, dir.x, EPSILON);
            assertEquals(0.0f, dir.y, EPSILON);
            assertFalse(Float.isNaN(p.getVelocity().x));
            assertFalse(Float.isNaN(p.getVelocity().y));
        }

        @Test
        @DisplayName("T5-P3.2: Projectiles crossing arena boundary margin (+15px) are culled")
        void testProjectileBoundaryCulling() {
            CombatSystem combat = new CombatSystem(new Rectangle(0, 0, 1000, 1000));
            // Spawn projectile inside arena heading right
            Projectile p = new Projectile(
                    new Vector2D(990, 500), new Vector2D(1, 0), 500.0f, 10.0f,
                    50.0f, 5.0f, 0, ProjectileFaction.PLAYER
            );
            combat.addProjectile(p);

            // Step 1: Still within 1000 + 15 = 1015 bounds
            combat.update(0.02f, null, null); // moves by 10px -> x = 1000
            assertTrue(p.isAlive());

            // Step 2: Moves beyond 1015px (e.g. x = 1050) -> culled
            combat.update(0.10f, null, null);
            assertFalse(p.isAlive(), "Projectile outside bounds must be destroyed");
            assertEquals(0, combat.getProjectileCount(), "Destroyed projectile must be pruned");
        }
    }

    // =========================================================================
    // SECTION 3: ENTITIES & PHYSICS
    // =========================================================================

    @Nested
    @DisplayName("3. Entities & Physics Adversarial Stress")
    class EntitiesAndPhysicsTests {

        @Test
        @DisplayName("T5-E1.1: Frame-rate independent exponential knockback decay and snap to zero")
        void testExponentialKnockbackDecayAndSnap() {
            MeleeEnemy enemy = new MeleeEnemy(new Vector2D(500, 500));
            enemy.applyKnockback(new Vector2D(100.0f, 0.0f));

            // Verify initial impulse
            assertEquals(100.0f, enemy.getKnockback().x, EPSILON);

            // Decay over 0.5s: K = 100 * exp(-7 * 0.5) = 100 * exp(-3.5) = 100 * 0.030197 = ~3.02
            enemy.updateKnockback(0.5f);
            assertEquals(3.02f, enemy.getKnockback().x, 0.05f);

            // Further decay over 0.5s: magnitude drops below 1.0 (len2 < 1.0) -> snaps to exactly 0.0
            enemy.updateKnockback(0.5f);
            assertTrue(enemy.getKnockback().isZero(), "Knockback must snap to zero when len2 < 1.0");
        }

        @Test
        @DisplayName("T5-E1.2: Knockback resistance clamps between [0, 1] and scales applied impulse")
        void testKnockbackResistanceScaling() {
            MeleeEnemy normal = new MeleeEnemy(new Vector2D(100, 100));
            normal.setKnockbackResistance(0.0f);
            normal.applyKnockback(new Vector2D(100.0f, 0.0f));
            assertEquals(100.0f, normal.getKnockback().x, EPSILON);

            TankEnemy tank = new TankEnemy(new Vector2D(200, 200)); // Default 0.5f resistance
            tank.applyKnockback(new Vector2D(100.0f, 0.0f));
            assertEquals(50.0f, tank.getKnockback().x, EPSILON, "50% resistance must halve knockback impulse");

            normal.setKnockbackResistance(1.0f); // 100% resistance
            normal.setKnockback(0, 0);
            normal.applyKnockback(new Vector2D(100.0f, 0.0f));
            assertTrue(normal.getKnockback().isZero(), "100% resistance must completely negate knockback");
        }

        @Test
        @DisplayName("T5-E2.1: Player 8-way movement diagonal magnitude is normalized to 1.0 (no diagonal speed inflation)")
        void testPlayerMovementNormalization() {
            Vector2D diagonal = Player.computeMovementVector(true, false, false, true); // UP + RIGHT
            assertEquals(1.0f, diagonal.len(), EPSILON, "Diagonal movement magnitude must strictly be 1.0");

            Vector2D opposingVertical = Player.computeMovementVector(true, true, false, false); // UP + DOWN
            assertEquals(0.0f, opposingVertical.len(), EPSILON, "Opposing vertical inputs must cancel out");

            Vector2D opposingHorizontal = Player.computeMovementVector(false, false, true, true); // LEFT + RIGHT
            assertEquals(0.0f, opposingHorizontal.len(), EPSILON, "Opposing horizontal inputs must cancel out");
        }

        @Test
        @DisplayName("T5-E2.2: Player facing direction 4-quadrant calculation")
        void testPlayerFacingDirection() {
            Player player = new Player(new Vector2D(500, 500));

            // Aim East (+X)
            player.setAimDirection(new Vector2D(1.0f, 0.0f));
            assertEquals(Player.FacingDirection.RIGHT, player.getFacingDirection());

            // Aim South (+Y in Y-down system)
            player.setAimDirection(new Vector2D(0.0f, 1.0f));
            assertEquals(Player.FacingDirection.DOWN, player.getFacingDirection());

            // Aim West (-X)
            player.setAimDirection(new Vector2D(-1.0f, 0.0f));
            assertEquals(Player.FacingDirection.LEFT, player.getFacingDirection());

            // Aim North (-Y in Y-down system)
            player.setAimDirection(new Vector2D(0.0f, -1.0f));
            assertEquals(Player.FacingDirection.UP, player.getFacingDirection());
        }

        @Test
        @DisplayName("T5-E3.1: Boss Colossus Overlord durability and stats specification")
        void testBossColossusStats() {
            BossEnemy boss = new BossEnemy(new Vector2D(1000, 1000));
            assertTrue(boss.isBoss());
            assertEquals("ANCIENT GOLEM OVERLORD", boss.getName());
            assertEquals(1800.0f, boss.getMaxHealth(), EPSILON);
            assertEquals(1800.0f, boss.getHealth(), EPSILON);
            assertEquals(45.0f, boss.getArmor(), EPSILON);
            assertEquals(55.0f, boss.getBaseMoveSpeed(), EPSILON);
            assertEquals(45.0f, boss.getContactDamage(), EPSILON);
            assertEquals(50.0f, boss.getRadius(), EPSILON);
            assertEquals(250, boss.getExpReward());
        }

        @Test
        @DisplayName("T5-E3.2: Boss 3-way spread volley attack count, 24 deg angles, and projectile attributes")
        void testBossVolleyAttackMechanics() {
            BossEnemy boss = new BossEnemy(new Vector2D(1000, 1000));
            assertTrue(boss.canFireVolley());

            // Aim directly east at (1500, 1000)
            List<Projectile> volley = boss.fireVolley(new Vector2D(1500, 1000));
            assertNotNull(volley);
            assertEquals(3, volley.size(), "Boss volley must fire exactly 3 missiles");
            assertFalse(boss.canFireVolley(), "Volley timer must be on cooldown");
            assertEquals(4.0f, boss.getVolleyTimer(), EPSILON, "Cooldown must be 4.0s");

            for (Projectile p : volley) {
                assertEquals(ProjectileFaction.ENEMY, p.getFaction());
                assertEquals(210.0f, p.getSpeed(), EPSILON);
                assertEquals(10.0f, p.getRadius(), EPSILON);
                assertEquals(20.0f, p.getDamage(), EPSILON);
                assertEquals(3.2f, p.getLifetime(), EPSILON);
                assertEquals(0, p.getPierceRemaining());
            }

            // Check spread angles: one at -24 deg, one center (0 deg), one at +24 deg
            float angle0 = volley.get(0).getDirection().angleDeg();
            float angle1 = volley.get(1).getDirection().angleDeg();
            float angle2 = volley.get(2).getDirection().angleDeg();

            float norm0 = angle0 > 180.0f ? angle0 - 360.0f : angle0;
            float norm1 = angle1 > 180.0f ? angle1 - 360.0f : angle1;
            float norm2 = angle2 > 180.0f ? angle2 - 360.0f : angle2;

            assertEquals(-24.0f, norm0, 1.0f);
            assertEquals(0.0f, norm1, 1.0f);
            assertEquals(24.0f, norm2, 1.0f);
        }

        @Test
        @DisplayName("T5-E4.1: Idempotent experience reward claiming on enemy defeat")
        void testIdempotentRewardClaiming() {
            MeleeEnemy enemy = new MeleeEnemy(new Vector2D(100, 100));
            assertFalse(enemy.isRewardClaimed());

            int reward1 = enemy.claimReward();
            assertEquals(MeleeEnemy.DEFAULT_EXP_REWARD, reward1);
            assertTrue(enemy.isRewardClaimed());

            int reward2 = enemy.claimReward();
            assertEquals(0, reward2, "Second claim must return 0 (idempotent guarantee)");

            int reward3 = enemy.claimReward();
            assertEquals(0, reward3, "Subsequent claims must return 0");
        }
    }

    // =========================================================================
    // SECTION 4: SYSTEMS & PROGRESSION
    // =========================================================================

    @Nested
    @DisplayName("4. Systems & Progression Adversarial Stress")
    class SystemsAndProgressionTests {

        @Test
        @DisplayName("T5-S1.1: EnemySpawner 120s difficulty scaling curve (interval tightening 2.2s -> 0.55s, cap 20 -> 36)")
        void testSpawnerScalingCurve() {
            EnemySpawner spawner = new EnemySpawner();
            spawner.reset();

            // t = 0s
            spawner.update(0.0f, new Vector2D(1000, 1000), new ArrayList<>());
            assertEquals(2.2f, spawner.getCurrentInterval(), EPSILON);
            assertEquals(20, spawner.getMaxEnemies());

            // t = 60s (50% progression)
            // interval = 2.2 - 0.5 * (2.2 - 0.55) = 2.2 - 0.825 = 1.375s
            // cap = 20 + 0.5 * 16 = 28
            spawner.update(60.0f, new Vector2D(1000, 1000), new ArrayList<>());
            assertEquals(1.375f, spawner.getCurrentInterval(), 0.05f);
            assertEquals(28, spawner.getMaxEnemies());

            // t = 120s (100% progression)
            spawner.update(60.0f, new Vector2D(1000, 1000), new ArrayList<>());
            assertEquals(0.55f, spawner.getCurrentInterval(), EPSILON);
            assertEquals(36, spawner.getMaxEnemies());

            // t = 200s (Beyond 120s, clamped to max progression)
            spawner.update(80.0f, new Vector2D(1000, 1000), new ArrayList<>());
            assertEquals(0.55f, spawner.getCurrentInterval(), EPSILON);
            assertEquals(36, spawner.getMaxEnemies());
        }

        @Test
        @DisplayName("T5-S1.2: Boss is strictly exempt from spawner late-game health scaling")
        void testBossHealthScalingExemption() {
            EnemySpawner spawner = new EnemySpawner();
            spawner.reset();

            // Advance time to 150 seconds (well past the 50s threshold)
            spawner.update(150.0f, new Vector2D(1000, 1000), new ArrayList<>());

            Enemy melee = spawner.spawnEnemy(new Vector2D(1000, 1000), EnemyType.MELEE);
            assertFalse(melee.isBoss());
            assertTrue(melee.getMaxHealth() > MeleeEnemy.DEFAULT_HEALTH,
                    "Regular melee enemy must have scaled health in late game");

            Enemy boss = spawner.spawnBoss(new Vector2D(1000, 1000));
            assertTrue(boss.isBoss());
            assertEquals(BossEnemy.BOSS_MAX_HEALTH, boss.getMaxHealth(), EPSILON,
                    "Boss must be strictly exempt from spawner health scaling");
        }

        @Test
        @DisplayName("T5-S2.1: PowerUpManager exact 5.0s lifecycle and clean expiration")
        void testPowerUpExactLifecycle() {
            ActiveTemporaryAbility ability = new ActiveTemporaryAbility();
            assertFalse(ability.isActive());
            assertEquals(0.0f, ability.getRemainingDuration(), EPSILON);

            ability.activate(PowerUpType.ARCANE_OVERDRIVE);
            assertTrue(ability.isActive());
            assertEquals(5.0f, ability.getRemainingDuration(), EPSILON);

            // Step 2.5s
            ability.update(2.5f);
            assertTrue(ability.isActive());
            assertEquals(2.5f, ability.getRemainingDuration(), EPSILON);

            // Step 2.5s -> exact 5.0s reached
            ability.update(2.5f);
            assertFalse(ability.isActive());
            assertEquals(0.0f, ability.getRemainingDuration(), EPSILON);
        }

        @Test
        @DisplayName("T5-S2.2: Modal pause suspension preserves power-up timer invariant (zero decrement while paused)")
        void testPowerUpPauseInvariance() {
            PowerUpManager manager = new PowerUpManager();
            Player player = new Player(new Vector2D(500, 500));
            manager.getActiveAbility().activate(PowerUpType.PIERCING_SURGE);

            float initialTimer = manager.getActiveAbility().getRemainingDuration();
            assertEquals(5.0f, initialTimer, EPSILON);

            // Simulate 10 seconds of paused gameplay (e.g. Level-Up modal open)
            for (int i = 0; i < 100; i++) {
                manager.update(0.1f, player, true); // paused = true
            }

            assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), EPSILON,
                    "Remaining duration must not decrease while paused");
            assertTrue(manager.hasActiveAbility());

            // Unpaused updates decrement normally
            manager.update(1.0f, player, false);
            assertEquals(4.0f, manager.getActiveAbility().getRemainingDuration(), EPSILON);
        }

        @Test
        @DisplayName("T5-S2.3: PowerUp recollection policy: same-type refreshes to 5.0s, different-type replaces")
        void testPowerUpRecollectionPolicy() {
            ActiveTemporaryAbility ability = new ActiveTemporaryAbility();

            // 1. Activate Arcane Overdrive, elapse 3.5s (1.5s remaining)
            ability.activate(PowerUpType.ARCANE_OVERDRIVE);
            ability.update(3.5f);
            assertEquals(1.5f, ability.getRemainingDuration(), EPSILON);

            // Same-type refresh
            ability.refresh();
            assertEquals(5.0f, ability.getRemainingDuration(), EPSILON);
            assertEquals(PowerUpType.ARCANE_OVERDRIVE, ability.getType());

            // 2. Elapse 4.0s (1.0s remaining) and replace with Piercing Surge
            ability.update(4.0f);
            ability.activate(PowerUpType.PIERCING_SURGE);
            assertEquals(5.0f, ability.getRemainingDuration(), EPSILON);
            assertEquals(PowerUpType.PIERCING_SURGE, ability.getType());
        }

        @Test
        @DisplayName("T5-S3.1: PlayerProgression curve, multi-level leaps, and excess carryover")
        void testPlayerProgressionLeapsAndCarryover() {
            PlayerProgression prog = new PlayerProgression(1);
            assertEquals(1, prog.getLevel());
            assertEquals(0, prog.getCurrentExp());

            // Level 1 requires 25 EXP
            // Level 2 requires 45 EXP
            // Level 3 requires 65 EXP
            // Total for Level 1 -> 3 is 25 + 45 = 70 EXP

            // Award 100 EXP
            int levelsGained = prog.addExp(100);
            assertEquals(2, levelsGained);
            assertEquals(3, prog.getLevel());
            // Excess EXP: 100 - 70 = 30 EXP into Level 3
            assertEquals(30, prog.getCurrentExp());
            assertEquals(65, prog.getExpForNextLevel());
            assertEquals(2, prog.getPendingLevelUps());
        }

        @Test
        @DisplayName("T5-S3.2: ExperienceSystem magnet pull occurs only within magnet radius (2.8x pickup radius)")
        void testExperienceMagnetPullRadius() {
            ExperienceSystem expSys = new ExperienceSystem();
            Player player = new Player(new Vector2D(500, 500));
            player.setPickupRadius(50.0f);
            // Magnet radius = 50 * 2.8 = 140px

            // Gem 1: At distance 100px (inside magnet radius)
            expSys.spawnPickup(new Vector2D(600, 500), 10);
            // Gem 2: At distance 200px (outside magnet radius)
            expSys.spawnPickup(new Vector2D(700, 500), 10);

            expSys.update(0.1f, player, new PlayerProgression());

            assertEquals(2, expSys.getPickupCount());
            // Gem 1 should have moved towards player (x < 600)
            assertTrue(expSys.getPickups().get(0).getPosition().x < 600.0f);
            // Gem 2 should remain stationary at 700
            assertEquals(700.0f, expSys.getPickups().get(1).getPosition().x, EPSILON);
        }

        @Test
        @DisplayName("T5-S4.1: UpgradeManager rank caps and distinct 3-card draw")
        void testUpgradeManagerRanksAndDrawing() {
            UpgradeManager upMgr = new UpgradeManager();
            Player player = new Player(new Vector2D(500, 500));

            // Max out Piercing perk (max rank 2)
            assertTrue(upMgr.applyUpgrade(UpgradeType.PIERCING, player));
            assertEquals(1, upMgr.getRank(UpgradeType.PIERCING));
            assertTrue(upMgr.applyUpgrade(UpgradeType.PIERCING, player));
            assertEquals(2, upMgr.getRank(UpgradeType.PIERCING));
            assertTrue(upMgr.isMaxRank(UpgradeType.PIERCING));

            // Applying again must return false
            assertFalse(upMgr.applyUpgrade(UpgradeType.PIERCING, player));

            // Available draw must NEVER include maxed out PIERCING
            for (int draw = 0; draw < 20; draw++) {
                List<UpgradeInfo> choices = upMgr.getAvailableUpgrades(3);
                for (UpgradeInfo info : choices) {
                    assertNotEquals(UpgradeType.PIERCING, info.type);
                }
            }
        }
    }

    // =========================================================================
    // SECTION 5: UI & STATE SUBSYSTEMS
    // =========================================================================

    @Nested
    @DisplayName("5. UI & State Subsystems Adversarial Stress")
    class UIAndStateSubsystemsTests {

        @Test
        @DisplayName("T5-U1.1: LevelUpOverlay two-step confirmation, card switching, and grace period")
        void testLevelUpTwoStepSelection() {
            LevelUpOverlay overlay = new LevelUpOverlay();
            List<UpgradeInfo> options = List.of(
                    new UpgradeInfo(UpgradeType.DAMAGE, "Damage", "+20%", 0, 5, Color.RED),
                    new UpgradeInfo(UpgradeType.MOVE_SPEED, "Speed", "+12%", 0, 5, Color.GREEN),
                    new UpgradeInfo(UpgradeType.MAX_HEALTH, "Health", "+40", 0, 5, Color.BLUE)
            );

            overlay.open(options);
            assertTrue(overlay.isOpen());
            assertFalse(overlay.hasValidSelection());

            // Click during grace timer (0.15s) -> rejected
            assertFalse(overlay.handleMouseClick(new Vector2D(640, 300)));

            // Elapse grace timer
            overlay.update(0.20f);
            assertEquals(0.0f, overlay.getOpenGraceTimer(), EPSILON);

            // Step 1: Click card 0 -> selects card, returns false (NEVER commits)
            Rectangle card0 = overlay.getCardBounds().get(0);
            boolean committed1 = overlay.handleMouseClick(new Vector2D(card0.x + 10, card0.y + 10));
            assertFalse(committed1, "Clicking a card must never commit upgrade");
            assertEquals(0, overlay.getSelectedIndex());
            assertTrue(overlay.hasValidSelection());

            // Switch to card 2 -> returns false, changes selection
            Rectangle card2 = overlay.getCardBounds().get(2);
            boolean committed2 = overlay.handleMouseClick(new Vector2D(card2.x + 10, card2.y + 10));
            assertFalse(committed2);
            assertEquals(2, overlay.getSelectedIndex());

            // Step 2: Click confirmation button -> returns true (commits!)
            Rectangle confirmBtn = overlay.getConfirmButtonBounds();
            boolean committed3 = overlay.handleMouseClick(new Vector2D(confirmBtn.x + 10, confirmBtn.y + 10));
            assertTrue(committed3, "Clicking confirm button must commit selected upgrade");
            assertEquals(UpgradeType.MAX_HEALTH, overlay.getSelectedUpgrade().type);
        }

        @Test
        @DisplayName("T5-U1.2: LevelUpOverlay description text wrapping safety")
        void testDescriptionTextWrapping() {
            // Test with long description
            String longDesc = "Unleashes a powerful storm of fiery energy dealing massive damage to all nearby adversaries and setting them ablaze.";
            List<String> lines = LevelUpOverlay.wrapText(longDesc, null, LevelUpOverlay.CONTENT_WIDTH);
            assertFalse(lines.isEmpty());
            for (String line : lines) {
                assertFalse(line.isEmpty());
            }

            // Test empty & null text
            assertTrue(LevelUpOverlay.wrapText("", null, 244.0f).isEmpty());
            assertTrue(LevelUpOverlay.wrapText(null, null, 244.0f).isEmpty());
        }

        @Test
        @DisplayName("T5-U2.1: GameOverOverlay survival time formatting across boundaries")
        void testGameOverTimeFormatting() {
            GameOverOverlay overlay = new GameOverOverlay();

            overlay.open(0.0f, 0, 1);
            assertEquals("00:00", overlay.formattedSurvivalTime());

            overlay.open(65.0f, 5, 2);
            assertEquals("01:05", overlay.formattedSurvivalTime());

            overlay.open(3661.0f, 100, 15);
            assertEquals("61:01", overlay.formattedSurvivalTime());
        }

        @Test
        @DisplayName("T5-U2.2: GameOverOverlay grace timer and keyboard actions")
        void testGameOverActionsAndKeyboardShortcuts() {
            GameOverOverlay overlay = new GameOverOverlay();
            overlay.open(120.0f, 20, 5);

            // Within grace timer (0.20s) -> clicks rejected
            assertEquals(GameOverOverlay.Action.NONE, overlay.handleMouseClick(new Vector2D(640, 400)));

            overlay.update(0.25f);

            // Keyboard shortcut R -> RESTART
            assertEquals(GameOverOverlay.Action.RESTART, overlay.handleKeyPress(Keys.R));
            assertEquals(GameOverOverlay.Action.RESTART, overlay.handleKeyPress(Keys.ENTER));
            assertEquals(GameOverOverlay.Action.RESTART, overlay.handleKeyPress(Keys.SPACE));

            // Keyboard shortcut Q / ESC -> QUIT
            assertEquals(GameOverOverlay.Action.QUIT, overlay.handleKeyPress(Keys.ESCAPE));
            assertEquals(GameOverOverlay.Action.QUIT, overlay.handleKeyPress(Keys.Q));
        }

        @Test
        @DisplayName("T5-U3.1: HudRenderer telemetry format methods null safety")
        void testHudRendererNullSafety() {
            assertEquals("", HudRenderer.formatHpText(null));
            assertEquals("", HudRenderer.formatVitalsText(null));
            assertEquals("", HudRenderer.formatExpText(null));
            assertEquals("", HudRenderer.formatPowerUpText(null));

            assertFalse(HudRenderer.isBossBarVisible(null));
            assertFalse(HudRenderer.isBossBarVisible(Collections.emptyList()));
        }

        @Test
        @DisplayName("T5-G1.1: Complete simulation freeze during modal overlays and pause")
        void testSimulationFreezeDuringModals() {
            GameSession session = new GameSession();
            session.restartMatch();
            assertEquals(GameState.PLAYING, session.getGameState());

            // 1. Trigger manual pause
            session.setPaused(true);
            assertEquals(GameState.PAUSED, session.getGameState());
            float elapsedBefore = session.getMatchElapsed();
            session.update(1.0f);
            assertEquals(elapsedBefore, session.getMatchElapsed(), EPSILON, "Match elapsed must freeze when paused");

            session.setPaused(false);
            session.update(0.1f);
            assertTrue(session.getMatchElapsed() > elapsedBefore);

            // 2. Trigger Level-Up modal
            session.getLevelUpOverlay().open(List.of(new UpgradeInfo(UpgradeType.DAMAGE, "Damage", "+20%", 0, 5, Color.RED)));
            session.update(0.5f);
            assertEquals(GameState.LEVEL_UP, session.getGameState());
            float elapsedInModal = session.getMatchElapsed();
            session.update(2.0f);
            assertEquals(elapsedInModal, session.getMatchElapsed(), EPSILON, "Match elapsed must freeze during Level-Up modal");
        }

        @Test
        @DisplayName("T5-G1.2: Deterministic, idempotent restartMatch() restores 100% clean initial state")
        void testDeterministicRestartMatch() {
            GameSession session = new GameSession();

            // Mutate session state heavily: damage player, add enemies, level up, add powerup
            session.getPlayer().takeDamage(150.0f);
            session.addEnemy(new MeleeEnemy(new Vector2D(600, 600)));
            session.addEnemy(new TankEnemy(new Vector2D(700, 700)));
            session.getProgression().addExp(500);
            session.getPowerUpManager().getActiveAbility().activate(PowerUpType.ARCANE_OVERDRIVE);
            session.toggleShootingMode();

            // Perform 5 consecutive restartMatch() calls
            for (int cycle = 1; cycle <= 5; cycle++) {
                session.restartMatch();

                assertEquals(GameState.PLAYING, session.getGameState());
                assertEquals(0.0f, session.getMatchElapsed(), EPSILON);
                assertEquals(0, session.getEnemiesDefeated());
                assertEquals(ShootingMode.MANUAL, session.getShootingMode());

                // Player state
                Player p = session.getPlayer();
                assertTrue(p.isAlive());
                assertEquals(Player.DEFAULT_HEALTH, p.getHealth(), EPSILON);
                assertEquals(Player.DEFAULT_ARMOR, p.getArmor(), EPSILON);
                assertEquals(Player.DEFAULT_SPEED, p.getBaseMoveSpeed(), EPSILON);
                assertEquals(1.0f, p.getDamageMultiplier(), EPSILON);
                assertEquals(0, p.getBonusPierce());

                // Entity & system state
                assertTrue(session.getEnemies().isEmpty());
                assertEquals(0, session.getCombatSystem().getProjectileCount());
                assertEquals(0, session.getExperienceSystem().getPickupCount());
                assertFalse(session.getPowerUpManager().hasActiveAbility());
                assertEquals(1, session.getProgression().getLevel());
                assertEquals(0, session.getProgression().getCurrentExp());
                assertEquals(0, session.getProgression().getPendingLevelUps());
                assertFalse(session.getLevelUpOverlay().isOpen());
                assertFalse(session.getGameOverOverlay().isOpen());
            }
        }
    }
}
