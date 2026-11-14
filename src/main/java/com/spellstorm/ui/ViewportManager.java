package com.spellstorm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;

/**
 * Manages aspect-preserving FitViewport scaling (1280x720 virtual coordinates)
 * with black letterboxing and accurate screen-to-UI / screen-to-world unprojection
 * across arbitrary window resolutions (1280x720, 1920x1080, 2560x1440, 4:3, 21:9, etc.).
 *
 * Safely operates in both live LWJGL3 rendering and headless JUnit 5 environments
 * where Gdx.gl or Gdx.graphics may be null.
 */
public class ViewportManager {

    public static final float VIRTUAL_WIDTH = 1280.0f;
    public static final float VIRTUAL_HEIGHT = 720.0f;

    private final OrthographicCamera uiCamera;
    private final FitViewport fitViewport;

    private int screenWidth = (int) VIRTUAL_WIDTH;
    private int screenHeight = (int) VIRTUAL_HEIGHT;
    private int viewportX = 0;
    private int viewportY = 0;
    private int viewportWidth = (int) VIRTUAL_WIDTH;
    private int viewportHeight = (int) VIRTUAL_HEIGHT;

    public ViewportManager() {
        this.uiCamera = new OrthographicCamera();
        this.uiCamera.setToOrtho(true, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        this.uiCamera.update();

        this.fitViewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, this.uiCamera);
        update(screenWidth, screenHeight);
    }

    /**
     * Recomputes FitViewport letterbox dimensions for the given window pixel dimensions.
     */
    public void update(int width, int height) {
        this.screenWidth = Math.max(1, width);
        this.screenHeight = Math.max(1, height);

        float scaleX = (float) this.screenWidth / VIRTUAL_WIDTH;
        float scaleY = (float) this.screenHeight / VIRTUAL_HEIGHT;
        float scale = Math.min(scaleX, scaleY);

        this.viewportWidth = Math.max(1, Math.round(VIRTUAL_WIDTH * scale));
        this.viewportHeight = Math.max(1, Math.round(VIRTUAL_HEIGHT * scale));
        this.viewportX = (this.screenWidth - this.viewportWidth) / 2;
        this.viewportY = (this.screenHeight - this.viewportHeight) / 2;

        this.fitViewport.setScreenBounds(viewportX, viewportY, viewportWidth, viewportHeight);
        this.fitViewport.setWorldSize(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);

        this.uiCamera.setToOrtho(true, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        this.uiCamera.update();

        if (Gdx.gl != null) {
            this.fitViewport.apply(false);
        }
    }

    /**
     * Clears the entire physical backbuffer with solid black so letterbox/pillarbox
     * bars remain clean black, then activates the FitViewport GL region.
     */
    public void beginFrameClear() {
        if (Gdx.gl == null) {
            return;
        }
        int fbW = Gdx.graphics != null && Gdx.graphics.getBackBufferWidth() > 0
                ? Gdx.graphics.getBackBufferWidth() : screenWidth;
        int fbH = Gdx.graphics != null && Gdx.graphics.getBackBufferHeight() > 0
                ? Gdx.graphics.getBackBufferHeight() : screenHeight;
        Gdx.gl.glViewport(0, 0, fbW, fbH);
        Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    private void applyGlViewport() {
        if (Gdx.gl == null) {
            return;
        }
        float scaleX = 1.0f;
        float scaleY = 1.0f;
        if (Gdx.graphics != null && Gdx.graphics.getBackBufferWidth() > 0 && Gdx.graphics.getBackBufferHeight() > 0) {
            scaleX = (float) Gdx.graphics.getBackBufferWidth() / (float) screenWidth;
            scaleY = (float) Gdx.graphics.getBackBufferHeight() / (float) screenHeight;
        }
        Gdx.gl.glViewport(
                Math.round(viewportX * scaleX),
                Math.round(viewportY * scaleY),
                Math.max(1, Math.round(viewportWidth * scaleX)),
                Math.max(1, Math.round(viewportHeight * scaleY))
        );
    }

    /**
     * Applies the letterboxed FitViewport to GL and binds the given camera.
     */
    public void applyWorldViewport(OrthographicCamera worldCamera) {
        applyGlViewport();
        if (worldCamera != null) {
            worldCamera.update();
        }
    }

    /**
     * Applies the letterboxed FitViewport to GL and binds the fixed 1280x720 y-down UI camera.
     */
    public void applyUiViewport() {
        applyGlViewport();
        uiCamera.update();
    }

    /**
     * Unprojects physical window coordinates (origin top-left, y-down) into
     * virtual 1280x720 UI coordinates (origin top-left, y-down).
     */
    public Vector2D screenToUi(float screenX, float screenY) {
        float uiX = (screenX - viewportX) * (VIRTUAL_WIDTH / (float) viewportWidth);
        float uiY = (screenY - viewportY) * (VIRTUAL_HEIGHT / (float) viewportHeight);
        return new Vector2D(uiX, uiY);
    }

    /**
     * Unprojects physical window coordinates into world space through the active
     * FitViewport and GameCamera.
     */
    public Vector2D screenToWorld(GameCamera camera, float screenX, float screenY) {
        Vector2D ui = screenToUi(screenX, screenY);
        if (camera == null) {
            return ui;
        }
        return camera.screenToWorld(ui.x, ui.y);
    }

    /**
     * Returns true if the given physical window coordinate falls inside the active
     * FitViewport content rectangle (outside black letterbox/pillarbox bars).
     */
    public boolean isInsideViewport(float screenX, float screenY) {
        return screenX >= viewportX && screenX <= viewportX + viewportWidth
                && screenY >= viewportY && screenY <= viewportY + viewportHeight;
    }

    public OrthographicCamera getUiCamera() {
        return uiCamera;
    }

    public FitViewport getFitViewport() {
        return fitViewport;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public int getViewportX() {
        return viewportX;
    }

    public int getViewportY() {
        return viewportY;
    }

    public int getViewportWidth() {
        return viewportWidth;
    }

    public int getViewportHeight() {
        return viewportHeight;
    }

    public Rectangle getViewportBounds() {
        return new Rectangle(viewportX, viewportY, viewportWidth, viewportHeight);
    }
}
