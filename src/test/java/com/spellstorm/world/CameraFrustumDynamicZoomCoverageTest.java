package com.spellstorm.world;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.camera.GameCamera;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Camera Frustum Dynamic Zoom & Coverage Tests")
public class CameraFrustumDynamicZoomCoverageTest {

    private ChunkManager chunkManager;

    @BeforeAll
    public static void setupHeadless() {
        HeadlessTestRunner.init();
    }

    @BeforeEach
    public void setUp() {
        chunkManager = new ChunkManagerImpl((cx, cy) -> new ChunkImpl(cx, cy));
    }

    @ParameterizedTest(name = "Frustum coverage at zoom = {0}")
    @ValueSource(floats = {GameCamera.MIN_ZOOM, 1.0f, GameCamera.DEFAULT_ZOOM, 1.8f, GameCamera.MAX_ZOOM})
    public void testCompleteFrustumCoverageAcrossZoomRange(float zoomLevel) {
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-100_000f, -100_000f, 200_000f, 200_000f);
        camera.setZoom(zoomLevel);
        camera.setCenter(512.0f, 512.0f); // Center of chunk (0, 0)

        // Verify LibGDX OrthographicCamera formula matches GameCamera
        OrthographicCamera gdxCam = camera.getOrthographicCamera();
        float expectedVisW = 1280.0f * gdxCam.zoom;
        float expectedVisH = 720.0f * gdxCam.zoom;
        assertEquals(1280.0f / zoomLevel, expectedVisW, 1e-3f, "LibGDX visible width must match GameCamera");
        assertEquals(720.0f / zoomLevel, expectedVisH, 1e-3f, "LibGDX visible height must match GameCamera");

        chunkManager.updateStreaming(camera.getCenter(), camera);
        List<Chunk> visible = chunkManager.getVisibleChunks(camera);

        assertFalse(visible.isEmpty(), "Visible chunks must not be empty");

        // Verify frustum rectangle is completely bounded by visible chunks
        Rectangle frustum = camera.getVisibleBounds();
        for (Chunk c : visible) {
            assertNotNull(c);
        }

        int minCx = visible.stream().mapToInt(Chunk::getChunkX).min().orElseThrow();
        int maxCx = visible.stream().mapToInt(Chunk::getChunkX).max().orElseThrow();
        int minCy = visible.stream().mapToInt(Chunk::getChunkY).min().orElseThrow();
        int maxCy = visible.stream().mapToInt(Chunk::getChunkY).max().orElseThrow();

        float coveredMinX = minCx * 1024.0f;
        float coveredMaxX = (maxCx + 1) * 1024.0f;
        float coveredMinY = minCy * 1024.0f;
        float coveredMaxY = (maxCy + 1) * 1024.0f;

        assertTrue(coveredMinX <= frustum.x, "Covered minX must enclose frustum minX");
        assertTrue(coveredMaxX >= frustum.x + frustum.width, "Covered maxX must enclose frustum maxX");
        assertTrue(coveredMinY <= frustum.y, "Covered minY must enclose frustum minY");
        assertTrue(coveredMaxY >= frustum.y + frustum.height, "Covered maxY must enclose frustum maxY");
    }

    @ParameterizedTest(name = "Negative coordinate coverage at cam=({0}, {1})")
    @CsvSource({
        "-1024.0, -1024.0",
        "-1.0, -1.0",
        "-5000.0, -2500.0",
        "0.0, 0.0"
    })
    public void testNegativeChunkBoundaryFrustumCoverage(float camX, float camY) {
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-100_000f, -100_000f, 200_000f, 200_000f);
        camera.setZoom(GameCamera.DEFAULT_ZOOM);
        camera.setCenter(camX, camY);

        chunkManager.updateStreaming(camera.getCenter(), camera);
        List<Chunk> visible = chunkManager.getVisibleChunks(camera);
        assertFalse(visible.isEmpty(), "Visible chunk set must not be empty");

        Rectangle frustum = camera.getVisibleBounds();
        int minCx = visible.stream().mapToInt(Chunk::getChunkX).min().orElseThrow();
        int maxCx = visible.stream().mapToInt(Chunk::getChunkX).max().orElseThrow();
        int minCy = visible.stream().mapToInt(Chunk::getChunkY).min().orElseThrow();
        int maxCy = visible.stream().mapToInt(Chunk::getChunkY).max().orElseThrow();

        assertTrue(minCx * 1024.0f <= frustum.x);
        assertTrue((maxCx + 1) * 1024.0f >= frustum.x + frustum.width);
        assertTrue(minCy * 1024.0f <= frustum.y);
        assertTrue((maxCy + 1) * 1024.0f >= frustum.y + frustum.height);
    }

    @Test
    public void testParityBetweenGameCameraAndOrthographicCameraFrustumQuery() {
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-100_000f, -100_000f, 200_000f, 200_000f);
        camera.setZoom(GameCamera.DEFAULT_ZOOM);
        camera.setCenter(700.0f, 400.0f);

        chunkManager.updateStreaming(camera.getCenter(), camera);
        List<Chunk> viaGameCam = chunkManager.getVisibleChunks(camera);
        List<Chunk> viaGdxCam = chunkManager.getVisibleChunks(camera.getOrthographicCamera());

        assertEquals(viaGameCam.size(), viaGdxCam.size(), "Visible chunk count must match bit-identically");
        for (int i = 0; i < viaGameCam.size(); i++) {
            assertEquals(viaGameCam.get(i).getChunkKey(), viaGdxCam.get(i).getChunkKey());
        }
    }
}
