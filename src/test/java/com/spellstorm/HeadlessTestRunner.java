package com.spellstorm;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.SharedLibraryLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Headless LibGDX test runner and JUnit 5 extension.
 * Provides a mock OpenGL context and loads native libraries to allow
 * running tests on game math, simulation, assets (Pixmaps/Textures),
 * and systems without an X11 or display environment.
 */
public class HeadlessTestRunner implements BeforeAllCallback {

    private static volatile boolean initialized = false;
    private static final Object LOCK = new Object();
    private static File assetRootDirectory = null;

    @BeforeAll
    public static void setupHeadless() {
        init();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        init();
    }

    /**
     * Initializes the headless LibGDX context if not already initialized.
     * Safe to call multiple times from multiple test classes or threads.
     */
    public static void init() {
        if (initialized) {
            return;
        }

        synchronized (LOCK) {
            if (initialized) {
                return;
            }

            // 1. Load native libraries for LibGDX (stb_image, etc.)
            try {
                new SharedLibraryLoader().load("gdx");
            } catch (Throwable t) {
                System.err.println("[HeadlessTestRunner] Warning: SharedLibraryLoader could not load 'gdx' natives: " 
                        + t.getMessage());
            }

            // 2. Initialize HeadlessApplication with no continuous render loop
            HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
            config.updatesPerSecond = -1;

            new HeadlessApplication(new ApplicationAdapter() {}, config);

            // 3. Install Mock GL20 context so Texture and graphics objects can be instantiated safely
            GL20 mockGL = createMockGL();
            Gdx.gl = mockGL;
            Gdx.gl20 = mockGL;

            // 4. Locate and cache asset root directory (enforces self-containment within SpellStorm-Java)
            assetRootDirectory = locateAssetDirectory();

            initialized = true;
            System.out.println("[HeadlessTestRunner] Headless LibGDX environment initialized successfully. Asset root: " 
                    + assetRootDirectory.getAbsolutePath());
        }
    }

