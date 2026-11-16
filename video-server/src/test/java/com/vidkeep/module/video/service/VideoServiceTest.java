package com.vidkeep.module.video.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import com.vidkeep.common.PageResult;
import com.vidkeep.infra.storage.StorageService;
import com.vidkeep.module.category.entity.Category;
import com.vidkeep.module.category.mapper.CategoryMapper;
import com.vidkeep.module.play.mapper.PlayHistoryMapper;
import com.vidkeep.module.transcode.mapper.TranscodeTaskMapper;
import com.vidkeep.module.video.dto.VideoAdminItemVO;
import com.vidkeep.module.video.entity.Video;
import com.vidkeep.module.video.entity.VideoEpisode;
import com.vidkeep.module.video.mapper.VideoEpisodeMapper;
import com.vidkeep.module.video.mapper.VideoMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 视频管理服务单元测试（Mockito，不启动 Spring）：
 * 列表筛选与批量填充、详情不存在、删除级联、上下架参数校验。
 */
@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    @Mock
    private VideoMapper videoMapper;
    @Mock
    private VideoEpisodeMapper videoEpisodeMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private TranscodeTaskMapper transcodeTaskMapper;
    @Mock
    private PlayHistoryMapper playHistoryMapper;
    @Mock
    private StorageService storageService;

    private VideoService videoService;

    /**
     * LambdaQueryWrapper 依赖 MyBatis-Plus 的 TableInfo 缓存把方法引用解析成列名；
     * 纯 Mockito 单测不启动 Spring，这里手动初始化一次（生产环境由 MapperScan 完成）。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Video.class);
        TableInfoHelper.initTableInfo(assistant, VideoEpisode.class);
        TableInfoHelper.initTableInfo(assistant, Category.class);
    }

    @BeforeEach
    void setUp() {
        videoService = new VideoService(videoMapper, videoEpisodeMapper, categoryMapper,
                transcodeTaskMapper, playHistoryMapper, storageService);
    }

    @Test
    void shouldFilterAndBatchFillCategoryNameAndEpisodeCount() {
        Video first = video(1L, "视频A", 10L, 1);
        Video second = video(2L, "视频B", null, 0);
        // 模拟分页插件回填：复用 Service 传入的 Page 对象
        doAnswer(invocation -> {
            Page<Video> page = invocation.getArgument(0);
            page.setRecords(List.of(first, second));
            page.setTotal(2L);
            return page;
        }).when(videoMapper).selectPage(any(), any());

        Category category = new Category();
        category.setId(10L);
        category.setName("电影");
        when(categoryMapper.selectList(any())).thenReturn(List.of(category));
        when(videoEpisodeMapper.selectList(any())).thenReturn(List.of(
                episode(11L, 1L, 1),
                episode(12L, 1L, 2),
                episode(13L, 2L, 1)));

        // page=0/size=999：验证服务端 clamp
        PageResult<VideoAdminItemVO> result = videoService.list(0, 999, "视频", 1, 10L);

        assertEquals(2L, result.getTotal());
        assertEquals(1L, result.getPage());
        assertEquals(200L, result.getSize());
        assertEquals(2, result.getRecords().size());
        VideoAdminItemVO firstVO = result.getRecords().get(0);
        assertEquals("电影", firstVO.categoryName());
        assertEquals(2, firstVO.episodeCount().intValue());
        assertEquals(1, result.getRecords().get(1).episodeCount().intValue());
        assertNull(result.getRecords().get(1).categoryName());

        // 筛选条件全部进入 LambdaQueryWrapper（title LIKE / status = / category_id =）
        ArgumentCaptor<AbstractWrapper<Video, ?, ?>> wrapperCaptor = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(videoMapper).selectPage(any(), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("title"), sqlSegment);
        assertTrue(sqlSegment.contains("status"), sqlSegment);
        assertTrue(sqlSegment.contains("category_id"), sqlSegment);
        // like 的参数值形如 %视频%，这里只校验包含关键词
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().values().stream()
                .anyMatch(value -> String.valueOf(value).contains("视频")));
    }

    @Test
    void shouldThrowNotFoundWhenVideoDetailMissing() {
        when(videoMapper.selectById(999L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> videoService.detail(999L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void shouldCascadeDeleteEpisodeFilesTasksHistoryAndCover() {
        Video video = video(1L, "多集作品", null, 0);
        video.setCoverUrl("/covers/1.jpg");
        when(videoMapper.selectById(1L)).thenReturn(video);

        VideoEpisode firstEpisode = episode(11L, 1L, 1);
        firstEpisode.setStorageKey("uploads/2026/11/a.mp4");
        VideoEpisode secondEpisode = episode(12L, 1L, 2);
        secondEpisode.setStorageKey("uploads/2026/11/b.mp4");
        when(videoEpisodeMapper.selectList(any())).thenReturn(List.of(firstEpisode, secondEpisode));

        videoService.delete(1L);

        // 每集：原片 + HLS 目录
        verify(storageService).delete("uploads/2026/11/a.mp4");
        verify(storageService).delete("hls/11");
        verify(storageService).delete("uploads/2026/11/b.mp4");
        verify(storageService).delete("hls/12");
        // 封面文件
        verify(storageService).delete("covers/1.jpg");
        // 每集一条转码任务；播放进度 = 每集 2 条 + 视频级 1 条
        verify(transcodeTaskMapper, times(2)).delete(any());
        verify(playHistoryMapper, times(3)).delete(any());
        verify(videoMapper).deleteById(1L);
    }

    @Test
    void shouldRejectInvalidStatus() {
        BizException exception = assertThrows(BizException.class,
                () -> videoService.updateStatus(1L, 2));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), exception.getCode());
    }

    private Video video(Long id, String title, Long categoryId, Integer status) {
        Video video = new Video();
        video.setId(id);
        video.setTitle(title);
        video.setCategoryId(categoryId);
        video.setStatus(status);
        video.setViewCount(0L);
        return video;
    }

    private VideoEpisode episode(Long id, Long videoId, int episodeNo) {
        VideoEpisode episode = new VideoEpisode();
        episode.setId(id);
        episode.setVideoId(videoId);
        episode.setEpisodeNo(episodeNo);
        return episode;
    }
}
