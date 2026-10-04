package io.github.pmoustopoulos.sftpconsole;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.github.pmoustopoulos.sftpconsole.dto.FileContent;
import io.github.pmoustopoulos.sftpconsole.dto.FileEntry;
import io.github.pmoustopoulos.sftpconsole.dto.PreviewContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SftpConsoleServiceTest {

    private FileSystem fs;
    private SftpConsoleService service;

    @BeforeEach
    void setUp() {

        fs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
                .setWorkingDirectory("/").build());

        service = new SftpConsoleService(fs);
    }

    @AfterEach
    void tearDown() throws Exception {
        fs.close();
    }

    @Test
    void uploadThenListAndDownload() {

        service.upload("/", "hello.txt", "hi there".getBytes(StandardCharsets.UTF_8));

        List<FileEntry> entries = service.list("/");
        assertThat(entries).extracting(FileEntry::name).contains("hello.txt");
        FileEntry entry = entries.stream().filter(e -> e.name().equals("hello.txt")).findFirst().orElseThrow();
        assertThat(entry.directory()).isFalse();
        assertThat(entry.contentType()).isEqualTo("text/plain");

        FileContent content = service.download("/hello.txt");
        assertThat(new String(content.data(), StandardCharsets.UTF_8)).isEqualTo("hi there");
        assertThat(content.filename()).isEqualTo("hello.txt");
    }

    @Test
    void createFolderRenameAndDeleteRecursively() throws Exception {

        service.createFolder("/docs");
        service.upload("/docs", "a.txt", "a".getBytes(StandardCharsets.UTF_8));

        service.rename("/docs", "/archive");
        assertThat(Files.exists(fs.getPath("/archive/a.txt"))).isTrue();
        assertThat(Files.exists(fs.getPath("/docs"))).isFalse();

        service.delete("/archive");
        assertThat(Files.exists(fs.getPath("/archive"))).isFalse();
    }

    @Test
    void previewClassifiesTextImagePdfAndBinary() {

        service.upload("/", "note.txt", "readable".getBytes(StandardCharsets.UTF_8));
        service.upload("/", "pic.png", new byte[]{1, 2, 3});
        service.upload("/", "doc.pdf", new byte[]{'%', 'P', 'D', 'F'});
        service.upload("/", "blob.bin", new byte[]{4, 5, 6});

        PreviewContent text = service.preview("/note.txt");
        assertThat(text.previewable()).isTrue();
        assertThat(text.contentType()).isEqualTo("text/plain");

        PreviewContent image = service.preview("/pic.png");
        assertThat(image.previewable()).isTrue();
        assertThat(image.contentType()).isEqualTo("image/png");

        PreviewContent pdf = service.preview("/doc.pdf");
        assertThat(pdf.previewable()).isTrue();
        assertThat(pdf.contentType()).isEqualTo("application/pdf");
        assertThat(pdf.data()).isNotEmpty();

        PreviewContent binary = service.preview("/blob.bin");
        assertThat(binary.previewable()).isFalse();
    }

    @Test
    void largeTextPreviewIsTruncated() {

        service.upload("/", "big.txt", new byte[1_500_000]);

        PreviewContent preview = service.preview("/big.txt");
        assertThat(preview.previewable()).isTrue();
        assertThat(preview.contentType()).isEqualTo("text/plain");
        assertThat(preview.data()).hasSize(1_000_000);
        assertThat(preview.truncated()).isTrue();
    }

    @Test
    void truncationNeverSplitsAMultiByteCharacter() {

        service = new SftpConsoleService(fs, 4);
        // "aé€" = 61 | C3 A9 | E2 82 AC — a 4-byte cut would land inside the euro sign
        service.upload("/", "utf8.txt", "aé€".getBytes(StandardCharsets.UTF_8));

        PreviewContent preview = service.preview("/utf8.txt");
        assertThat(new String(preview.data(), StandardCharsets.UTF_8)).isEqualTo("aé");
        assertThat(preview.truncated()).isTrue();
    }

    @Test
    void smallTextPreviewIsNotTruncated() {
        service.upload("/", "small.txt", "hi".getBytes(StandardCharsets.UTF_8));
        assertThat(service.preview("/small.txt").truncated()).isFalse();
    }

    @Test
    void renameRefusesToOverwriteAnExistingPath() {

        service.upload("/", "a.txt", "keep me".getBytes(StandardCharsets.UTF_8));
        service.upload("/", "b.txt", "b".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.rename("/b.txt", "/a.txt"))
                .isInstanceOf(ConflictException.class);
        assertThat(service.download("/a.txt").data()).isEqualTo("keep me".getBytes(StandardCharsets.UTF_8));
        assertThat(service.download("/b.txt").data()).isEqualTo("b".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void renameOntoItselfIsANoOp() {
        service.upload("/", "a.txt", "a".getBytes(StandardCharsets.UTF_8));
        service.rename("/a.txt", "/a.txt");
        assertThat(service.download("/a.txt").data()).isEqualTo("a".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void cannotMoveAFolderIntoItself() {

        service.createFolder("/docs/sub");

        assertThatThrownBy(() -> service.rename("/docs", "/docs/sub/docs"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.list("/docs")).extracting(FileEntry::name).containsExactly("sub");
    }

    @Test
    void movesAFileIntoAFolder() {

        service.upload("/", "a.txt", "a".getBytes(StandardCharsets.UTF_8));
        service.createFolder("/docs");

        service.rename("/a.txt", "/docs/a.txt");

        assertThat(service.list("/docs")).extracting(FileEntry::name).containsExactly("a.txt");
    }

    @Test
    void listOrdersDirectoriesBeforeFiles() {

        service.upload("/", "zeta.txt", "z".getBytes(StandardCharsets.UTF_8));
        service.createFolder("/alpha");

        List<FileEntry> entries = service.list("/");
        assertThat(entries.getFirst().name()).isEqualTo("alpha");
        assertThat(entries.getFirst().directory()).isTrue();
    }

    @Test
    void rejectsPathTraversal() {

        assertThatThrownBy(() -> service.list("/../etc"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.upload("/..", "evil.txt", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.upload("/", "..", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.upload("/", ".", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingPathThrowsNotFound() {
        assertThatThrownBy(() -> service.download("/nope.txt"))
                .isInstanceOf(NotFoundException.class);
    }
}
