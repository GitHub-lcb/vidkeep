package com.vidkeep.module.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import com.vidkeep.common.PageResult;
import com.vidkeep.module.category.entity.Category;
import com.vidkeep.module.category.mapper.CategoryMapper;
import com.vidkeep.module.video.dto.PublicEpisodeVO;
import com.vidkeep.module.video.dto.PublicVideoDetailVO;
import com.vidkeep.module.video.dto.PublicVideoItemVO;
import com.vidkeep.module.video.entity.Video;
import com.vidkeep.module.video.entity.VideoEpisode;
import com.vidkeep.module.video.mapper.VideoEpisodeMapper;
import com.vidkeep.module.video.mapper.VideoMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 前台公开视频服务（文档 6.2 播放模块，免鉴权）。
 *
 * <p>只暴露 status=1 的作品；剧集只返回播放所需字段。列表的 episodeCount/duration
 * 通过一次 selectList(episode where videoId in ids) 批量完成，禁止循环查库。</p>
 */
@Service
public class PublicVideoService {

    /** 单页上限（与 MyBatis-Plus 分页插件 maxLimit 保持一致） */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 上线状态 */
    private static final int STATUS_ONLINE = 1;

    private final VideoMapper videoMapper;
    private final VideoEpisodeMapper videoEpisodeMapper;
    private final CategoryMapper categoryMapper;

    public PublicVideoService(VideoMapper videoMapper,
                              VideoEpisodeMapper videoEpisodeMapper,
                              CategoryMapper categoryMapper) {
        this.videoMapper = videoMapper;
        this.videoEpisodeMapper = videoEpisodeMapper;
        this.categoryMapper = categoryMapper;
    }

    /**
     * 前台视频分页列表：只返回 status=1，keyword 模糊（title）、categoryId 精确，按 created_at desc。
     *
     * @param page       页码，服务端 clamp 到 ≥1
     * @param size       每页条数，服务端 clamp 到 1..200
     * @param keyword    标题关键字，可空
     * @param categoryId 分类 ID，可空
     */
    public PageResult<PublicVideoItemVO> list(long page, long size, String keyword, Long categoryId) {
        long current = Math.max(page, 1L);
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Video> wrapper = Wrappers.<Video>lambdaQuery()
                .eq(Video::getStatus, STATUS_ONLINE);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Video::getTitle, keyword.trim());
        }
        if (categoryId != null) {
            wrapper.eq(Video::getCategoryId, categoryId);
        }
        wrapper.orderByDesc(Video::getCreatedAt);

        Page<Video> videoPage = videoMapper.selectPage(new Page<>(current, pageSize), wrapper);
        List<Video> videos = videoPage.getRecords();

        Map<Long, String> categoryNames = queryCategoryNames(
                videos.stream().map(Video::getCategoryId).filter(id -> id != null).distinct().toList());
        Map<Long, List<VideoEpisode>> episodesByVideo = queryEpisodesGrouped(
                videos.stream().map(Video::getId).filter(id -> id != null).toList());

        List<PublicVideoItemVO> records = videos.stream()
                .map(video -> {
                    List<VideoEpisode> episodes = episodesByVideo.getOrDefault(video.getId(), List.of());
                    // 已按 episodeNo 升序，首集即"第一集"
                    Integer duration = episodes.isEmpty() ? null : episodes.get(0).getDuration();
                    return new PublicVideoItemVO(
                            video.getId(),
                            video.getTitle(),
                            video.getCoverUrl(),
                            video.getCategoryId(),
                            video.getCategoryId() == null ? null : categoryNames.get(video.getCategoryId()),
                            video.getViewCount() == null ? 0L : video.getViewCount(),
                            episodes.size(),
                            duration,
                            video.getCreatedAt());
                })
                .toList();

        Page<PublicVideoItemVO> voPage = new Page<>(videoPage.getCurrent(), videoPage.getSize());
        voPage.setTotal(videoPage.getTotal());
        voPage.setRecords(records);
        return PageResult.of(voPage);
    }

    /**
     * 前台视频详情：仅上线作品可访问，否则返回 3001；剧集按 episodeNo 升序（所有转码状态都返回）。
     */
    public PublicVideoDetailVO detail(Long id) {
        Video video = videoMapper.selectById(id);
        if (video == null || !Integer.valueOf(STATUS_ONLINE).equals(video.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "视频不存在或未上线");
        }
        List<VideoEpisode> episodes = videoEpisodeMapper.selectList(Wrappers.<VideoEpisode>lambdaQuery()
                .eq(VideoEpisode::getVideoId, id)
                .orderByAsc(VideoEpisode::getEpisodeNo));
        Map<Long, String> categoryNames = queryCategoryNames(
                video.getCategoryId() == null ? List.of() : List.of(video.getCategoryId()));
        List<PublicEpisodeVO> episodeVOs = episodes.stream()
                .map(episode -> new PublicEpisodeVO(
                        episode.getId(),
                        episode.getEpisodeNo(),
                        episode.getTitle(),
                        episode.getDuration(),
                        episode.getTranscodeStatus()))
                .toList();
        return new PublicVideoDetailVO(
                video.getId(),
                video.getTitle(),
                video.getDescription(),
                video.getCoverUrl(),
                video.getCategoryId(),
                video.getCategoryId() == null ? null : categoryNames.get(video.getCategoryId()),
                video.getViewCount() == null ? 0L : video.getViewCount(),
                video.getCreatedAt(),
                episodeVOs);
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
     * 批量查询剧集并按 videoId 分组（一次 SQL + Java 分组，避免 N+1）。
     *
     * <p>按 episodeNo 升序查询，分组后每组首元素即该视频第一集。</p>
     */
    private Map<Long, List<VideoEpisode>> queryEpisodesGrouped(List<Long> videoIds) {
        if (videoIds.isEmpty()) {
            return Map.of();
        }
        List<VideoEpisode> episodes = videoEpisodeMapper.selectList(Wrappers.<VideoEpisode>lambdaQuery()
                .in(VideoEpisode::getVideoId, videoIds)
                .orderByAsc(VideoEpisode::getEpisodeNo));
        return episodes.stream()
                .filter(episode -> episode.getVideoId() != null)
                .collect(Collectors.groupingBy(VideoEpisode::getVideoId));
    }
}
