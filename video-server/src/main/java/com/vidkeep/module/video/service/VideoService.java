package com.vidkeep.module.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import com.vidkeep.common.PageResult;
import com.vidkeep.infra.storage.StorageService;
import com.vidkeep.module.category.entity.Category;
import com.vidkeep.module.category.mapper.CategoryMapper;
import com.vidkeep.module.play.entity.PlayHistory;
import com.vidkeep.module.play.mapper.PlayHistoryMapper;
import com.vidkeep.module.transcode.entity.TranscodeTask;
import com.vidkeep.module.transcode.mapper.TranscodeTaskMapper;
import com.vidkeep.module.video.dto.AdminEpisodeVO;
import com.vidkeep.module.video.dto.VideoAdminDetailVO;
import com.vidkeep.module.video.dto.VideoAdminItemVO;
import com.vidkeep.module.video.dto.VideoSaveRequest;
import com.vidkeep.module.video.entity.Video;
import com.vidkeep.module.video.entity.VideoEpisode;
import com.vidkeep.module.video.mapper.VideoEpisodeMapper;
import com.vidkeep.module.video.mapper.VideoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 视频管理服务（文档 6.2 视频管理模块，全部为 ADMIN 接口）。
 *
 * <p>N+1 处理：列表的 categoryName 用一次 selectList 批量查分类；
 * episodeCount 用一次 selectList(episode where videoId in ids) 在 Java 内分组统计，禁止循环查库。</p>
 */
