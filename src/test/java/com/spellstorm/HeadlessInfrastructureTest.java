package com.spellstorm;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.IntBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Feature 3: Headless Test Infrastructure & Mock GL20 Validation")
public class HeadlessInfrastructureTest {

    // Tier 1: Equivalence Tests (5 tests)
    @Test
    @DisplayName("1.1 HeadlessTestRunner.init() idempotency across repeated invocations")
    void testInitIdempotency() {
        assertDoesNotThrow(() -> {
            HeadlessTestRunner.init();
            HeadlessTestRunner.init();
            HeadlessTestRunner.init();
        });
        assertNotNull(Gdx.app);
        assertNotNull(Gdx.gl);
    }

    @Test
    @DisplayName("1.2 Mock GL20 generates positive monotonic integer handles for textures, shaders, and programs")
    void testMockGLIntegerHandles() {
        HeadlessTestRunner.init();
        GL20 gl = Gdx.gl;
        assertNotNull(gl);

        int tex = gl.glGenTexture();
        int shader = gl.glCreateShader(GL20.GL_VERTEX_SHADER);
        int prog = gl.glCreateProgram();

        assertTrue(tex > 0, "Texture handle must be positive integer");
        assertTrue(shader > 0, "Shader handle must be positive integer");
        assertTrue(prog > 0, "Program handle must be positive integer");
    }

    @Test
    @DisplayName("1.3 Mock GL20 reports complete framebuffer status and no errors")
    void testMockGLStatusAndErrors() {
        HeadlessTestRunner.init();
        GL20 gl = Gdx.gl;
        assertEquals(GL20.GL_FRAMEBUFFER_COMPLETE, gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER));
        assertEquals(GL20.GL_NO_ERROR, gl.glGetError());
        String vendor = gl.glGetString(GL20.GL_RENDERER);
        assertNotNull(vendor);
        assertTrue(vendor.contains("Headless Mock GL"));
    }

    @Test
    @DisplayName("1.4 Mock GL20 populates IntBuffer upon glGenTextures invocation")
    void testMockGLIntBufferPopulation() {
        HeadlessTestRunner.init();
        GL20 gl = Gdx.gl;
        IntBuffer buffer = IntBuffer.allocate(4);
        gl.glGenTextures(4, buffer);
        for (int i = 0; i < 4; i++) {
            assertTrue(buffer.get(i) > 0, "IntBuffer elements must be populated with non-zero handles");
        }
    }

    @Test
    @DisplayName("1.5 HeadlessTestRunner.resolveAsset() resolves valid internal textures and fonts")
    void testInternalAssetResolution() {
        HeadlessTestRunner.init();
        FileHandle texHandle = HeadlessTestRunner.resolveAsset("textures/characters/wizard.png");
        assertTrue(texHandle.exists());

        FileHandle fontHandle = HeadlessTestRunner.resolveAsset("fonts/DejaVuSans.ttf");
        assertTrue(fontHandle.exists());
    }

    // Tier 2: Boundary & Corner Tests (5 tests)
    @Test
    @DisplayName("2.1 Security guard: resolveAsset() rejects external reference project paths with IllegalArgumentException")
    void testSecurityGuardRejectsExternalPaths() {
        HeadlessTestRunner.init();
        assertThrows(IllegalArgumentException.class, () -> {
            HeadlessTestRunner.resolveAsset("/external/SpellStorm/assets/textures/characters/wizard.png");
        });
        assertThrows(IllegalArgumentException.class, () -> {
            HeadlessTestRunner.resolveAsset("SpellStorm/assets/textures/characters/wizard.png");
        });
    }

    @Test
    @DisplayName("2.2 locateAssetDirectoryFromCandidates honors valid system property override")
    void testLocateAssetDirectorySystemPropertyOverride() throws FileNotFoundException {
        File expected = HeadlessTestRunner.getAssetRoot();
        String sysProp = expected.getAbsolutePath();
        File resolved = HeadlessTestRunner.locateAssetDirectoryFromCandidates(sysProp, new String[]{"bogus/path"});
        assertEquals(expected.getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    @DisplayName("2.3 locateAssetDirectoryFromCandidates throws FileNotFoundException when candidate paths are invalid")
    void testLocateAssetDirectoryThrowsWhenMissing() {
        assertThrows(FileNotFoundException.class, () -> {
            HeadlessTestRunner.locateAssetDirectoryFromCandidates(null, new String[]{"definitely/non_existent_dir_999"});
        });
    }

    @Test
    @DisplayName("2.4 Mock GL20 returns safe defaults for unmocked primitive queries without crashing")
    void testMockGLPrimitiveDefaults() {
        HeadlessTestRunner.init();
        GL20 gl = Gdx.gl;
        assertEquals(0, gl.glGetUniformLocation(1, "u_projTrans"));
        assertEquals(0, gl.glGetAttribLocation(1, "a_position"));
        assertTrue(gl.glIsEnabled(GL20.GL_BLEND));
    }

    @Test
    @DisplayName("2.5 Multi-threaded HeadlessTestRunner.init() concurrent invocation safety")
    void testConcurrentHeadlessInit() throws InterruptedException {
        int threads = 10;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    HeadlessTestRunner.init();
                    if (Gdx.gl != null && HeadlessTestRunner.getAssetRoot().isDirectory()) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        assertEquals(threads, successCount.get(), "All concurrent initialization threads must succeed");
    }
}
