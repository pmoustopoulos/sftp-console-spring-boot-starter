package io.github.pmoustopoulos.sftpconsole.fs;

import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.session.SessionContext;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

/**
 * MINA SSHD {@link FileSystemFactory} that serves one shared in-memory filesystem to
 * every SFTP session, wrapped so MINA cannot close it (see {@link NonClosingFileSystem}).
 */
public final class SharedFileSystemFactory implements FileSystemFactory {

    private final FileSystem sharedFileSystem;

    public SharedFileSystemFactory(FileSystem sharedFileSystem) {
        this.sharedFileSystem = new NonClosingFileSystem(sharedFileSystem);
    }

    @Override
    public Path getUserHomeDir(SessionContext session) {
        return sharedFileSystem.getPath("/");
    }

    @Override
    public FileSystem createFileSystem(SessionContext session) throws IOException {
        return sharedFileSystem;
    }
}
