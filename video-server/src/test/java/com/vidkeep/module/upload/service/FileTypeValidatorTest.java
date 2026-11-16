package com.vidkeep.module.upload.service;

import com.vidkeep.common.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 文件类型白名单与魔数校验单元测试（AGENTS 4.1）。
 */
class FileTypeValidatorTest {

    private final FileTypeValidator validator = new FileTypeValidator();

    @Test
    void shouldAcceptWhitelistedExtensions() {
        assertEquals("mp4", validator.validateExtension("movie.MP4"));
        assertEquals("mkv", validator.validateExtension("a.mkv"));
        assertEquals("webm", validator.validateExtension("b.webm"));
    }

    @Test
    void shouldRejectNonWhitelistedExtension() {
        assertThrows(BizException.class, () -> validator.validateExtension("shell.sh"));
        assertThrows(BizException.class, () -> validator.validateExtension("no-extension"));
        assertThrows(BizException.class, () -> validator.validateExtension(null));
    }

    @Test
    void shouldAcceptMp4Magic(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("sample.mp4");
        byte[] header = new byte[16];
        header[0] = 0x00;
        header[1] = 0x00;
        header[2] = 0x00;
        header[3] = 0x18;
        header[4] = 'f';
        header[5] = 't';
        header[6] = 'y';
        header[7] = 'p';
        Files.write(file, header);

        assertDoesNotThrow(() -> validator.validateMagic(file));
    }

    @Test
    void shouldRejectFakeVideoByMagic(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("fake.mp4");
        Files.write(file, "#!/bin/sh\necho hacked\n".getBytes(StandardCharsets.UTF_8));

        assertThrows(BizException.class, () -> validator.validateMagic(file));
    }
}
