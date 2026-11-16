package com.vidkeep.infra.storage;

import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * 本地文件系统存储实现。
 *
 * <p>安全要求：所有路径都必须 normalize 后校验仍位于 DATA_DIR 内，防止 {@code ../} 路径穿越。</p>
 */
@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    private static final String TMP_DIR = "tmp";
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Path baseDir;

    public LocalStorageService(@Value("${app.storage.data-dir:./data}") String dataDir) {
        this.baseDir = Path.of(dataDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建本地存储根目录：" + baseDir, e);
        }
        log.info("本地存储根目录：{}", baseDir);
    }

    @Override
    public Path resolve(String relativeKey) {
        if (relativeKey == null || relativeKey.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "存储路径不能为空");
        }
        Path target = baseDir.resolve(relativeKey).normalize();
        // 防路径穿越：normalize 后必须仍在 DATA_DIR 内
        if (!target.startsWith(baseDir)) {
            log.warn("拦截非法存储路径：key={}, resolved={}", relativeKey, target);
            throw new BizException(ErrorCode.PARAM_INVALID, "非法存储路径");
        }
        return target;
    }

    @Override
    public void ensureDirectory(String relativeDirKey) {
        try {
            Files.createDirectories(resolve(relativeDirKey));
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "创建目录失败：" + relativeDirKey);
        }
    }

    @Override
    public boolean exists(String relativeKey) {
        return Files.exists(resolve(relativeKey));
    }

    @Override
    public long size(String relativeKey) {
        Path path = resolve(relativeKey);
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    @Override
    public void write(String relativeKey, InputStream inputStream) {
        Path target = resolve(relativeKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "写入文件失败：" + relativeKey);
        }
    }

    @Override
    public void writeChunk(String uploadId, int index, InputStream inputStream) {
        Path target = chunkPath(uploadId, index);
        try {
            Files.createDirectories(target.getParent());
            // 分片重复上传覆盖写，幂等
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "写入分片失败：" + uploadId + "/" + index);
        }
    }

    @Override
    public List<Integer> listUploadedChunks(String uploadId) {
        Path dir = resolve(TMP_DIR + "/" + uploadId);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".part"))
                    .map(name -> name.substring(0, name.length() - ".part".length()))
                    .flatMap(name -> {
                        try {
                            return Stream.of(Integer.parseInt(name));
                        } catch (NumberFormatException e) {
                            return Stream.empty();
                        }
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "读取分片列表失败：" + uploadId);
        }
    }

    @Override
    public void cleanChunks(String uploadId) {
        delete(TMP_DIR + "/" + uploadId);
    }

    @Override
    public String mergeChunks(String uploadId, int totalChunks, String targetKey) {
        Path target = resolve(targetKey);
        try {
            Files.createDirectories(target.getParent());
            try (FileChannel out = FileChannel.open(target,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int i = 0; i < totalChunks; i++) {
                    Path part = chunkPath(uploadId, i);
                    if (!Files.isRegularFile(part)) {
                        throw new BizException(ErrorCode.MERGE_FAILED, "分片缺失：" + i);
                    }
                    // 零拷贝合并
                    try (FileChannel in = FileChannel.open(part, StandardOpenOption.READ)) {
                        long position = 0;
                        long partSize = in.size();
                        while (position < partSize) {
                            position += in.transferTo(position, partSize - position, out);
                        }
                    }
                }
            }
            return md5OfFile(target);
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            log.error("分片合并失败：uploadId={}", uploadId, e);
            throw new BizException(ErrorCode.MERGE_FAILED, "文件合并失败");
        }
    }

    @Override
    public String md5(String relativeKey) {
        try {
            return md5OfFile(resolve(relativeKey));
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "计算 MD5 失败：" + relativeKey);
        }
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        Path source = resolve(sourceKey);
        Path target = resolve(targetKey);
        if (!Files.isRegularFile(source)) {
            throw new BizException(ErrorCode.NOT_FOUND, "源文件不存在：" + sourceKey);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "复制文件失败：" + sourceKey);
        }
    }

    @Override
    public void delete(String relativeKey) {
        Path path = resolve(relativeKey);
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException e) {
                    log.warn("删除失败：{}", item, e);
                }
            });
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "删除失败：" + relativeKey);
        }
    }

    @Override
    public long usableSpace() {
        try {
            return Files.getFileStore(baseDir).getUsableSpace();
        } catch (IOException e) {
            log.warn("获取磁盘可用空间失败", e);
            return Long.MAX_VALUE;
        }
    }

    private Path chunkPath(String uploadId, int index) {
        return resolve(TMP_DIR + "/" + uploadId + "/" + index + ".part");
    }

    private String md5OfFile(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 不支持 MD5", e);
        }
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream digestIn = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (digestIn.read(buffer) != -1) {
                // 读完即完成摘要
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
