package io.github.pmoustopoulos.sftpconsole.fs;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.io.FileInfoExtractor;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.sftp.server.SftpSubsystem;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

/**
 * An {@link SftpSubsystemFactory} whose subsystem tolerates file attributes the backing filesystem
 * cannot provide.
 *
 * <p>An in-memory {@code Jimfs} filesystem doesn't support every attribute a client may ask for in a
 * {@code STAT}/{@code LSTAT} (clients request a broad set, e.g. flags {@code 0xfffd}). MINA's default
 * resolver calls {@code FileInfoExtractor.infoOf(...)} for each missing attribute, and those
 * extractors throw {@link UnsupportedOperationException} — a {@code RuntimeException} that MINA's
 * per-attribute {@code catch (IOException)} does not catch, so the whole {@code STAT} fails with
 * {@code SSH_FX_OP_UNSUPPORTED}. That breaks standard clients (FileZilla, WinSCP, the {@code sftp}
 * CLI) and MINA's own stream-based {@code read(path)} (which stats first for the file size).
 *
 * <p>Here we swallow that {@link UnsupportedOperationException} and simply omit the unsupported
 * attribute, so {@code STAT} returns the attributes that <em>are</em> available (size, times, …) and
 * the operation succeeds — behaving like a normal SFTP server.
 */
public final class TolerantSftpSubsystemFactory extends SftpSubsystemFactory {

    @Override
    public Command createSubsystem(ChannelSession channel) throws IOException {
        SftpSubsystem subsystem = new SftpSubsystem(channel, this) {
            @Override
            protected Object resolveMissingFileAttributeValue(
                    Path file, String name, Object value, FileInfoExtractor<?> x, LinkOption... options)
                    throws IOException {
                try {
                    return super.resolveMissingFileAttributeValue(file, name, value, x, options);
                } catch (UnsupportedOperationException e) {
                    // The in-memory filesystem can't provide this attribute — omit it rather than
                    // failing the whole STAT. Returning the original value makes MINA skip it.
                    return value;
                }
            }
        };
        GenericUtils.forEach(getRegisteredListeners(), subsystem::addSftpEventListener);
        return subsystem;
    }
}
