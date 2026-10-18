package io.github.elderpath_crusade.server.launcher;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.files.FileHandle;

import java.io.File;

/**
 * Headless Gdx.files whose internal paths resolve against an assets directory instead of the
 * process's working directory, so the server can be started from anywhere with --assets — or,
 * with no directory, against the classpath (a packaged game ships its assets inside its jar).
 */
final class AssetFiles implements Files {
    private final HeadlessFiles delegate = new HeadlessFiles();
    private final File assetsDir;

    /** assetsDir null = read internal paths from the classpath. */
    AssetFiles(File assetsDir) {
        this.assetsDir = assetsDir;
    }

    @Override
    public FileHandle getFileHandle(String path, FileType type) {
        return type == FileType.Internal ? internal(path) : delegate.getFileHandle(path, type);
    }

    @Override
    public FileHandle classpath(String path) {
        return delegate.classpath(path);
    }

    @Override
    public FileHandle internal(String path) {
        if (assetsDir == null) return delegate.classpath(path);
        return delegate.absolute(new File(assetsDir, path).getAbsolutePath());
    }

    @Override
    public FileHandle external(String path) {
        return delegate.external(path);
    }

    @Override
    public FileHandle absolute(String path) {
        return delegate.absolute(path);
    }

    @Override
    public FileHandle local(String path) {
        return delegate.local(path);
    }

    @Override
    public String getExternalStoragePath() {
        return delegate.getExternalStoragePath();
    }

    @Override
    public boolean isExternalStorageAvailable() {
        return delegate.isExternalStorageAvailable();
    }

    @Override
    public String getLocalStoragePath() {
        return delegate.getLocalStoragePath();
    }

    @Override
    public boolean isLocalStorageAvailable() {
        return delegate.isLocalStorageAvailable();
    }
}
