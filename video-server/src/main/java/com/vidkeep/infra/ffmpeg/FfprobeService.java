package com.vidkeep.infra.ffmpeg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ffprobe 探测（文档 7.2.3）。
 */
@Component
public class FfprobeService {

    private static final Logger log = LoggerFactory.getLogger(FfprobeService.class);

    private final String ffprobePath;
    private final ObjectMapper objectMapper;

    public FfprobeService(@Value("${app.transcode.ffprobe-path:ffprobe}") String ffprobePath,
                          ObjectMapper objectMapper) {
        this.ffprobePath = ffprobePath;
        this.objectMapper = objectMapper;
    }

    /**
     * 探测源片：编码、容器、码率、宽高、时长。
     *
     * @throws BizException ffprobe 执行失败或解析失败（错误码 5002）
     */
    public ProbeResult probe(Path file) {
        List<String> command = List.of(
                ffprobePath,
                "-v", "error",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                "-select_streams", "v:0",
                file.toString());
        log.info("执行 ffprobe：{}", file);

        String output;
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BizException(ErrorCode.FFMPEG_FAILED, "ffprobe 执行超时");
            }
            if (process.exitValue() != 0) {
                throw new BizException(ErrorCode.FFMPEG_FAILED, "ffprobe 执行失败：" + truncate(output));
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.FFMPEG_FAILED, "无法启动 ffprobe：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.FFMPEG_FAILED, "ffprobe 执行被中断");
        }
        return parseJson(output);
    }

    private ProbeResult parseJson(String output) {
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BizException(ErrorCode.FFMPEG_FAILED, "ffprobe 输出无法解析");
        }
        try {
            JsonNode root = objectMapper.readTree(output.substring(start, end + 1));
            JsonNode streams = root.path("streams");
            if (!streams.isArray() || streams.isEmpty()) {
                throw new BizException(ErrorCode.FFMPEG_FAILED, "未找到视频流");
            }
            JsonNode video = streams.get(0);
            JsonNode format = root.path("format");

            String codecName = video.path("codec_name").asText(null);
            int width = video.path("width").asInt(0);
            int height = video.path("height").asInt(0);
            long bitRate = parseLong(format.path("bit_rate").asText(null));
            if (bitRate <= 0) {
                bitRate = parseLong(video.path("bit_rate").asText(null));
            }
            double duration = parseDouble(format.path("duration").asText(null));
            if (duration <= 0) {
                duration = parseDouble(video.path("duration").asText(null));
            }
            String formatName = format.path("format_name").asText("");
            ProbeResult result = new ProbeResult(codecName, formatName, bitRate, width, height, duration);
            log.info("ffprobe 结果：codec={}, format={}, bitRate={}, {}x{}, duration={}s",
                    codecName, formatName, bitRate, width, height, duration);
            return result;
        } catch (IOException e) {
            throw new BizException(ErrorCode.FFMPEG_FAILED, "ffprobe JSON 解析失败");
        }
    }

    private long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private double parseDouble(String value) {
        try {
            return value == null ? 0D : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0D;
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
