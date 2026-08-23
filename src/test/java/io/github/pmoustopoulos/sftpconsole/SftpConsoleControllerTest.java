package io.github.pmoustopoulos.sftpconsole;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SftpConsoleController.class)
class SftpConsoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SftpConsoleService service;

    @BeforeEach
    void setUp() {

        // start from an empty root each test
        for (var e : service.list("/")) {
            service.delete(e.path());
        }

        service.upload("/", "hello.txt", "hi".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void servesConsolePageWithBasePathSubstituted() throws Exception {
        mockMvc.perform(get("/sftp-console"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/sftp-console")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("__BASE_PATH__"))));
    }

    @Test
    void listsFiles() throws Exception {
        mockMvc.perform(get("/sftp-console/api/files").param("path", "/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='hello.txt')]").exists());
    }

    @Test
    void uploadsFile() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
                "file", "new.txt", "text/plain", "data".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/sftp-console/api/files/upload").file(file).param("path", "/"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/sftp-console/api/files").param("path", "/"))
                .andExpect(jsonPath("$[?(@.name=='new.txt')]").exists());
    }

    @Test
    void renamesViaJsonBody() throws Exception {
        mockMvc.perform(post("/sftp-console/api/files/rename")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"/hello.txt\",\"to\":\"/renamed.txt\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void traversalPathReturns400() throws Exception {
        mockMvc.perform(get("/sftp-console/api/files").param("path", "/../etc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingFileReturns404() throws Exception {
        mockMvc.perform(get("/sftp-console/api/files/download").param("path", "/nope.txt"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/sftp-console/api/files").param("path", "/hello.txt"))
                .andExpect(status().isNoContent());
    }

    @Test
    void previewOfBinaryReturns415() throws Exception {

        service.upload("/", "blob.bin", new byte[]{1, 2, 3});

        mockMvc.perform(get("/sftp-console/api/files/preview").param("path", "/blob.bin"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void createFolderReturns204AndFolderAppears() throws Exception {

        mockMvc.perform(post("/sftp-console/api/files/folder").param("path", "/newdir"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/sftp-console/api/files").param("path", "/"))
                .andExpect(jsonPath("$[?(@.name=='newdir' && @.directory==true)]").exists());
    }

    @TestConfiguration
    static class Config {

        @Bean
        SftpConsoleProperties sftpConsoleProperties() {
            return new SftpConsoleProperties();
        }

        @Bean
        SftpConsoleService sftpConsoleService() {
            FileSystem fs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
                    .setWorkingDirectory("/").build());
            return new SftpConsoleService(fs);
        }

        @Bean
        SftpConsoleController sftpConsoleController(SftpConsoleService service,
                                                   SftpConsoleProperties properties) {
            return new SftpConsoleController(service, properties);
        }
    }
}
