package com.vidkeep.module.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vidkeep.module.video.entity.Video;
import org.apache.ibatis.annotations.Select;

/**
 * 视频作品 Mapper（单表 CRUD 由 MyBatis-Plus 提供）。
 */
public interface VideoMapper extends BaseMapper<Video> {

    /**
     * 全部作品播放次数汇总（P1 F-15 统计）。
     */
    @Select("SELECT COALESCE(SUM(view_count), 0) FROM video")
    long sumViewCount();
}
