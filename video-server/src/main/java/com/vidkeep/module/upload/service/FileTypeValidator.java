package com.vidkeep.module.upload.service;

import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * 上传文件类型校验（AGENTS 红线：白名单 mp4/mov/mkv/avi/webm/flv，扩展名 + 魔数）。
 */
@Component
public class FileTypeValidator {

    private static final Logger log = LoggerFactory.getLogger(FileTypeValidator.class);

    /** 允许上传的视频扩展名 */
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp4", "mov", "mkv", "avi", "webm", "flv");

    /**
     * 取小写扩展名并校验白名单。
     *
     * @throws BizException 扩展名不合法
     */
    public String validateExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件名不能为空");
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件缺少扩展名");
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "不支持的文件类型，仅允许：" + String.join(" / ", ALLOWED_EXTENSIONS));
        }
        return extension;
    }

    /**
     * 校验文件头魔数，防止改后缀绕过。
     *
     * @throws BizException 文件内容不是受支持的视频容器
     */
    public void validateMagic(Path file) {
        byte[] header = new byte[16];
        int read;
        try (InputStream in = Files.newInputStream(file)) {
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无法读取文件内容");
        }
        String container = detectContainer(header, read);
        if (container == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件内容不是受支持的视频格式");
        }
        log.debug("文件魔数校验通过：container={}", container);
    }

    /**
     * 根据文件头识别容器类型，识别失败返回 null。
     */
    private String detectContainer(byte[] header, int length) {
        if (length >= 8 && "ftyp".equals(ascii(header, 4, 4))) {
            return "mp4/mov";
        }
        if (length >= 4
                && (header[0] & 0xFF) == 0x1A
                && (header[1] & 0xFF) == 0x45
                && (header[2] & 0xFF) == 0xDF
                && (header[3] & 0xFF) == 0xA3) {
            return "mkv/webm";
        }
        if (length >= 12 && "RIFF".equals(ascii(header, 0, 4)) && "AVI ".equals(ascii(header, 8, 4))) {
            return "avi";
        }
        if (length >= 3 && "FLV".equals(ascii(header, 0, 3))) {
            return "flv";
        }
        return null;
    }

    private String ascii(byte[] bytes, int offset, int count) {
        if (bytes.length < offset + count) {
            return "";
        }
        return new String(bytes, offset, count, java.nio.charset.StandardCharsets.US_ASCII);
    }
}
