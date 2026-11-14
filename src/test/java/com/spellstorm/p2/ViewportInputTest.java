package com.spellstorm.p2;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;
import com.spellstorm.ui.PauseMenuOverlay;
import com.spellstorm.ui.SettingsOverlay;
import com.spellstorm.ui.TitleScreen;
import com.spellstorm.ui.ViewportManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2: ViewportManager Unprojection & Shell Overlay Input Tests")
public class ViewportInputTest {

    @BeforeAll
    static void init() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("screenToUi maps center and corners accurately at 1280x720, 1920x1080, and 2560x1440")
    void testScreenToUiStandard16By9Resolutions() {
        ViewportManager vm = new ViewportManager();

        int[][] resolutions = {{1280, 720}, {1920, 1080}, {2560, 1440}};
        for (int[] res : resolutions) {
            int w = res[0];
            int h = res[1];
            vm.update(w, h);

            assertEquals(0, vm.getViewportX());
            assertEquals(0, vm.getViewportY());
            assertEquals(w, vm.getViewportWidth());
            assertEquals(h, vm.getViewportHeight());

            Vector2D topLeft = vm.screenToUi(0, 0);
            assertEquals(0.0f, topLeft.x, 0.01f);
            assertEquals(0.0f, topLeft.y, 0.01f);

            Vector2D center = vm.screenToUi(w * 0.5f, h * 0.5f);
            assertEquals(640.0f, center.x, 0.01f);
            assertEquals(360.0f, center.y, 0.01f);

            Vector2D bottomRight = vm.screenToUi(w, h);
            assertEquals(1280.0f, bottomRight.x, 0.01f);
            assertEquals(720.0f, bottomRight.y, 0.01f);
        }
    }

    @Test
    @DisplayName("screenToUi handles vertical letterboxing (1280x1024) and horizontal pillarboxing (2560x1080)")
    void testNon16By9LetterboxAndPillarboxUnprojection() {
        ViewportManager vm = new ViewportManager();

        // 5:4 taller window -> black bars top & bottom (152px each)
        vm.update(1280, 1024);
        assertEquals(0, vm.getViewportX());
        assertEquals(152, vm.getViewportY());
        assertEquals(1280, vm.getViewportWidth());
        assertEquals(720, vm.getViewportHeight());

        assertFalse(vm.isInsideViewport(640, 50));
        assertTrue(vm.isInsideViewport(640, 512));

        Vector2D letterboxTop = vm.screenToUi(640, 152);
        assertEquals(640.0f, letterboxTop.x, 0.01f);
        assertEquals(0.0f, letterboxTop.y, 0.01f);

        Vector2D letterboxCenter = vm.screenToUi(640, 512);
        assertEquals(640.0f, letterboxCenter.x, 0.01f);
        assertEquals(360.0f, letterboxCenter.y, 0.01f);

        // 21:9 ultra-wide window -> black bars left & right (320px each)
        vm.update(2560, 1080);
        assertEquals(320, vm.getViewportX());
        assertEquals(0, vm.getViewportY());
        assertEquals(1920, vm.getViewportWidth());
        assertEquals(1080, vm.getViewportHeight());

        assertFalse(vm.isInsideViewport(100, 540));
        assertTrue(vm.isInsideViewport(1280, 540));

        Vector2D pillarLeft = vm.screenToUi(320, 0);
        assertEquals(0.0f, pillarLeft.x, 0.01f);
        assertEquals(0.0f, pillarLeft.y, 0.01f);

        Vector2D pillarCenter = vm.screenToUi(1280, 540);
        assertEquals(640.0f, pillarCenter.x, 0.01f);
        assertEquals(360.0f, pillarCenter.y, 0.01f);
    }

