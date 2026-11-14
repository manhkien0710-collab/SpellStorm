package com.spellstorm.p0;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.combat.DamageMitigation;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.core.GameSession;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Player;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P0 Integration tests for Defect D4: Contact damage duplicate mitigation.
 * Verifies that armor mitigation is applied exactly once to contact damage,
 * avoiding duplicate mitigation between GameSession and Character.takeDamage.
 */
public class ContactDamageIntegrationTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("D4.1: Raw contact damage 15 vs player armor 30 results in 11.5385 +/- 1e-4 damage in GameSession")
    public void testPlayerContactDamage15VsArmor30() {
        Rectangle bounds = new Rectangle(0, 0, 4096, 4096);
        SpellRegistry registry = SpellRegistry.createDefaultRegistry();
        GameSession session = new GameSession(bounds, registry, 42L);

        Player player = session.getPlayer();
        player.setHealth(100.0f);
        player.setArmor(30.0f);

        // Place a melee enemy directly overlapping player
        Vector2D pPos = player.getPosition();
        MeleeEnemy melee = new MeleeEnemy(pPos.cpy());
        melee.setContactDamage(15.0f);
        session.addEnemy(melee);

        // Update session by 1 frame (0.016s) to trigger contact damage
        session.update(0.016f, new Vector2D(pPos.x + 100, pPos.y), new Vector2D(0, 0), false);

        // Expected damage with single mitigation: 15 * (100 / (100 + 30)) = 1500 / 130 = 11.5384615...
        float expectedDamage = 15.0f * (100.0f / (100.0f + 30.0f));
        float actualDamage = 100.0f - player.getHealth();

        assertEquals(11.5385f, actualDamage, 1e-4f,
                "Contact damage 15 against armor 30 must be mitigated once to 11.5385, not twice (8.8757)");
        assertEquals(expectedDamage, actualDamage, 1e-4f);
    }

    @Test
    @DisplayName("D4.2: Boss contact damage 45 vs player armor 30 results in 34.6154 +/- 1e-4 damage in GameSession")
    public void testBossContactDamage45VsArmor30() {
        Rectangle bounds = new Rectangle(0, 0, 4096, 4096);
        SpellRegistry registry = SpellRegistry.createDefaultRegistry();
        GameSession session = new GameSession(bounds, registry, 42L);

        Player player = session.getPlayer();
        player.setHealth(100.0f);
        player.setArmor(30.0f);

        // Place BossEnemy directly overlapping player
        Vector2D pPos = player.getPosition();
        BossEnemy boss = new BossEnemy(pPos.cpy());
        assertEquals(45.0f, boss.getContactDamage(), 0.001f);
        session.addEnemy(boss);

        // Update session by 1 frame (0.016s)
        session.update(0.016f, new Vector2D(pPos.x + 100, pPos.y), new Vector2D(0, 0), false);

        // Expected damage: 45 * (100 / 130) = 4500 / 130 = 34.6153846...
        float expectedDamage = 45.0f * (100.0f / (100.0f + 30.0f));
        float actualDamage = 100.0f - player.getHealth();

        assertEquals(34.6154f, actualDamage, 1e-4f,
                "Boss contact damage 45 against armor 30 must be mitigated once to 34.6154, not twice (26.6272)");
        assertEquals(expectedDamage, actualDamage, 1e-4f);
    }

    @Test
    @DisplayName("D4.3: Direct mitigation calculation matches Character.takeDamage single mitigation")
    public void testDirectMitigationEquivalence() {
        Player player = new Player(new Vector2D(500, 500));
        player.setHealth(100.0f);
        player.setArmor(30.0f);

        float rawDamage = 15.0f;
        float singleMitigation = DamageMitigation.calculateDamage(rawDamage, player.getArmor());

        float dealt = player.takeDamage(rawDamage);
        assertEquals(singleMitigation, dealt, 1e-4f);
        assertEquals(11.5385f, dealt, 1e-4f);
    }
}
