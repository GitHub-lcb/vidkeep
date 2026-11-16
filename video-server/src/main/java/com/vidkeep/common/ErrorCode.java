package com.vidkeep.common;

/**
 * 业务错误码（与 docs/01-技术选型与开发文档.md 6.1 节错误码表保持一致）。
 *
 * <p>0 表示成功；其余为业务错误码，HTTP 状态码仍返回 200。</p>
 */
public enum ErrorCode {

    SUCCESS(0, "ok"),

    PARAM_INVALID(1001, "参数校验失败"),
    PAYLOAD_TOO_LARGE(1002, "请求体过大"),

    UNAUTHORIZED(2001, "未登录或登录已过期"),
    LOGIN_FAILED(2002, "账号或密码错误"),
    FORBIDDEN(2003, "无权限"),

    NOT_FOUND(3001, "资源不存在"),
    STATUS_NOT_ALLOWED(3002, "资源状态不允许该操作"),

    UPLOAD_SESSION_NOT_FOUND(4001, "上传会话不存在"),
    CHUNK_INDEX_OUT_OF_RANGE(4002, "分片序号越界"),
    MERGE_FAILED(4003, "文件合并失败或 MD5 不匹配"),
    DISK_FULL(4004, "磁盘空间不足"),

    TRANSCODE_TASK_FAILED(5001, "转码任务创建失败"),
    FFMPEG_FAILED(5002, "FFmpeg 执行失败"),

    SYSTEM_ERROR(9001, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
