package com.spellstorm.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Immutable configuration parameters and command-line flags
 * providing feature parity with C++ GameOptions and Phase 2 CLI options.
 */
public class EngineConfig {

    public static final int DEFAULT_WIDTH = 1280;
    public static final int DEFAULT_HEIGHT = 720;
    public static final int DEFAULT_FPS = 60;
    public static final String DEFAULT_TITLE = "Spell Storm";

    public static final String SCENARIO_TITLE = "title";
    public static final String SCENARIO_PAUSE = "pause";
    public static final String SCENARIO_SETTINGS = "settings";
    public static final String SCENARIO_CODEX = "codex";
    public static final String SCENARIO_LEVEL_UP = "level-up";
    public static final String SCENARIO_GAME_OVER = "game-over";
    public static final String SCENARIO_AUTO_SHOT = "auto-shot";
    public static final String SCENARIO_SHOWCASE = "showcase";
    public static final String SCENARIO_BRIDGE = "bridge";
    public static final String SCENARIO_RUINS = "ruins";
    public static final String SCENARIO_CAMPSITE = "campsite";
    public static final String SCENARIO_BOSS = "boss";
    public static final String SCENARIO_POWERUP = "powerup";
    public static final String SCENARIO_RESTART_FLOW = "restart-flow";
    public static final String SCENARIO_M2_PROTOTYPE = "m2-prototype";
    public static final String SCENARIO_M5_BOSS = "m5-boss";
    public static final String SCENARIO_M5_ELEMENTS = "m5-elements";

    public static final String MODE_INFINITE = "infinite";
    public static final String MODE_TILEMAP = "tilemap";

    private final String mode;
    private final int windowWidth;
    private final int windowHeight;
    private final int targetFPS;
    private final boolean vsyncEnabled;
    private final String windowTitle;

    // Runtime & Automation Flags
    private final boolean headless;
    private final boolean autoplay;
    private final boolean debugMode;
    private final Long seed;
    private final Set<String> scenarios;
    private final float quitAfterSeconds;
    private final String screenshotPath;
    private final float screenshotAtSeconds;

    public EngineConfig() {
        this(new Builder());
    }

    private EngineConfig(Builder builder) {
        this.mode = builder.mode;
        this.windowWidth = builder.windowWidth;
        this.windowHeight = builder.windowHeight;
        this.targetFPS = builder.targetFPS;
        this.vsyncEnabled = builder.vsyncEnabled;
        this.windowTitle = builder.windowTitle;
        this.headless = builder.headless;
        this.autoplay = builder.autoplay;
        this.debugMode = builder.debugMode;
        this.seed = builder.seed;
        this.scenarios = Collections.unmodifiableSet(new LinkedHashSet<>(builder.scenarios));
        this.quitAfterSeconds = builder.quitAfterSeconds;
        this.screenshotPath = builder.screenshotPath;
        this.screenshotAtSeconds = builder.screenshotAtSeconds;
    }

