package io.github.elderpath_crusade.rendering;
import io.github.elderpath_crusade.GameContext;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import io.github.elderpath_crusade.utils.Logger;
import lombok.Getter;

public class ShaderManager {
    @Getter private ShaderProgram blurShader;
    @Getter private FrameBuffer fboA;
    @Getter private FrameBuffer fboB;

    public ShaderManager() {}

    public void initialize() {
        if (blurShader == null) {
            blurShader = new ShaderProgram(
                Gdx.files.internal("shaders/blur-vertex.glsl"),
                Gdx.files.internal("shaders/blur-fragment.glsl")
            );
            if (!blurShader.isCompiled()) {
                Logger.error("Shader", blurShader.getLog());
            }
        }
        createFbos();
    }

    public void createFbos() {
        if (fboA != null) fboA.dispose();
        if (fboB != null) fboB.dispose();

        fboA = new FrameBuffer(
            Pixmap.Format.RGBA8888,
            GameContext.get().getSettingsManager().screenSize.getScreenWidth(),
            GameContext.get().getSettingsManager().screenSize.getScreenHeight(),
            false
        );
        fboB = new FrameBuffer(
            Pixmap.Format.RGBA8888,
            GameContext.get().getSettingsManager().screenSize.getScreenWidth(),
            GameContext.get().getSettingsManager().screenSize.getScreenHeight(),
            false
        );
    }

    public void dispose() {
        if (blurShader != null) blurShader.dispose();
        if (fboA != null) fboA.dispose();
        if (fboB != null) fboB.dispose();
    }
}
