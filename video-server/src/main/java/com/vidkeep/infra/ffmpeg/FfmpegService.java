package com.vidkeep.infra.ffmpeg;

import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * FFmpeg 命令封装（文档 7.2.4 / 7.2.5）。
 *
 * <p>关键点：</p>
 * <ul>
 *   <li>REMUX 只转封装（{@code -c copy}），不重编码；</li>
 *   <li>TRANSCODE 按档位重编码并输出 HLS；</li>
 *   <li>通过 {@code -progress pipe:1 -nostats} 解析转码进度；</li>
 *   <li>支持按 episodeId 强制终止进程（取消转码）。</li>
 * </ul>
 */
@Component
public class FfmpegService {

    private static final Logger log = LoggerFactory.getLogger(FfmpegService.class);

    /** 进度回调：percent 取值 0–100 */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(double percent);
    }

    private final String ffmpegPath;
    private final int hlsTimeSeconds;
    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();

    public FfmpegService(@Value("${app.transcode.ffmpeg-path:ffmpeg}") String ffmpegPath,
                         @Value("${app.transcode.hls-time-seconds:6}") int hlsTimeSeconds) {
        this.ffmpegPath = ffmpegPath;
        this.hlsTimeSeconds = hlsTimeSeconds;
    }

    /**
     * REMUX：HLS 切片仍按 6 秒，视频/音频流直接拷贝。
     */
    public void remux(Long episodeId, Path input, Path hlsDir, double durationSeconds,
                      ProgressListener listener) {
        Path profileDir = hlsDir.resolve(TranscodeProfile.P720.getDirectoryName());
        createDirectories(profileDir);
        List<String> command = new ArrayList<>(List.of(
                ffmpegPath, "-y", "-i", input.toString(),
                "-c", "copy",
                "-f", "hls",
                "-hls_time", String.valueOf(hlsTimeSeconds),
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", profileDir.resolve("seg_%05d.ts").toString(),
                "-progress", "pipe:1", "-nostats",
                profileDir.resolve("index.m3u8").toString()));
        run(episodeId, command, durationSeconds, listener);
    }

    /**
     * TRANSCODE：按档位重编码。
     */
    public void transcode(Long episodeId, Path input, Path hlsDir, TranscodeProfile profile,
                          double durationSeconds, ProgressListener listener) {
        Path profileDir = hlsDir.resolve(profile.getDirectoryName());
        createDirectories(profileDir);
        List<String> command = new ArrayList<>(List.of(
                ffmpegPath, "-y", "-i", input.toString(),
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-crf", profile.getCrf(),
                "-c:a", "aac",
                "-b:a", profile.getAudioBitRate(),
                "-ac", "2",
                "-vf", "scale=-2:" + profile.getHeight(),
                "-f", "hls",
                "-hls_time", String.valueOf(hlsTimeSeconds),
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", profileDir.resolve("seg_%05d.ts").toString(),
                "-progress", "pipe:1", "-nostats",
                profileDir.resolve("index.m3u8").toString()));
        run(episodeId, command, durationSeconds, listener);
    }

    /**
     * 生成 master.m3u8（多码率时包含多档；REMUX 只有一档）。
     */
    public void writeMasterPlaylist(Path hlsDir, ProbeResult probe, List<TranscodeProfile> profiles) {
        StringBuilder content = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
        for (TranscodeProfile profile : profiles) {
            int bandwidth = profile.getBandwidth();
            String resolution = profile.getResolution();
            if (profiles.size() == 1 && probe.width() > 0 && probe.height() > 0) {
                // 单档 REMUX 时按源分辨率/码率声明，便于播放器选择
                bandwidth = probe.bitRate() > 0 ? (int) probe.bitRate() : profile.getBandwidth();
                resolution = probe.width() + "x" + probe.height();
            }
            content.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(bandwidth)
                    .append(",RESOLUTION=").append(resolution).append('\n')
                    .append(profile.getDirectoryName()).append("/index.m3u8\n");
        }
        try {
            Files.writeString(hlsDir.resolve("master.m3u8"), content.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(ErrorCode.FFMPEG_FAILED, "写入 master.m3u8 失败");
        }
    }

    /**
     * 截帧生成封面；失败只记录告警，不影响转码结果。
     */
    public boolean extractCover(Path input, Path coverFile, int atSecond) {
        createDirectories(coverFile.getParent());
        List<String> command = List.of(
                ffmpegPath, "-y",
                "-ss", String.valueOf(Math.max(0, atSecond)),
                "-i", input.toString(),
                "-vframes", "1",
                "-q:v", "2",
                // FFmpeg 7 起 image2 输出单帧必须加 -update 1，否则会以 exitCode=1 告警（文件实际已写入）
                "-update", "1",
                coverFile.toString());
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            if (process.exitValue() != 0) {
                log.warn("封面截帧失败（忽略）：exitCode={}", process.exitValue());
                return false;
            }
            return Files.isRegularFile(coverFile);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("封面截帧异常（忽略）：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 终止某剧集正在运行的 FFmpeg 进程（取消转码）。
     */
    public void cancel(Long episodeId) {
        Process process = runningProcesses.get(episodeId);
        if (process != null && process.isAlive()) {
            log.warn("强制终止 FFmpeg 进程：episodeId={}", episodeId);
            process.destroyForcibly();
        }
    }

    public boolean isRunning(Long episodeId) {
        Process process = runningProcesses.get(episodeId);
        return process != null && process.isAlive();
    }

    /**
     * 执行命令并解析进度；非 0 退出码抛 FFMPEG_FAILED。
     */
    private void run(Long episodeId, List<String> command, double durationSeconds, ProgressListener listener) {
        log.info("执行 FFmpeg：episodeId={}, command={}", episodeId, String.join(" ", command));
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            runningProcesses.put(episodeId, process);

            // -progress pipe:1 输出 key=value 行；out_time_us 单位为微秒
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("out_time_us=") && durationSeconds > 0) {
                        long micros = parseLongSafely(line.substring("out_time_us=".length()));
                        double percent = micros / 1_000_000.0 / durationSeconds * 100.0;
                        listener.onProgress(Math.max(0.0, Math.min(99.0, percent)));
                    } else if (line.startsWith("progress=end")) {
                        listener.onProgress(100.0);
                    }
                }
            }

            boolean finished = process.waitFor(6, TimeUnit.HOURS);
            if (!finished) {
                process.destroyForcibly();
                throw new BizException(ErrorCode.FFMPEG_FAILED, "FFmpeg 执行超时");
            }
            if (process.exitValue() != 0) {
                throw new BizException(ErrorCode.FFMPEG_FAILED, "FFmpeg 执行失败，exitCode=" + process.exitValue());
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException(ErrorCode.FFMPEG_FAILED, "无法启动 FFmpeg：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.FFMPEG_FAILED, "FFmpeg 执行被中断");
        } finally {
            if (process != null) {
                runningProcesses.remove(episodeId, process);
            }
        }
    }

    private long parseLongSafely(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "创建 HLS 输出目录失败：" + dir);
        }
    }
}