    public static EngineConfig fromArgs(String[] args) {
        Builder builder = new Builder();
        if (args == null) return builder.build();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--headless" -> builder.headless(true);
                case "--autoplay" -> builder.autoplay(true);
                case "--debug" -> builder.debugMode(true);
                case "--mode" -> {
                    if (i + 1 < args.length) {
                        builder.mode(args[++i]);
                    }
                }
                case "--test-level-up" -> builder.testLevelUp(true);
                case "--test-game-over" -> builder.testGameOver(true);
                case "--test-auto-shot" -> builder.testAutoShot(true);
                case "--test-showcase" -> builder.testShowcase(true);
                case "--test-bridge" -> builder.testBridge(true);
                case "--test-ruins" -> builder.testRuins(true);
                case "--test-campsite" -> builder.testCampsite(true);
                case "--test-boss" -> builder.testBoss(true);
                case "--test-powerup" -> builder.testPowerup(true);
                case "--test-restart-flow" -> builder.testRestartFlow(true);
                case "--scenario" -> {
                    if (i + 1 < args.length) {
                        builder.scenario(args[++i]);
                    }
                }
                case "--seed" -> {
                    if (i + 1 < args.length) {
                        try {
                            builder.seed(Long.parseLong(args[++i]));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--width" -> {
                    if (i + 1 < args.length) {
                        try {
                            int w = Integer.parseInt(args[++i]);
                            if (w > 0) builder.windowWidth(w);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--height" -> {
                    if (i + 1 < args.length) {
                        try {
                            int h = Integer.parseInt(args[++i]);
                            if (h > 0) builder.windowHeight(h);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--quit-after" -> {
                    if (i + 1 < args.length) {
                        try {
                            builder.quitAfterSeconds(Float.parseFloat(args[++i]));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "--screenshot" -> {
                    if (i + 1 < args.length) {
                        builder.screenshotPath(args[++i]);
                    }
                }
                case "--screenshot-at" -> {
                    if (i + 1 < args.length) {
                        try {
                            builder.screenshotAtSeconds(Float.parseFloat(args[++i]));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        return builder.build();
    }

    private static String normalizeScenario(String name) {
        if (name == null) return null;
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    // Getters
    public String getMode() { return mode; }
    public boolean isTileMapMode() { return MODE_TILEMAP.equalsIgnoreCase(mode); }
    public boolean isInfiniteMode() { return !MODE_TILEMAP.equalsIgnoreCase(mode); }
    public int getWindowWidth() { return windowWidth; }
    public int getWindowHeight() { return windowHeight; }
    public int getTargetFPS() { return targetFPS; }
    public boolean isVsyncEnabled() { return vsyncEnabled; }
    public String getWindowTitle() { return windowTitle; }
    public boolean isHeadless() { return headless; }
    public boolean isAutoplay() { return autoplay; }
    public boolean isDebugMode() { return debugMode; }
    public Long getSeed() { return seed; }
    public boolean hasExplicitSeed() { return seed != null; }

    public String getScenario() {
        return scenarios.isEmpty() ? null : scenarios.iterator().next();
    }

    public Set<String> getScenarios() {
        return scenarios;
    }

    public boolean hasScenario(String name) {
        String norm = normalizeScenario(name);
        return norm != null && scenarios.contains(norm);
    }

    public boolean isScenarioTitle() { return hasScenario(SCENARIO_TITLE); }
    public boolean isScenarioPause() { return hasScenario(SCENARIO_PAUSE); }
    public boolean isScenarioSettings() { return hasScenario(SCENARIO_SETTINGS); }
    public boolean isScenarioCodex() { return hasScenario(SCENARIO_CODEX); }
    public boolean isTestLevelUp() { return hasScenario(SCENARIO_LEVEL_UP); }
    public boolean isTestGameOver() { return hasScenario(SCENARIO_GAME_OVER); }
    public boolean isTestAutoShot() { return hasScenario(SCENARIO_AUTO_SHOT); }
    public boolean isTestShowcase() { return hasScenario(SCENARIO_SHOWCASE); }
    public boolean isTestBridge() { return hasScenario(SCENARIO_BRIDGE); }
    public boolean isTestRuins() { return hasScenario(SCENARIO_RUINS); }
    public boolean isTestCampsite() { return hasScenario(SCENARIO_CAMPSITE); }
    public boolean isTestBoss() { return hasScenario(SCENARIO_BOSS); }
    public boolean isTestPowerup() { return hasScenario(SCENARIO_POWERUP); }
    public boolean isTestRestartFlow() { return hasScenario(SCENARIO_RESTART_FLOW); }
    public boolean isScenarioM2Prototype() { return hasScenario(SCENARIO_M2_PROTOTYPE); }
    public boolean isScenarioM5Boss() { return hasScenario(SCENARIO_M5_BOSS); }
    public boolean isScenarioM5Elements() { return hasScenario(SCENARIO_M5_ELEMENTS); }

    public float getQuitAfterSeconds() { return quitAfterSeconds; }
    public String getScreenshotPath() { return screenshotPath; }
    public float getScreenshotAtSeconds() { return screenshotAtSeconds; }

    public static class Builder {
        private String mode = MODE_INFINITE;
        private int windowWidth = DEFAULT_WIDTH;
        private int windowHeight = DEFAULT_HEIGHT;
        private int targetFPS = DEFAULT_FPS;
        private boolean vsyncEnabled = true;
        private String windowTitle = DEFAULT_TITLE;
        private boolean headless = false;
        private boolean autoplay = false;
        private boolean debugMode = false;
        private Long seed = null;
        private final Set<String> scenarios = new LinkedHashSet<>();
        private float quitAfterSeconds = -1f;
        private String screenshotPath = null;
        private float screenshotAtSeconds = -1f;

        public Builder windowSize(int width, int height) { this.windowWidth = width; this.windowHeight = height; return this; }
        public Builder windowWidth(int width) { this.windowWidth = width; return this; }
        public Builder windowHeight(int height) { this.windowHeight = height; return this; }
        public Builder targetFPS(int fps) { this.targetFPS = fps; return this; }
        public Builder vsync(boolean vsync) { this.vsyncEnabled = vsync; return this; }
        public Builder windowTitle(String title) { this.windowTitle = title; return this; }
        public Builder headless(boolean v) { this.headless = v; return this; }
        public Builder autoplay(boolean v) { this.autoplay = v; return this; }
        public Builder debugMode(boolean v) { this.debugMode = v; return this; }
        public Builder seed(Long seed) { this.seed = seed; return this; }
        public Builder mode(String mode) { this.mode = mode != null ? mode : MODE_INFINITE; return this; }
        public Builder tileMapMode() { this.mode = MODE_TILEMAP; return this; }
        public Builder infiniteMode() { this.mode = MODE_INFINITE; return this; }

        public Builder scenario(String name) {
            String norm = normalizeScenario(name);
            if (norm != null) {
                this.scenarios.add(norm);
            }
            return this;
        }

        private Builder setScenarioFlag(String key, boolean enabled) {
            if (enabled) {
                this.scenarios.add(key);
            } else {
                this.scenarios.remove(key);
            }
            return this;
        }

        public Builder testLevelUp(boolean v) { return setScenarioFlag(SCENARIO_LEVEL_UP, v); }
        public Builder testGameOver(boolean v) { return setScenarioFlag(SCENARIO_GAME_OVER, v); }
        public Builder testAutoShot(boolean v) { return setScenarioFlag(SCENARIO_AUTO_SHOT, v); }
        public Builder testShowcase(boolean v) { return setScenarioFlag(SCENARIO_SHOWCASE, v); }
        public Builder testBridge(boolean v) { return setScenarioFlag(SCENARIO_BRIDGE, v); }
        public Builder testRuins(boolean v) { return setScenarioFlag(SCENARIO_RUINS, v); }
        public Builder testCampsite(boolean v) { return setScenarioFlag(SCENARIO_CAMPSITE, v); }
        public Builder testBoss(boolean v) { return setScenarioFlag(SCENARIO_BOSS, v); }
        public Builder testPowerup(boolean v) { return setScenarioFlag(SCENARIO_POWERUP, v); }
        public Builder testRestartFlow(boolean v) { return setScenarioFlag(SCENARIO_RESTART_FLOW, v); }
        public Builder scenarioM2Prototype(boolean v) { return setScenarioFlag(SCENARIO_M2_PROTOTYPE, v); }
        public Builder scenarioM5Boss(boolean v) { return setScenarioFlag(SCENARIO_M5_BOSS, v); }
        public Builder scenarioM5Elements(boolean v) { return setScenarioFlag(SCENARIO_M5_ELEMENTS, v); }
        public Builder quitAfterSeconds(float v) { this.quitAfterSeconds = v; return this; }
        public Builder screenshotPath(String v) { this.screenshotPath = v; return this; }
        public Builder screenshotAtSeconds(float v) { this.screenshotAtSeconds = v; return this; }

        public EngineConfig build() { return new EngineConfig(this); }
    }
}
