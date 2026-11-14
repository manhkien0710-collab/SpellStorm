package com.spellstorm.p0;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputProcessor;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.core.GameScreen;
import com.spellstorm.core.GameSession;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.ShootingMode;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P0 Regression: Input Integration & Screen Coordination (Defects D2 & D3)")
public class InputIntegrationTest {

    private Input originalInput;
    private MockInputController mockController;

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

    @BeforeAll
    static void initHeadless() {
        HeadlessTestRunner.init();
    }

    @BeforeEach
    void setUp() {
        originalInput = Gdx.input;
        mockController = new MockInputController();
        Gdx.input = mockController.createProxy();
    }

    @AfterEach
    void tearDown() {
        Gdx.input = originalInput;
    }

    @Test
    @DisplayName("D2: Player movement input moves 3.8333 px/frame at 230 px/s, NOT 7.6666 px (single processing)")
    void testSingleMovementInputProcessingPerFrame() {
        GameSession session = new GameSession();
        GameScreen screen = new GameScreen(null, session, null, null);

        Player player = session.getPlayer();
        float startX = player.getPosition().x;
        float startY = player.getPosition().y;

        // Press 'D' (move right)
        mockController.pressKey(Keys.D);

        float dt = 1.0f / 60.0f; // Standard frame tick
        float expectedDx = player.getBaseMoveSpeed() * dt; // 230.0f * (1/60f) = 3.8333333f

        // Render one frame
        screen.render(dt);

        float actualDx = player.getPosition().x - startX;
        float actualDy = player.getPosition().y - startY;

        assertEquals(0.0f, actualDy, 1e-4f, "No vertical movement expected when pressing only D");
        // Verify displacement matches 1x frame speed (~3.8333 px), NOT 2x duplicate (~7.6666 px)
        assertEquals(expectedDx, actualDx, 0.01f,
                String.format("Expected 1x displacement %.4f px/frame, but got %.4f px/frame (duplicate input processing)",
                        expectedDx, actualDx));
    }

    @Test
    @DisplayName("D2: Diagonal movement (W+D) displacement magnitude is strictly 3.8333 px/frame, not 7.6666 px")
    void testDiagonalMovementSingleProcessing() {
        GameSession session = new GameSession();
        GameScreen screen = new GameScreen(null, session, null, null);

        Player player = session.getPlayer();
        Vector2D startPos = player.getPosition().cpy();

        // Press 'W' and 'D' (up-right)
        mockController.pressKey(Keys.W);
        mockController.pressKey(Keys.D);

        float dt = 1.0f / 60.0f;
        screen.render(dt);

        float displacement = player.getPosition().cpy().sub(startPos).len();
        float expectedDisplacement = player.getBaseMoveSpeed() * dt; // 3.8333333f

        assertEquals(expectedDisplacement, displacement, 0.01f,
                String.format("Expected diagonal displacement %.4f px, but got %.4f px",
                        expectedDisplacement, displacement));
    }

    @Test
    @DisplayName("D2: Opposing movement keys cancel to strictly zero displacement in GameScreen.render")
    void testOpposingMovementKeysCancel() {
        GameSession session = new GameSession();
        GameScreen screen = new GameScreen(null, session, null, null);

        Player player = session.getPlayer();
        Vector2D startPos = player.getPosition().cpy();

        // Press both left and right (A + D)
        mockController.pressKey(Keys.A);
        mockController.pressKey(Keys.D);

        float dt = 1.0f / 60.0f;
        screen.render(dt);

        assertEquals(startPos.x, player.getPosition().x, 1e-4f);
        assertEquals(startPos.y, player.getPosition().y, 1e-4f);
    }

    @Test
    @DisplayName("D2: Camera smooth tracking is updated exactly once per frame without duplication")
    void testCameraSmoothTrackingSingleUpdate() {
        GameSession session = new GameSession();
        GameScreen screen = new GameScreen(null, session, null, null);
        GameCamera camera = session.getCamera();
        Player player = session.getPlayer();

        // Move player away from center
        player.setPosition(800.0f, 600.0f);

        float dt = 1.0f / 60.0f;
        float prevCamX = camera.getCenterX();
        float prevCamY = camera.getCenterY();

        screen.render(dt);

        // Smooth camera movement: t = 1 - exp(-smoothSpeed * dt) with smoothSpeed = 8.0f
        float t = 1.0f - (float) Math.exp(-GameCamera.DEFAULT_SMOOTH_SPEED * dt);
        float expectedCamX = prevCamX + (player.getPosition().x - prevCamX) * t;
        float expectedCamY = prevCamY + (player.getPosition().y - prevCamY) * t;

        assertEquals(expectedCamX, camera.getCenterX(), 0.05f, "Camera centerX must reflect single update step");
        assertEquals(expectedCamY, camera.getCenterY(), 0.05f, "Camera centerY must reflect single update step");
    }

