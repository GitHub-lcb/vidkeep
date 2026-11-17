-- ============================================================
-- vidkeep 数据库初始化脚本
-- 与 docs/01-技术选型与开发文档.md 第 5.3 节保持一致
-- ============================================================

CREATE DATABASE IF NOT EXISTS `vidkeep`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `vidkeep`;

SET NAMES utf8mb4;

-- ----------------------------
-- 1. 用户表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `user` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username`    VARCHAR(64)     NOT NULL COMMENT '登录名',
  `password`    VARCHAR(128)    NOT NULL COMMENT 'BCrypt 哈希',
  `nickname`    VARCHAR(64)     DEFAULT NULL,
  `avatar`      VARCHAR(512)    DEFAULT NULL,
  `role`        VARCHAR(16)     NOT NULL DEFAULT 'USER' COMMENT 'ADMIN / USER',
  `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- ----------------------------
-- 2. 分类表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `category` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(64)     NOT NULL,
  `sort`        INT             NOT NULL DEFAULT 0,
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类';

-- ----------------------------
-- 3. 视频作品表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `video` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title`         VARCHAR(255)    NOT NULL,
  `description`   TEXT,
  `cover_url`     VARCHAR(512)    DEFAULT NULL,
  `category_id`   BIGINT UNSIGNED DEFAULT NULL,
  `view_count`    BIGINT          NOT NULL DEFAULT 0,
  `status`        TINYINT         NOT NULL DEFAULT 1 COMMENT '1上线 0下线',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_created` (`status`, `created_at`),
  KEY `idx_category` (`category_id`),
  KEY `idx_title` (`title`(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频作品';

-- ----------------------------
-- 4. 播放单元（剧集）表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `video_episode` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `video_id`         BIGINT UNSIGNED NOT NULL,
  `episode_no`       INT             NOT NULL DEFAULT 1 COMMENT '第几集，单文件作品固定为1',
  `title`            VARCHAR(255)    DEFAULT NULL,
  `play_type`        VARCHAR(16)     NOT NULL DEFAULT 'HLS_LOCAL'
                     COMMENT '预留：HLS_LOCAL 本地转码',
  `duration`         INT             DEFAULT NULL COMMENT '秒',
  `storage_key`      VARCHAR(512)    DEFAULT NULL COMMENT '原片相对路径',
  `file_size`        BIGINT          DEFAULT NULL COMMENT '字节',
  `file_fingerprint` CHAR(64)        DEFAULT NULL COMMENT '抽样指纹，秒传用',
  `file_md5`         CHAR(32)        DEFAULT NULL COMMENT '完整 MD5，合并后校验',
  `width`            INT             DEFAULT NULL,
  `height`           INT             DEFAULT NULL,
  `bit_rate`         INT             DEFAULT NULL COMMENT 'bps',
  `transcode_status` VARCHAR(16)     NOT NULL DEFAULT 'NONE'
                     COMMENT 'NONE/PENDING/PROCESSING/DONE/FAILED',
  `transcode_mode`   VARCHAR(16)     DEFAULT NULL COMMENT 'REMUX/TRANSCODE',
  `hls_master_url`   VARCHAR(512)    DEFAULT NULL COMMENT 'master.m3u8 相对路径',
  `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_video_ep` (`video_id`, `episode_no`),
  KEY `idx_status` (`transcode_status`),
  KEY `idx_fingerprint` (`file_fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='播放单元';

-- ----------------------------
-- 5. 转码任务表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `transcode_task` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `episode_id`  BIGINT UNSIGNED NOT NULL,
  `status`      VARCHAR(16)     NOT NULL DEFAULT 'PENDING'
                COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
  `mode`        VARCHAR(16)     DEFAULT NULL COMMENT 'REMUX/TRANSCODE',
  `progress`    TINYINT         NOT NULL DEFAULT 0 COMMENT '0-100',
  `retry_count` TINYINT         NOT NULL DEFAULT 0,
  `error_msg`   VARCHAR(1024)   DEFAULT NULL,
  `started_at`  DATETIME        DEFAULT NULL,
  `finished_at` DATETIME        DEFAULT NULL,
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_episode` (`episode_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转码任务';

-- ----------------------------
-- 6. 播放进度表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `play_history` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `device_id`   VARCHAR(64)     NOT NULL COMMENT '匿名设备标识（存 localStorage）',
  `video_id`    BIGINT UNSIGNED NOT NULL,
  `episode_id`  BIGINT UNSIGNED NOT NULL,
  `position`    INT             NOT NULL DEFAULT 0 COMMENT '秒',
  `duration`    INT             DEFAULT NULL,
  `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_ep` (`device_id`, `episode_id`),
  KEY `idx_video` (`video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='播放进度';

-- ----------------------------
-- 7. 管理员操作日志表（P1 F-19）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `admin_operation_log` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT UNSIGNED DEFAULT NULL,
  `username`    VARCHAR(64)     DEFAULT NULL,
  `module`      VARCHAR(32)     NOT NULL COMMENT '模块：视频/分类/上传/转码等',
  `action`      VARCHAR(64)     NOT NULL COMMENT '动作：新增视频/删除视频等',
  `method`      VARCHAR(8)      NOT NULL COMMENT 'HTTP 方法',
  `path`        VARCHAR(255)    NOT NULL COMMENT '请求路径',
  `detail`      VARCHAR(1024)   DEFAULT NULL COMMENT '请求参数摘要',
  `ip`          VARCHAR(64)     DEFAULT NULL,
  `duration_ms` INT             DEFAULT NULL COMMENT '处理耗时（毫秒）',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_created` (`created_at`),
  KEY `idx_user` (`user_id`),
  KEY `idx_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作日志';

-- ----------------------------
-- 初始数据
-- ----------------------------
-- 默认管理员：admin / admin123（首次登录后请立即修改）
-- 密码为 BCrypt 哈希（cost=10），已校验对应明文 admin123
INSERT INTO `user` (`username`, `password`, `nickname`, `role`)
SELECT 'admin', '$2a$10$/fDcFJSFMLZsGuWd0XLSnODHrWe0FSrluTn5cU2rRuYmp7S6kgsY6', '管理员', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'admin');

INSERT INTO `category` (`name`, `sort`)
SELECT '默认分类', 0
WHERE NOT EXISTS (SELECT 1 FROM `category` WHERE `name` = '默认分类');
