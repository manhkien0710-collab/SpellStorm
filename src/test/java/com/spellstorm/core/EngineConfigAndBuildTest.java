package com.spellstorm.core;

import com.spellstorm.desktop.DesktopLauncher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Feature 1: Standalone Gradle Build & CLI Engine Configuration")
public class EngineConfigAndBuildTest {

    // Tier 1: Equivalence Tests (5 tests)
    @Test
    @DisplayName("1.1 EngineConfig default constructor initializes standard window parameters")
    void testEngineConfigDefaultParameters() {
        EngineConfig config = new EngineConfig();
        assertEquals(1280, config.getWindowWidth());
        assertEquals(720, config.getWindowHeight());
        assertEquals(60, config.getTargetFPS());
        assertTrue(config.isVsyncEnabled());
        assertEquals("Spell Storm", config.getWindowTitle());
        assertFalse(config.isHeadless());
        assertFalse(config.isAutoplay());
    }

    @Test
    @DisplayName("1.2 EngineConfig parses nominal automation CLI flags")
    void testEngineConfigCliFlagsParsing() {
        String[] args = {
            "--headless", "--autoplay", "--test-level-up", "--test-game-over",
            "--test-auto-shot", "--test-bridge", "--test-ruins", "--test-campsite",
            "--test-boss", "--test-powerup", "--test-restart-flow"
        };
        EngineConfig config = EngineConfig.fromArgs(args);
        assertTrue(config.isHeadless());
        assertTrue(config.isAutoplay());
        assertTrue(config.isTestLevelUp());
        assertTrue(config.isTestGameOver());
        assertTrue(config.isTestAutoShot());
        assertTrue(config.isTestBridge());
        assertTrue(config.isTestRuins());
        assertTrue(config.isTestCampsite());
        assertTrue(config.isTestBoss());
        assertTrue(config.isTestPowerup());
        assertTrue(config.isTestRestartFlow());
    }

    @Test
    @DisplayName("1.3 DesktopLauncher public window constants match specification")
    void testDesktopLauncherConstants() {
        assertEquals(1280, DesktopLauncher.WINDOW_WIDTH);
        assertEquals(720, DesktopLauncher.WINDOW_HEIGHT);
        assertEquals(60, DesktopLauncher.TARGET_FPS);
        assertEquals("Spell Storm", DesktopLauncher.WINDOW_TITLE);
    }

    @Test
    @DisplayName("1.4 EngineConfig parses quit-after seconds and screenshot path parameters")
    void testEngineConfigParametricFlags() {
        String[] args = {"--quit-after", "15.5", "--screenshot", "build/test_screen.png"};
        EngineConfig config = EngineConfig.fromArgs(args);
        assertEquals(15.5f, config.getQuitAfterSeconds(), 1e-4f);
        assertEquals("build/test_screen.png", config.getScreenshotPath());
    }

    @Test
    @DisplayName("1.5 EngineConfig Builder custom dimensions and title configuration")
    void testEngineConfigBuilderCustomization() {
        EngineConfig config = new EngineConfig.Builder()
                .windowSize(1920, 1080)
                .targetFPS(120)
                .vsync(false)
                .windowTitle("Spell Storm - Custom Window")
                .headless(true)
                .build();
        assertEquals(1920, config.getWindowWidth());
        assertEquals(1080, config.getWindowHeight());
        assertEquals(120, config.getTargetFPS());
        assertFalse(config.isVsyncEnabled());
        assertEquals("Spell Storm - Custom Window", config.getWindowTitle());
        assertTrue(config.isHeadless());
    }

    // Tier 2: Boundary & Corner Tests (5 tests)
    @Test
    @DisplayName("2.1 EngineConfig handles null arguments array safely without throwing")
    void testEngineConfigNullArgs() {
        assertDoesNotThrow(() -> {
            EngineConfig config = EngineConfig.fromArgs(null);
            assertNotNull(config);
            assertEquals(1280, config.getWindowWidth());
            assertFalse(config.isHeadless());
        });
    }

    @Test
    @DisplayName("2.2 EngineConfig handles empty arguments array cleanly")
    void testEngineConfigEmptyArgs() {
        EngineConfig config = EngineConfig.fromArgs(new String[0]);
        assertNotNull(config);
        assertEquals(60, config.getTargetFPS());
        assertFalse(config.isHeadless());
    }

    @Test
    @DisplayName("2.3 EngineConfig gracefully handles malformed float in --quit-after")
    void testEngineConfigMalformedQuitAfter() {
        String[] args = {"--quit-after", "not_a_number"};
        EngineConfig config = EngineConfig.fromArgs(args);
        assertEquals(-1.0f, config.getQuitAfterSeconds(), 1e-4f);
    }

    @Test
    @DisplayName("2.4 EngineConfig gracefully handles trailing flag without value")
    void testEngineConfigTrailingFlagWithoutValue() {
        String[] args = {"--headless", "--quit-after"};
        EngineConfig config = EngineConfig.fromArgs(args);
        assertTrue(config.isHeadless());
        assertEquals(-1.0f, config.getQuitAfterSeconds(), 1e-4f);
    }

    @Test
    @DisplayName("2.5 EngineConfig ignores unknown arguments without exception")
    void testEngineConfigUnknownArgs() {
        String[] args = {"--unknown-flag-1", "--bogus=true", "-x", "--headless"};
        EngineConfig config = EngineConfig.fromArgs(args);
        assertTrue(config.isHeadless());
        assertFalse(config.isAutoplay());
    }
}
