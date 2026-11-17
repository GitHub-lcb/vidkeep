package com.vidkeep.module.play.controller;

import com.vidkeep.common.ErrorCode;
import com.vidkeep.common.Result;
import com.vidkeep.infra.storage.StorageService;
import com.vidkeep.security.PlayTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * HLS 分发（P1 防盗链）：校验播放地址签名，并把 m3u8 内的子列表/切片改写为带签名的地址。
 *
 * <p>访问链路：播放接口返回带签名的 master.m3u8 → 播放器请求 master（校验）→
 * 服务端返回改写后的子列表地址（带签名）→ 子列表请求（校验）→ 返回改写后的切片地址（带签名）→
 * 切片请求（校验）后返回 TS 内容。这样外站即使拿到 master 地址，2 小时后也会失效。</p>
 */
@RestController
public class HlsStreamController {

    private static final Logger log = LoggerFactory.getLogger(HlsStreamController.class);

    private static final Pattern RENDITION_PATTERN = Pattern.compile("^(720p|480p)$");
    private static final Pattern SEGMENT_PATTERN = Pattern.compile("^seg_\\d{5}\\.ts$");
    private static final MediaType HLS_CONTENT_TYPE = MediaType.parseMediaType("application/vnd.apple.mpegurl");
    private static final MediaType TS_CONTENT_TYPE = MediaType.parseMediaType("video/mp2t");

    private final StorageService storageService;
    private final PlayTokenService playTokenService;

    public HlsStreamController(StorageService storageService, PlayTokenService playTokenService) {
        this.storageService = storageService;
        this.playTokenService = playTokenService;
    }

    /**
     * master.m3u8：校验签名，并把各档位子列表地址改写为带签名地址。
     */
    @GetMapping("/hls/{episodeId}/master.m3u8")
    public ResponseEntity<?> masterPlaylist(@PathVariable Long episodeId,
                                            @RequestParam(required = false) String expire,
                                            @RequestParam(required = false) String token) {
        String requestPath = "/hls/" + episodeId + "/master.m3u8";
        if (!playTokenService.verify(requestPath, expire, token)) {
            return forbidden();
        }
        Path file = storageService.resolve("hls/" + episodeId + "/master.m3u8");
        if (!Files.isRegularFile(file)) {
            return notFound();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String rewritten = rewritePlaylist(content, line -> {
                String relative = line.trim();
                if (relative.isEmpty()) {
                    return line;
                }
                String childPath = "/hls/" + episodeId + "/" + relative;
                return playTokenService.buildSignedSegmentUrl(childPath);
            });
            return textResponse(rewritten);
        } catch (IOException e) {
            log.error("读取 master.m3u8 失败：episodeId={}", episodeId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 档位子列表 720p/index.m3u8：校验签名，并把切片地址改写为带签名地址。
     */
    @GetMapping("/hls/{episodeId}/{rendition}/index.m3u8")
    public ResponseEntity<?> variantPlaylist(@PathVariable Long episodeId,
                                             @PathVariable String rendition,
                                             @RequestParam(required = false) String expire,
                                             @RequestParam(required = false) String token) {
        if (!RENDITION_PATTERN.matcher(rendition).matches()) {
            return notFound();
        }
        String requestPath = "/hls/" + episodeId + "/" + rendition + "/index.m3u8";
        if (!playTokenService.verify(requestPath, expire, token)) {
            return forbidden();
        }
        Path file = storageService.resolve("hls/" + episodeId + "/" + rendition + "/index.m3u8");
        if (!Files.isRegularFile(file)) {
            return notFound();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String rewritten = rewritePlaylist(content, line -> {
                String relative = line.trim();
                if (relative.isEmpty()) {
                    return line;
                }
                String segmentPath = "/hls/" + episodeId + "/" + rendition + "/" + relative;
                return playTokenService.buildSignedSegmentUrl(segmentPath);
            });
            return textResponse(rewritten);
        } catch (IOException e) {
            log.error("读取档位 playlist 失败：episodeId={}, rendition={}", episodeId, rendition, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * TS 切片：校验签名后返回二进制内容（支持 Range，浏览器拖动进度条会用到）。
     */
    @GetMapping("/hls/{episodeId}/{rendition}/{segment}")
    public ResponseEntity<?> segment(@PathVariable Long episodeId,
                                     @PathVariable String rendition,
                                     @PathVariable String segment,
                                     @RequestParam(required = false) String expire,
                                     @RequestParam(required = false) String token) {
        if (!RENDITION_PATTERN.matcher(rendition).matches() || !SEGMENT_PATTERN.matcher(segment).matches()) {
            return notFound();
        }
        String requestPath = "/hls/" + episodeId + "/" + rendition + "/" + segment;
        if (!playTokenService.verify(requestPath, expire, token)) {
            return forbidden();
        }
        Path file = storageService.resolve("hls/" + episodeId + "/" + rendition + "/" + segment);
        if (!Files.isRegularFile(file)) {
            return notFound();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(TS_CONTENT_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .body(resource);
    }

    /**
     * 逐行改写播放列表：注释/空行原样保留，其余行（相对地址）交由 rewriter 处理。
     */
    private String rewritePlaylist(String content, LineRewriter rewriter) {
        StringBuilder result = new StringBuilder(content.length() + 256);
        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                result.append(line);
            } else {
                result.append(rewriter.rewrite(line));
            }
            if (i < lines.length - 1) {
                result.append('\n');
            }
        }
        return result.toString();
    }

    private ResponseEntity<String> textResponse(String content) {
        return ResponseEntity.ok()
                .contentType(new MediaType(HLS_CONTENT_TYPE, StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .body(content);
    }

    private ResponseEntity<Result<Void>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.fail(ErrorCode.PLAY_TOKEN_INVALID));
    }

    private ResponseEntity<Result<Void>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.fail(ErrorCode.NOT_FOUND));
    }

    @FunctionalInterface
    private interface LineRewriter {
        String rewrite(String line);
    }
}
