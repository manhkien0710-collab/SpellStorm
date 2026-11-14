package com.spellstorm.desktop;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.spellstorm.assets.AssetRegistry;
import com.spellstorm.assets.AssetRegistry.FontId;
import com.spellstorm.assets.AssetRegistry.TextureId;
import com.spellstorm.core.EngineConfig;
import com.spellstorm.core.SpellStormGame;

import java.io.File;

/**
 * DesktopLauncher
 * 
 * Entry point for the Spell Storm desktop application on the LWJGL3 backend.
 * Configures the primary window (1280x720, 60fps limit, VSync, window title)
 * and passes command-line configuration arguments into the game engine coordinator.
 */
public class DesktopLauncher {

    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;
    public static final int TARGET_FPS = 60;
    public static final String WINDOW_TITLE = "Spell Storm";

    public static void main(String[] args) {
        EngineConfig config = EngineConfig.fromArgs(args);

        // If headless mode is requested via CLI, run headless verification and exit
        if (config.isHeadless()) {
            runHeadlessVerification(config);
            return;
        }

        Lwjgl3ApplicationConfiguration lwjglConfig = new Lwjgl3ApplicationConfiguration();
        lwjglConfig.setTitle(config.getWindowTitle());
        lwjglConfig.setWindowedMode(config.getWindowWidth(), config.getWindowHeight());
        lwjglConfig.setForegroundFPS(config.getTargetFPS());
        lwjglConfig.setIdleFPS(30);
        lwjglConfig.useVsync(config.isVsyncEnabled());
        lwjglConfig.setResizable(true);

        // Configure window icon if asset is available
        setWindowIconIfPresent(lwjglConfig);

        new Lwjgl3Application(new SpellStormGame(config), lwjglConfig);
    }

    private static void setWindowIconIfPresent(Lwjgl3ApplicationConfiguration config) {
        String[] candidatePaths = {
            "assets/textures/characters/mage_down.png",
            "assets/images/characters/mage_down.png",
            "textures/characters/mage_down.png",
            "images/characters/mage_down.png"
        };

        for (String path : candidatePaths) {
            File file = new File(path);
            if (file.exists() && !file.isDirectory()) {
                config.setWindowIcon(Files.FileType.Internal, path);
                return;
            }
        }

        // Check classpath resources (e.g. when packaged in a JAR)
        for (String path : candidatePaths) {
            String cpResource = path.startsWith("/") ? path : ("/" + path);
            if (DesktopLauncher.class.getResource(cpResource) != null) {
                config.setWindowIcon(Files.FileType.Classpath, path);
                return;
            }
        }
    }

    private static void runHeadlessVerification(EngineConfig config) {
        System.out.println("[SpellStorm] Running headless verification...");
        int verifiedCount = 0;

        // 1. Verify all 36 textures
        for (TextureId textureId : TextureId.values()) {
            String path = "textures/" + textureId.getSubPath();
            if (!isAssetPresent("textures", textureId.getSubPath())) {
                System.err.println("[SpellStorm ERROR] Missing asset: " + path);
                System.exit(1);
            }
            verifiedCount++;
        }

        // 2. Verify all 3 fonts
        for (FontId fontId : FontId.values()) {
            String fontPath = fontId.getPath();
            String fileName = fontPath.substring(fontPath.lastIndexOf('/') + 1);
            if (!isAssetPresent("fonts", fileName)) {
                System.err.println("[SpellStorm ERROR] Missing asset: " + fontPath);
                System.exit(1);
            }
            verifiedCount++;
        }

        // 3. Verify license and readme (bringing total verified assets to 41)
        if (!isAssetPresent("fonts", "DejaVu-LICENSE.txt")) {
            System.err.println("[SpellStorm ERROR] Missing asset: fonts/DejaVu-LICENSE.txt");
            System.exit(1);
        }
        verifiedCount++;

        if (!isAssetPresent("", "README.md") && !isAssetPresent("assets", "README.md")) {
            System.err.println("[SpellStorm ERROR] Missing asset: README.md");
            System.exit(1);
        }
        verifiedCount++;

        System.out.println("[SpellStorm] Headless verification completed successfully: all " + verifiedCount + " assets verified.");
        System.exit(0);
    }

    private static boolean isAssetPresent(String subDir, String relativePath) {
        // 1. Check via AssetRegistry.resolveFile on filesystem
        try {
            FileHandle handle = AssetRegistry.resolveFile(subDir, relativePath);
            if (handle != null && handle.file() != null && handle.file().exists()) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        // 2. Check classpath resources (e.g. when packaged in a JAR)
        String prefix = (subDir != null && !subDir.isEmpty()) ? (subDir + "/") : "";
        String[] classpathCandidates = {
            "/assets/" + prefix + relativePath,
            "/" + prefix + relativePath,
            "/assets/" + relativePath,
            "/" + relativePath
        };

        for (String candidate : classpathCandidates) {
            try {
                if (DesktopLauncher.class.getResource(candidate) != null) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }
}
