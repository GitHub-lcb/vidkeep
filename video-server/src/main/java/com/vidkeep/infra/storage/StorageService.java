package com.vidkeep.infra.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * 存储抽象：M1 只实现本地文件系统，后续如需对象存储（S3/OSS）实现同一接口即可切换。
 *
 * <p>所有方法接收的路径均为「相对 DATA_DIR 的 key」，例如：
 * {@code uploads/2026/11/uuid.mp4}、{@code hls/12/master.m3u8}、{@code tmp/uploadId/0.part}。</p>
 */
public interface StorageService {

    /**
     * 解析相对 key 为绝对路径，并校验路径穿越（必须位于 DATA_DIR 内）。
     *
     * @throws com.vidkeep.common.BizException 路径非法时抛出
     */
    Path resolve(String relativeKey);

    /**
     * 确保目录存在（含父目录）。
     */
    void ensureDirectory(String relativeDirKey);

    /**
     * 文件或目录是否存在。
     */
    boolean exists(String relativeKey);

    /**
     * 文件大小（字节）；不存在或为目录时返回 0。
     */
    long size(String relativeKey);

    /**
     * 全量写入文件（覆盖），用于小文件。
     */
    void write(String relativeKey, InputStream inputStream);

    /**
     * 写入上传分片（覆盖写，幂等）。
     */
    void writeChunk(String uploadId, int index, InputStream inputStream);

    /**
     * 列出已上传的分片序号（升序）。
     */
    List<Integer> listUploadedChunks(String uploadId);

    /**
     * 删除上传临时分片目录。
     */
    void cleanChunks(String uploadId);

    /**
     * 按序号升序零拷贝合并分片到目标文件，并返回合并后文件的 MD5（32 位小写）。
     *
     * @param uploadId     上传会话 ID
     * @param totalChunks  分片总数
     * @param targetKey    合并后的相对路径
     */
    String mergeChunks(String uploadId, int totalChunks, String targetKey);

    /**
     * 计算文件 MD5（32 位小写）。
     */
    String md5(String relativeKey);

    /**
     * 拷贝文件到目标路径（覆盖）。
     */
    void copy(String sourceKey, String targetKey);

    /**
     * 删除文件或目录（目录递归删除；不存在不报错）。
     */
    void delete(String relativeKey);

    /**
     * DATA_DIR 所在磁盘的可用空间（字节）。
     */
    long usableSpace();
}
