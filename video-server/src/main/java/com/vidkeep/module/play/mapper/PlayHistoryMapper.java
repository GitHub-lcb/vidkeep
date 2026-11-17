package com.vidkeep.module.play.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vidkeep.module.play.entity.PlayHistory;
import org.apache.ibatis.annotations.Select;

/**
 * 播放进度 Mapper。
 */
public interface PlayHistoryMapper extends BaseMapper<PlayHistory> {

    /**
     * 全部观看时长汇总（P1 F-15 统计，口径见 StatsService）。
     */
    @Select("SELECT COALESCE(SUM(duration), 0) FROM play_history")
    long sumWatchSeconds();
}
