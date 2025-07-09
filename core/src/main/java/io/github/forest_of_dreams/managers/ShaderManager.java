package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import lombok.Getter;

public class ShaderManager {
    @Getter private static ShaderProgram blurShader;
    @Getter private static FrameBuffer fboA;
    @Getter private static FrameBuffer fboB;

    public static void initialize() {
        if (blurShader == null) {
            blurShader = new ShaderProgram(
                Gdx.files.internal("shaders/blur-vertex.glsl"),
                Gdx.files.internal("shaders/blur-fragment.glsl")
            );
            if (!blurShader.isCompiled()) {
                Gdx.app.error("Shader", blurShader.getLog());
            }
        }
        createFbos();
    }

    public static void createFbos() {
        if (fboA != null) fboA.dispose();
        if (fboB != null) fboB.dispose();

        fboA = new FrameBuffer(
            Pixmap.Format.RGBA8888,
            SettingsManager.screenSize.getScreenWidth(),
            SettingsManager.screenSize.getScreenHeight(),
            false
        );
        fboB = new FrameBuffer(
            Pixmap.Format.RGBA8888,
            SettingsManager.screenSize.getScreenWidth(),
            SettingsManager.screenSize.getScreenHeight(),
            false
        );
    }

    public static void dispose() {
        if (blurShader != null) blurShader.dispose();
        if (fboA != null) fboA.dispose();
        if (fboB != null) fboB.dispose();
    }
}
