package io.github.elderpath_crusade.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.elderpath_crusade.GameContext;

public class RenderPipeline {
    public RenderPipeline() {}

    public void draw(SpriteBatch batch) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(GameContext.get().getSettingsManager().screenSize.getViewport().getCamera().combined);
        batch.begin();
        batch.setShader(null);
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        GameContext.get().getGraphicsManager().render(batch);
        batch.end();
    }

    public void blurredDraw(SpriteBatch batch) {
        ShaderManager shaderManager = GameContext.get().getShaderManager();
        ShaderProgram blurShader = shaderManager.getBlurShader();
        FrameBuffer fboA = shaderManager.getFboA();
        FrameBuffer fboB = shaderManager.getFboB();

        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

        fboA.begin();
        draw(batch);
        fboA.end();

        batch.setProjectionMatrix(GameContext.get().getSettingsManager().screenSize.getViewport().getCamera().combined);

        fboB.begin();
        batch.begin();
        batch.setShader(blurShader);
        blurShader.setUniformf("u_blurSize", 1f / (float) screenW);
        blurShader.setUniformf("u_direction", 1f, 0f);
        batch.draw(fboA.getColorBufferTexture(), 0, 0, screenW, screenH, 0f, 1f, 1f, 0f);
        batch.end();
        fboB.end();

        batch.begin();
        blurShader.setUniformf("u_blurSize", 1f / (float) screenH);
        blurShader.setUniformf("u_direction", 0f, 1f);
        batch.draw(fboB.getColorBufferTexture(), 0, 0, screenW, screenH, 0f, 1f, 1f, 0f);
        batch.end();

        batch.setShader(null);
    }

    public void drawPauseUI(SpriteBatch batch) {
        batch.begin();
        GameContext.get().getGraphicsManager().renderPauseUI(batch);
        batch.end();
    }
}