    /**
     * Creates a dynamic proxy Mock GL20 that returns safe dummy values for all GL calls.
     */
    private static GL20 createMockGL() {
        final AtomicInteger idCounter = new AtomicInteger(100);

        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                Class<?> returnType = method.getReturnType();

                // Fill IntBuffer when generating textures, buffers, or framebuffers
                if (args != null) {
                    for (Object arg : args) {
                        if (arg instanceof IntBuffer) {
                            IntBuffer ib = (IntBuffer) arg;
                            if (ib.hasRemaining()) {
                                int pos = ib.position();
                                for (int i = pos; i < ib.limit(); i++) {
                                    ib.put(i, idCounter.incrementAndGet());
                                }
                            }
                        }
                    }
                }

                // Handle GL methods returning integer handles
                if (name.equals("glGenTexture") || name.equals("glCreateShader") || name.equals("glCreateProgram")) {
                    return idCounter.incrementAndGet();
                }
                if (name.equals("glCheckFramebufferStatus")) {
                    return GL20.GL_FRAMEBUFFER_COMPLETE;
                }
                if (name.equals("glGetError")) {
                    return GL20.GL_NO_ERROR;
                }
                if (name.equals("glGetString")) {
                    return "Headless Mock GL 2.0 (SpellStorm)";
                }

                // Set status flags for shader and program compilation checks
                if ((name.equals("glGetShaderiv") || name.equals("glGetProgramiv")) && args != null && args.length >= 3) {
                    if (args[2] instanceof IntBuffer) {
                        ((IntBuffer) args[2]).put(0, 1); // GL_TRUE
                    }
                    return null;
                }

                // Primitive defaults
                if (returnType == boolean.class) return true;
                if (returnType == int.class) return 0;
                if (returnType == float.class) return 0.0f;
                if (returnType == byte.class) return (byte) 0;
                if (returnType == short.class) return (short) 0;
                if (returnType == long.class) return 0L;
                if (returnType == double.class) return 0.0;
                if (returnType == String.class) return "";

                return null;
            }
        };

        return (GL20) Proxy.newProxyInstance(
                GL20.class.getClassLoader(),
                new Class<?>[]{GL20.class},
                handler
        );
    }

    /**
     * Resolves an asset file by checking relative candidate paths strictly within the target project root.
     *
     * @param relativePath Relative asset path (e.g. "textures/characters/wizard.png")
     * @return FileHandle to the resolved asset, or non-existent FileHandle if not found
     * @throws IllegalArgumentException if an external project path escaping self-containment is requested
     */
    public static FileHandle resolveAsset(String relativePath) {
        init();

        // Security / Isolation Guard: Reject any attempt to resolve external reference paths
        if (relativePath != null && (relativePath.contains("SpellStorm/assets") || relativePath.contains("/SpellStorm/"))) {
            throw new IllegalArgumentException(
                    "Project self-containment violation: Attempted to resolve external reference asset: " + relativePath
            );
        }

        if (assetRootDirectory != null) {
            File target = new File(assetRootDirectory, relativePath);
            if (target.exists()) {
                return new FileHandle(target);
            }

            // Handle cases where relativePath includes the "assets/" directory prefix
            if (relativePath != null && (relativePath.startsWith("assets/") || relativePath.startsWith("assets\\"))) {
                String normalized = relativePath.substring(7);
                File targetNorm = new File(assetRootDirectory, normalized);
                if (targetNorm.exists()) {
                    return new FileHandle(targetNorm);
                }
            }
        }

        // Fallback candidate checks strictly within the target project root
        String[] prefixes = {"assets/", "../assets/", "SpellStorm-Java/assets/"};
        for (String prefix : prefixes) {
            File f = new File(prefix + relativePath);
            if (f.exists()) {
                return new FileHandle(f);
            }
        }

        // Default to Gdx internal file handle (or FileHandle on asset root) for clear error reporting on missing files
        return Gdx.files != null ? Gdx.files.internal(relativePath) 
                : (assetRootDirectory != null ? new FileHandle(new File(assetRootDirectory, relativePath)) : new FileHandle(relativePath));
    }

    /**
     * Returns the absolute path of the assets directory.
     * Guaranteed to be non-null and within the target project root.
     *
     * @throws IllegalStateException if assets directory cannot be resolved
     */
    public static File getAssetRoot() {
        init();
        if (assetRootDirectory == null || !assetRootDirectory.isDirectory()) {
            throw new IllegalStateException("Asset root directory is not initialized or does not exist.");
        }
        return assetRootDirectory;
    }

    /**
     * Locates the asset directory strictly within the target project root hierarchy.
     * Searches ONLY:
     * 1. System property 'spellstorm.assets.dir'
     * 2. "assets"
     * 3. "../assets"
     * 4. "SpellStorm-Java/assets"
     *
     * Never searches or falls back to external project paths (such as a separate legacy C++ repository).
     *
     * @return Absolute File of the verified asset directory
     * @throws IllegalStateException if assets cannot be located within the target project
     */
    public static File locateAssetDirectory() {
        String sysProp = System.getProperty("spellstorm.assets.dir");
        String[] candidateDirs = {
                "assets",
                "../assets",
                "SpellStorm-Java/assets"
        };

        try {
            return locateAssetDirectoryFromCandidates(sysProp, candidateDirs);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(
                    "Asset directory could not be resolved within project root. " +
                    "Searched candidate paths: " + Arrays.toString(candidateDirs) + 
                    (sysProp != null ? " and system property 'spellstorm.assets.dir'='" + sysProp + "'" : "") + ". " +
                    "Project self-containment violation (Acceptance Criteria R1): assets must be located within the Java project.",
                    e
            );
        }
    }

    /**
     * Resolves an asset root directory from an explicit list of candidate paths.
     * Package-private for testability.
     */
    static File locateAssetDirectoryFromCandidates(String sysProp, String[] candidateDirs) throws FileNotFoundException {
        // 1. System property override if configured
        if (sysProp != null && !sysProp.isBlank()) {
            File f = new File(sysProp);
            if (isValidAssetDirectory(f)) {
                return f.getAbsoluteFile();
            }
        }

        // 2. Candidate directories within target project
        if (candidateDirs != null) {
            for (String c : candidateDirs) {
                File dir = new File(c);
                if (isValidAssetDirectory(dir)) {
                    return dir.getAbsoluteFile();
                }
            }
        }

        throw new FileNotFoundException(
                "Assets directory not found in candidate paths: " + Arrays.toString(candidateDirs)
        );
    }

    /**
     * Validates that a directory is a genuine asset root and not an external reference repo.
     */
    private static boolean isValidAssetDirectory(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }

        // Integrity check: Reject any directory path referencing the reference C++ project
        String absPath = dir.getAbsolutePath().replace('\\', '/');
        if (absPath.contains("/SpellStorm/assets") && !absPath.contains("/SpellStorm-Java/assets")) {
            System.err.println("[HeadlessTestRunner] REJECTED external reference asset directory: " + absPath);
            return false;
        }

        File textures = new File(dir, "textures");
        File fonts = new File(dir, "fonts");
        return textures.isDirectory() && fonts.isDirectory();
    }
}