    @Test
    @DisplayName("screenToWorld maps scaled/letterboxed window clicks consistently to world coordinates")
    void testScreenToWorldConsistencyAcrossResolutions() {
        ViewportManager vm = new ViewportManager();
        GameCamera camera = new GameCamera(1280f, 720f);
        camera.setCenter(2048f, 2048f);
        camera.setZoom(1.0f);

        vm.update(1280, 720);
        Vector2D refCenter = vm.screenToWorld(camera, 640f, 360f);
        Vector2D refOffset = vm.screenToWorld(camera, 960f, 540f);

        // 1920x1080 (1.5x scale)
        vm.update(1920, 1080);
        Vector2D hdCenter = vm.screenToWorld(camera, 960f, 540f);
        Vector2D hdOffset = vm.screenToWorld(camera, 1440f, 810f);
        assertEquals(refCenter.x, hdCenter.x, 0.05f);
        assertEquals(refCenter.y, hdCenter.y, 0.05f);
        assertEquals(refOffset.x, hdOffset.x, 0.05f);
        assertEquals(refOffset.y, hdOffset.y, 0.05f);

        // 2560x1080 pillarboxed (1.5x scale + 320px left bar)
        vm.update(2560, 1080);
        Vector2D uwCenter = vm.screenToWorld(camera, 1280f, 540f);
        assertEquals(refCenter.x, uwCenter.x, 0.05f);
        assertEquals(refCenter.y, uwCenter.y, 0.05f);
    }

    @Test
    @DisplayName("TitleScreen, PauseMenuOverlay, and SettingsOverlay button hits work via scaled unprojection")
    void testShellOverlaysInteraction() {
        ViewportManager vm = new ViewportManager();
        vm.update(1920, 1080);

        TitleScreen title = new TitleScreen();
        Rectangle playBounds = title.getPlayButtonBounds();
        // Convert virtual center of Play button back to 1920x1080 screen coords
        float screenPlayX = (playBounds.x + playBounds.width * 0.5f) * 1.5f;
        float screenPlayY = (playBounds.y + playBounds.height * 0.5f) * 1.5f;
        assertEquals(TitleScreen.Action.PLAY, title.handleMouseClick(vm.screenToUi(screenPlayX, screenPlayY)));

        // Quit confirmation flow on TitleScreen
        Rectangle quitBounds = title.getQuitButtonBounds();
        assertEquals(TitleScreen.Action.NONE, title.handleMouseClick(new Vector2D(quitBounds.x + 10, quitBounds.y + 10)));
        assertTrue(title.isQuitConfirmOpen());
        Rectangle cancelBounds = title.getCancelQuitButtonBounds();
        assertEquals(TitleScreen.Action.NONE, title.handleMouseClick(new Vector2D(cancelBounds.x + 10, cancelBounds.y + 10)));
        assertFalse(title.isQuitConfirmOpen());

        // Quit confirm -> CONFIRM returns QUIT
        title.handleMouseClick(new Vector2D(quitBounds.x + 10, quitBounds.y + 10));
        Rectangle confirmBounds = title.getConfirmQuitButtonBounds();
        assertEquals(TitleScreen.Action.QUIT, title.handleMouseClick(new Vector2D(confirmBounds.x + 10, confirmBounds.y + 10)));

        // PauseMenuOverlay
        PauseMenuOverlay pause = new PauseMenuOverlay();
        pause.open();
        assertTrue(pause.isOpen());
        Rectangle resumeBtn = pause.getResumeButtonBounds();
        assertEquals(PauseMenuOverlay.Action.RESUME, pause.handleMouseClick(new Vector2D(resumeBtn.x + 10, resumeBtn.y + 10)));

        Rectangle quitToTitleBtn = pause.getQuitToTitleButtonBounds();
        assertEquals(PauseMenuOverlay.Action.NONE, pause.handleMouseClick(new Vector2D(quitToTitleBtn.x + 10, quitToTitleBtn.y + 10)));
        assertTrue(pause.isQuitConfirmOpen());
        Rectangle pauseConfirm = pause.getConfirmQuitButtonBounds();
        assertEquals(PauseMenuOverlay.Action.QUIT_TO_TITLE, pause.handleMouseClick(new Vector2D(pauseConfirm.x + 10, pauseConfirm.y + 10)));

        // SettingsOverlay
        SettingsOverlay settings = new SettingsOverlay();
        settings.open();
        assertFalse(settings.isFullscreen());
        Rectangle fsBtn = settings.getFullscreenButtonBounds();
        assertEquals(SettingsOverlay.Action.TOGGLE_FULLSCREEN, settings.handleMouseClick(new Vector2D(fsBtn.x + 10, fsBtn.y + 10)));
        assertTrue(settings.isFullscreen());
        assertEquals(SettingsOverlay.Action.BACK, settings.handleKeyPress(Keys.ESCAPE));
    }
}