    @Test
    @DisplayName("D2: Discrete left-click spellcasting in GameScreen remains active and functional")
    void testContinuousLeftClickCasting() {
        GameSession session = new GameSession();
        GameScreen screen = new GameScreen(null, session, null, null);

        assertEquals(0, session.getCombatSystem().getProjectiles().size());
        assertTrue(session.getPlayer().canCast());

        // Press left mouse button
        mockController.leftMouseButton = true;
        mockController.mouseX = 800;
        mockController.mouseY = 360;

        float dt = 1.0f / 60.0f;
        screen.render(dt);

        // Projectile should be spawned
        assertTrue(session.getCombatSystem().getProjectiles().size() > 0,
                "Pressing left mouse button during render() must cast spell projectile");
    }

    @Test
    @DisplayName("D3: Pressing V keeps session and player shooting mode strictly synchronized across frames")
    void testShootingModeSyncOnVKeyPress() {
        GameSession session = new GameSession();
        GameScreen screen = new GameScreen(null, session, null, null);
        Player player = session.getPlayer();

        assertEquals(ShootingMode.MANUAL, session.shootingMode());
        assertEquals(ShootingMode.MANUAL, player.getShootingMode());

        // Simulate pressing V key down via InputProcessor
        screen.keyDown(Keys.V);
        mockController.pressKey(Keys.V);

        // Immediately after keyDown, both should be AUTO
        assertEquals(ShootingMode.AUTO, session.shootingMode(), "Session must switch to AUTO on V keyDown");
        assertEquals(ShootingMode.AUTO, player.getShootingMode(), "Player must switch to AUTO on V keyDown");

        // Simulate frame execution while V key is held
        float dt = 1.0f / 60.0f;
        screen.render(dt);

        // Verify no desynchronization happened during render() / handleInput()
        assertEquals(ShootingMode.AUTO, session.shootingMode(), "Session must remain AUTO");
        assertEquals(ShootingMode.AUTO, player.getShootingMode(),
                "Player shooting mode must NOT revert to MANUAL during handleInput");
        assertEquals(session.shootingMode(), player.getShootingMode(),
                "Session and Player shooting modes must remain strictly equal");

        // Simulate subsequent frames with V still pressed
        for (int frame = 0; frame < 5; frame++) {
            screen.render(dt);
            assertEquals(session.shootingMode(), player.getShootingMode(),
                    "Session and Player shooting modes must stay equal on subsequent frames");
        }

        // Release V
        screen.keyUp(Keys.V);
        mockController.releaseKey(Keys.V);

        screen.render(dt);
        assertEquals(ShootingMode.AUTO, session.shootingMode());
        assertEquals(ShootingMode.AUTO, player.getShootingMode());

        // Press V again to toggle back to MANUAL
        screen.keyDown(Keys.V);
        mockController.pressKey(Keys.V);

        screen.render(dt);
        assertEquals(ShootingMode.MANUAL, session.shootingMode(), "Session must switch back to MANUAL");
        assertEquals(ShootingMode.MANUAL, player.getShootingMode(), "Player must switch back to MANUAL");
        assertEquals(session.shootingMode(), player.getShootingMode(),
                "Session and Player modes must match after toggling back to MANUAL");
    }

    @Test
    @DisplayName("D3 Regression: Player.tryToggleShootingMode() remains functional as a public unit test method")
    void testPlayerTryToggleShootingModeDirectly() {
        Player p = new Player(new Vector2D(0, 0));
        assertEquals(ShootingMode.MANUAL, p.getShootingMode());

        // First toggle succeeds
        assertTrue(p.tryToggleShootingMode());
        assertEquals(ShootingMode.AUTO, p.getShootingMode());

        // Immediate subsequent toggle fails due to debounce / key held
        assertFalse(p.tryToggleShootingMode());
        assertEquals(ShootingMode.AUTO, p.getShootingMode());

        // Release key and advance cooldown
        p.onToggleKeyReleased();
        p.update(0.3f);

        // Next toggle succeeds
        assertTrue(p.tryToggleShootingMode());
        assertEquals(ShootingMode.MANUAL, p.getShootingMode());
    }
}