@Service
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    /** 单页上限（与 MyBatis-Plus 分页插件 maxLimit 保持一致） */
    private static final long MAX_PAGE_SIZE = 200L;

    private final VideoMapper videoMapper;
    private final VideoEpisodeMapper videoEpisodeMapper;
    private final CategoryMapper categoryMapper;
    private final TranscodeTaskMapper transcodeTaskMapper;
    private final PlayHistoryMapper playHistoryMapper;
    private final StorageService storageService;

    public VideoService(VideoMapper videoMapper,
                        VideoEpisodeMapper videoEpisodeMapper,
                        CategoryMapper categoryMapper,
                        TranscodeTaskMapper transcodeTaskMapper,
                        PlayHistoryMapper playHistoryMapper,
                        StorageService storageService) {
        this.videoMapper = videoMapper;
        this.videoEpisodeMapper = videoEpisodeMapper;
        this.categoryMapper = categoryMapper;
        this.transcodeTaskMapper = transcodeTaskMapper;
        this.playHistoryMapper = playHistoryMapper;
        this.storageService = storageService;
    }

    /**
     * 后台视频分页列表：title 模糊、status/categoryId 精确，按 created_at desc。
     *
     * @param page       页码，服务端 clamp 到 ≥1
     * @param size       每页条数，服务端 clamp 到 1..200
     * @param title      标题关键字，可空
     * @param status     状态，可空
     * @param categoryId 分类 ID，可空
     */
    public PageResult<VideoAdminItemVO> list(long page, long size, String title,
                                             Integer status, Long categoryId) {
        long current = Math.max(page, 1L);
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Video> wrapper = Wrappers.<Video>lambdaQuery();
        if (StringUtils.hasText(title)) {
            wrapper.like(Video::getTitle, title.trim());
        }
        if (status != null) {
            wrapper.eq(Video::getStatus, status);
        }
        if (categoryId != null) {
            wrapper.eq(Video::getCategoryId, categoryId);
        }
        wrapper.orderByDesc(Video::getCreatedAt);

        Page<Video> videoPage = videoMapper.selectPage(new Page<>(current, pageSize), wrapper);
        List<Video> videos = videoPage.getRecords();

        Map<Long, String> categoryNames = queryCategoryNames(
                videos.stream().map(Video::getCategoryId).filter(id -> id != null).distinct().toList());
        Map<Long, Integer> episodeCounts = queryEpisodeCounts(
                videos.stream().map(Video::getId).filter(id -> id != null).toList());

        List<VideoAdminItemVO> records = videos.stream()
                .map(video -> new VideoAdminItemVO(
                        video.getId(),
                        video.getTitle(),
                        video.getDescription(),
                        video.getCoverUrl(),
                        video.getCategoryId(),
                        video.getCategoryId() == null ? null : categoryNames.get(video.getCategoryId()),
                        video.getViewCount() == null ? 0L : video.getViewCount(),
                        video.getStatus() == null ? 0 : video.getStatus(),
                        episodeCounts.getOrDefault(video.getId(), 0),
                        video.getCreatedAt(),
                        video.getUpdatedAt()))
                .toList();

        Page<VideoAdminItemVO> voPage = new Page<>(videoPage.getCurrent(), videoPage.getSize());
        voPage.setTotal(videoPage.getTotal());
        voPage.setRecords(records);
        return PageResult.of(voPage);
    }

    /**
     * 后台视频详情：含按 episodeNo 升序的剧集列表；不存在返回 3001。
     */
    public VideoAdminDetailVO detail(Long id) {
        Video video = videoMapper.selectById(id);
        if (video == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "视频不存在");
        }
        List<VideoEpisode> episodes = videoEpisodeMapper.selectList(Wrappers.<VideoEpisode>lambdaQuery()
                .eq(VideoEpisode::getVideoId, id)
                .orderByAsc(VideoEpisode::getEpisodeNo));
        Map<Long, String> categoryNames = queryCategoryNames(
                video.getCategoryId() == null ? List.of() : List.of(video.getCategoryId()));
        List<AdminEpisodeVO> episodeVOs = episodes.stream().map(this::toAdminEpisodeVO).toList();
        return new VideoAdminDetailVO(
                video.getId(),
                video.getTitle(),
                video.getDescription(),
                video.getCoverUrl(),
                video.getCategoryId(),
                video.getCategoryId() == null ? null : categoryNames.get(video.getCategoryId()),
                video.getViewCount() == null ? 0L : video.getViewCount(),
                video.getStatus() == null ? 0 : video.getStatus(),
                episodeVOs.size(),
                video.getCreatedAt(),
                video.getUpdatedAt(),
                episodeVOs);
    }

    /**
     * 新建作品（先建壳，再上传文件；文档 7.4.2 两步式）。
     *
     * <p>默认上线（status=1，与文档 5.3 建表默认值一致）；如需先下线待转码完成再
     * 通过上下架接口发布，可调用 PUT /api/admin/videos/{id}/status 改为 0。</p>
     *
     * @return 新视频 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(VideoSaveRequest request) {
        validateCategoryExists(request.categoryId());
        Video video = new Video();
        video.setTitle(request.title().trim());
        video.setDescription(request.description());
        video.setCoverUrl(request.coverUrl());
        video.setCategoryId(request.categoryId());
        video.setViewCount(0L);
        video.setStatus(1);
        videoMapper.insert(video);
        log.info("新增视频作品：id={}, title={}", video.getId(), video.getTitle());
        return video.getId();
    }

    /**
     * 编辑作品：标题/简介/封面/分类（null 字段按 MyBatis-Plus 默认策略不更新）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, VideoSaveRequest request) {
        Video existing = videoMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "视频不存在");
        }
        validateCategoryExists(request.categoryId());
        Video update = new Video();
        update.setId(id);
        update.setTitle(request.title().trim());
        update.setDescription(request.description());
        update.setCoverUrl(request.coverUrl());
        update.setCategoryId(request.categoryId());
        videoMapper.updateById(update);
        log.info("更新视频作品：id={}, title={}", id, update.getTitle());
    }

    /**
     * 删除作品并级联清理：
     * <ol>
     *   <li>每集：删原片文件、HLS 目录、transcode_task、play_history(episodeId)、episode 行</li>
     *   <li>删 video</li>
     *   <li>删 play_history(videoId)</li>
     *   <li>删封面 covers/{videoId}.jpg（存在 coverUrl 才尝试）</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Video video = videoMapper.selectById(id);
        if (video == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "视频不存在");
        }
        List<VideoEpisode> episodes = videoEpisodeMapper.selectList(Wrappers.<VideoEpisode>lambdaQuery()
                .eq(VideoEpisode::getVideoId, id));
        for (VideoEpisode episode : episodes) {
            deleteEpisodeArtifacts(episode);
            // 注意：删除剧集行，否则会留下指向已删作品的孤儿 episode（秒传/统计会被污染）
            videoEpisodeMapper.deleteById(episode.getId());
        }
        videoMapper.deleteById(id);
        playHistoryMapper.delete(Wrappers.<PlayHistory>lambdaQuery()
                .eq(PlayHistory::getVideoId, id));
        if (StringUtils.hasText(video.getCoverUrl())) {
            storageService.delete("covers/" + id + ".jpg");
            log.info("已删除视频封面文件：videoId={}", id);
        }
        log.info("已删除视频作品：id={}, title={}, 级联删除剧集 {} 集", id, video.getTitle(), episodes.size());
    }

    /**
     * 上下架：status 只允许 0/1（DTO 已校验，服务层再兜底）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态只允许 0 或 1");
        }
        Video existing = videoMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "视频不存在");
        }
        Video update = new Video();
        update.setId(id);
        update.setStatus(status);
        videoMapper.updateById(update);
        log.info("视频上下架：id={}, status={}", id, status);
    }

    /**
     * 删除单集：删原片、HLS 目录、转码任务、播放进度，再删 episode。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteEpisode(Long episodeId) {
        VideoEpisode episode = videoEpisodeMapper.selectById(episodeId);
        if (episode == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "剧集不存在");
        }
        deleteEpisodeArtifacts(episode);
        videoEpisodeMapper.deleteById(episodeId);
        log.info("已删除剧集：episodeId={}, videoId={}", episodeId, episode.getVideoId());
    }

    /**
     * 删除单集的全部产物与关联数据（原片、HLS 目录、转码任务、播放进度）。
     */
    private void deleteEpisodeArtifacts(VideoEpisode episode) {
        Long episodeId = episode.getId();
        if (StringUtils.hasText(episode.getStorageKey())) {
            storageService.delete(episode.getStorageKey());
            log.info("已删除原片文件：episodeId={}, storageKey={}", episodeId, episode.getStorageKey());
        }
        storageService.delete("hls/" + episodeId);
        log.info("已删除 HLS 目录：episodeId={}", episodeId);
        transcodeTaskMapper.delete(Wrappers.<TranscodeTask>lambdaQuery()
                .eq(TranscodeTask::getEpisodeId, episodeId));
        playHistoryMapper.delete(Wrappers.<PlayHistory>lambdaQuery()
                .eq(PlayHistory::getEpisodeId, episodeId));
        log.info("已删除剧集关联的转码任务与播放进度：episodeId={}", episodeId);
    }

    /**
     * 分类存在性校验：传了 categoryId 但分类不存在时返回 1001。
     */
    private void validateCategoryExists(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类不存在");
        }
    }

    /**
     * 批量查询分类名称（一次 SQL，避免 N+1）。
     */
    private Map<Long, String> queryCategoryNames(List<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        List<Category> categories = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .in(Category::getId, categoryIds));
        return categories.stream()
                .filter(category -> category.getId() != null && category.getName() != null)
                .collect(Collectors.toMap(Category::getId, Category::getName, (first, second) -> first));
    }

    /**
     * 批量统计每个视频的剧集数（一次 SQL + Java 分组，避免 N+1）。
     */
    private Map<Long, Integer> queryEpisodeCounts(List<Long> videoIds) {
        if (videoIds.isEmpty()) {
            return Map.of();
        }
        List<VideoEpisode> episodes = videoEpisodeMapper.selectList(Wrappers.<VideoEpisode>lambdaQuery()
                .in(VideoEpisode::getVideoId, videoIds));
        return episodes.stream()
                .filter(episode -> episode.getVideoId() != null)
                .collect(Collectors.groupingBy(VideoEpisode::getVideoId, Collectors.summingInt(episode -> 1)));
    }

    private AdminEpisodeVO toAdminEpisodeVO(VideoEpisode episode) {
        return new AdminEpisodeVO(
                episode.getId(),
                episode.getVideoId(),
                episode.getEpisodeNo(),
                episode.getTitle(),
                episode.getPlayType(),
                episode.getDuration(),
                episode.getFileSize(),
                episode.getWidth(),
                episode.getHeight(),
                episode.getBitRate(),
                episode.getTranscodeStatus(),
                episode.getTranscodeMode(),
                episode.getHlsMasterUrl(),
                episode.getCreatedAt());
    }
}
