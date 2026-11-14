package com.spellstorm.p2;

import com.spellstorm.assets.AssetRegistry.FontId;
import com.spellstorm.assets.AssetRegistry.TextureId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 verification tests for asset packaging hygiene, root README documentation,
 * and docs/PHASE2.md architectural documentation.
 */
public class ToolingP2Test {

    private static Path resolveProjectRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.exists(cwd.resolve("build.gradle"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.exists(parent.resolve("build.gradle"))) {
            return parent;
        }
        return cwd;
    }

    @Test
    @DisplayName("contact_sheet_p0.png is excluded from assets/textures/ and archived in docs/")
    void testContactSheetMovedOutOfRuntimeAssets() throws IOException {
        Path root = resolveProjectRoot();
        Path oldAssetSheet = root.resolve("assets/textures/contact_sheet_p0.png");
        Path docsSheet = root.resolve("docs/contact_sheet_p0.png");

        assertFalse(Files.exists(oldAssetSheet),
                "Dev contact sheet must not exist in assets/textures/ so it is not packaged in the JAR");
        assertTrue(Files.exists(docsSheet),
                "contact_sheet_p0.png must exist in docs/");
        assertTrue(Files.size(docsSheet) > 0,
                "docs/contact_sheet_p0.png must be non-empty");
    }

    @Test
    @DisplayName("All 41 runtime headless assets remain intact under assets/")
    void testAll41RuntimeAssetsRemainIntact() {
        Path root = resolveProjectRoot();
        Path assetsDir = root.resolve("assets");

        int verifiedCount = 0;
        for (TextureId textureId : TextureId.values()) {
            Path p = assetsDir.resolve("textures").resolve(textureId.getSubPath());
            assertTrue(Files.exists(p), "Missing TextureId asset: " + p);
            verifiedCount++;
        }
        for (FontId fontId : FontId.values()) {
            Path p = assetsDir.resolve(fontId.getPath());
            assertTrue(Files.exists(p), "Missing FontId asset: " + p);
            verifiedCount++;
        }
        assertTrue(Files.exists(assetsDir.resolve("fonts/DejaVu-LICENSE.txt")),
                "Missing DejaVu-LICENSE.txt");
        verifiedCount++;
        assertTrue(Files.exists(assetsDir.resolve("README.md")),
                "Missing assets/README.md");
        verifiedCount++;

        assertEquals(41, verifiedCount, "Headless asset inventory count must remain 41");
    }

    @Test
    @DisplayName("Root README.md exists and documents build/run, controls, recipes, power-ups, and CLI flags")
    void testReadmeContainsRequiredDocumentation() throws IOException {
        Path root = resolveProjectRoot();
        Path readme = root.resolve("README.md");
        assertTrue(Files.exists(readme), "Root README.md must exist");

        String content = Files.readString(readme, StandardCharsets.UTF_8);
        assertFalse(content.isBlank(), "README.md must not be blank");

        // Build / run / test commands
        assertTrue(content.contains("./gradlew test"), "README.md must document ./gradlew test");
        assertTrue(content.contains("./gradlew build"), "README.md must document ./gradlew build");
        assertTrue(content.contains("./gradlew run"), "README.md must document ./gradlew run");
        assertTrue(content.contains("java -jar"), "README.md must document java -jar");

        // Controls
        assertTrue(content.contains("WASD"), "README.md must document WASD movement");
        assertTrue(content.contains("LMB") || content.contains("Left Mouse"),
                "README.md must document mouse spell casting");
        assertTrue(content.contains("F11"), "README.md must document F11 fullscreen toggle");

        // SpellRegistry spells (5 base + 3 registered combinations + 15 pairings coverage)
        String[] requiredSpells = {
                "Firebolt", "Frost Shard", "Spark Arc", "Gale Burst", "Boulder Slam",
                "Firestorm", "Frost Tempest", "Magma Core"
        };
        for (String spell : requiredSpells) {
            assertTrue(content.contains(spell), "README.md must document SpellRegistry spell: " + spell);
        }

        // Power-up types
        assertTrue(content.contains("ARCANE_OVERDRIVE"), "README.md must document ARCANE_OVERDRIVE");
        assertTrue(content.contains("PIERCING_SURGE"), "README.md must document PIERCING_SURGE");
        assertTrue(content.contains("ELEMENTAL_BURST"), "README.md must document ELEMENTAL_BURST");

        // CLI flags
        String[] requiredFlags = {
                "--headless", "--autoplay", "--seed", "--scenario",
                "--screenshot", "--screenshot-at", "--debug", "--width", "--height"
        };
        for (String flag : requiredFlags) {
            assertTrue(content.contains(flag), "README.md must document CLI flag: " + flag);
        }
    }

    @Test
    @DisplayName("docs/PHASE2.md exists and documents Phase 2 architecture, state flow, viewport, balance, and overlays")
    void testPhase2DocExistsWithRequiredSections() throws IOException {
        Path root = resolveProjectRoot();
        Path phase2Doc = root.resolve("docs/PHASE2.md");
        assertTrue(Files.exists(phase2Doc), "docs/PHASE2.md must exist");

        String content = Files.readString(phase2Doc, StandardCharsets.UTF_8);
        assertFalse(content.isBlank(), "docs/PHASE2.md must not be blank");

        assertTrue(content.contains("FitViewport") && content.contains("ViewportManager"),
                "docs/PHASE2.md must document FitViewport / ViewportManager unprojection");
        assertTrue(content.contains("BalanceConfig"),
                "docs/PHASE2.md must document BalanceConfig comparison");
        assertTrue(content.contains("START") && content.contains("PLAYING")
                        && content.contains("PAUSED") && content.contains("LEVEL_UP")
                        && content.contains("GAME_OVER"),
                "docs/PHASE2.md must document App Shell GameState flow");
        assertTrue(content.contains("TitleScreen") && content.contains("PauseMenuOverlay")
                        && content.contains("SettingsOverlay") && content.contains("CodexOverlay")
                        && content.contains("TutorialOverlay"),
                "docs/PHASE2.md must document Phase 2 overlays");
    }
}
