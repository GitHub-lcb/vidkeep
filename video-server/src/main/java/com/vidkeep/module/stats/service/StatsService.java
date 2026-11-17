package com.vidkeep.module.stats.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vidkeep.module.category.mapper.CategoryMapper;
import com.vidkeep.module.play.mapper.PlayHistoryMapper;
import com.vidkeep.module.stats.dto.AdminStatsResponse;
import com.vidkeep.module.video.entity.VideoEpisode;
import com.vidkeep.module.video.mapper.VideoEpisodeMapper;
import com.vidkeep.module.video.mapper.VideoMapper;
import org.springframework.stereotype.Service;

/**
 * 后台统计服务（P1 F-15）。
 *
 * <p>播放次数取自 video.view_count 累加；观看时长按播放进度表 duration 汇总
 * （每台设备每集保留最后一次观看时长，属于文档既有模型下的合理口径）。</p>
 */
@Service
public class StatsService {

    private final VideoMapper videoMapper;
    private final CategoryMapper categoryMapper;
    private final VideoEpisodeMapper episodeMapper;
    private final PlayHistoryMapper playHistoryMapper;

    public StatsService(VideoMapper videoMapper,
                        CategoryMapper categoryMapper,
                        VideoEpisodeMapper episodeMapper,
                        PlayHistoryMapper playHistoryMapper) {
        this.videoMapper = videoMapper;
        this.categoryMapper = categoryMapper;
        this.episodeMapper = episodeMapper;
        this.playHistoryMapper = playHistoryMapper;
    }

    public AdminStatsResponse stats() {
        long videoCount = count(videoMapper.selectCount(null));
        long categoryCount = count(categoryMapper.selectCount(null));
        long episodeCount = count(episodeMapper.selectCount(null));
        long transcodingCount = count(episodeMapper.selectCount(Wrappers.<VideoEpisode>lambdaQuery()
                .in(VideoEpisode::getTranscodeStatus, "PENDING", "PROCESSING")));
        long totalViewCount = Math.max(0L, videoMapper.sumViewCount());
        long totalWatchSeconds = Math.max(0L, playHistoryMapper.sumWatchSeconds());
        return new AdminStatsResponse(videoCount, categoryCount, episodeCount, transcodingCount,
                totalViewCount, totalWatchSeconds);
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }
}
