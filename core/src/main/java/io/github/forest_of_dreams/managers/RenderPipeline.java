package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.ScreenUtils;

/**
 * Rendering orchestration and post-processing passes.
 * GraphicsManager delegates high-level draw calls here to reduce its size.
 */
public final class RenderPipeline {
    private RenderPipeline() {}

    public static void draw(SpriteBatch batch) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(SettingsManager.screenSize.getViewport().getCamera().combined);
        batch.begin();
        batch.setShader(null);
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        GraphicsManager.render(batch);
        batch.end();
    }

    public static void blurredDraw(SpriteBatch batch) {
        ShaderProgram blurShader = ShaderManager.getBlurShader();
        FrameBuffer fboA = ShaderManager.getFboA();
        FrameBuffer fboB = ShaderManager.getFboB();

        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();

        // First pass - capture the scene into fboA
        fboA.begin();
        draw(batch);
        fboA.end();

        // Prepare projection for post-process passes
        batch.setProjectionMatrix(SettingsManager.screenSize.getViewport().getCamera().combined);

        // Horizontal blur into fboB
        fboB.begin();
        batch.begin();
        batch.setShader(blurShader);
        blurShader.setUniformf("u_blurSize", 1f / (float) screenW);
        blurShader.setUniformf("u_direction", 1f, 0f);
        // Flip Y when drawing FrameBuffer texture
        batch.draw(
            fboA.getColorBufferTexture(),
            0, 0,
            screenW, screenH,
            0f, 1f, 1f, 0f
        );
        batch.end();
        fboB.end();

        // Vertical blur (final pass) from fboB to screen
        batch.begin();
        blurShader.setUniformf("u_blurSize", 1f / (float) screenH);
        blurShader.setUniformf("u_direction", 0f, 1f);
        batch.draw(
            fboB.getColorBufferTexture(),
            0, 0,
            screenW, screenH,
            0f, 1f, 1f, 0f
        );
        batch.end();

        batch.setShader(null);
    }

    public static void drawPauseUI(SpriteBatch batch) {
        batch.begin();
        GraphicsManager.renderPauseUI(batch);
        batch.end();
    }
}
