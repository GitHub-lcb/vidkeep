package com.vidkeep.module.category.service;

import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import com.vidkeep.module.category.dto.CategorySaveRequest;
import com.vidkeep.module.category.entity.Category;
import com.vidkeep.module.category.mapper.CategoryMapper;
import com.vidkeep.module.video.mapper.VideoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分类服务单元测试（Mockito，不启动 Spring）：
 * 重名校验、删除保护、新增默认 sort。
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private VideoMapper videoMapper;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryMapper, videoMapper);
    }

    @Test
    void shouldRejectDuplicateNameWhenCreate() {
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> categoryService.create(new CategorySaveRequest("电影", 1)));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), exception.getCode());
        assertEquals("分类名称已存在", exception.getMessage());
        verify(categoryMapper, never()).insert(any(Category.class));
    }

    @Test
    void shouldCreateCategoryWithDefaultSortAndDbGeneratedTime() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(8L);
            return 1;
        }).when(categoryMapper).insert(any(Category.class));

        Long id = categoryService.create(new CategorySaveRequest("纪录片", null));

        assertEquals(8L, id.longValue());
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryMapper).insert(captor.capture());
        assertEquals("纪录片", captor.getValue().getName());
        assertEquals(0, captor.getValue().getSort().intValue());
        // created_at 由数据库默认值生成，插入时不赋值
        assertNull(captor.getValue().getCreatedAt());
    }

    @Test
    void shouldRejectDeleteWhenCategoryHasVideos() {
        Category category = new Category();
        category.setId(5L);
        category.setName("电影");
        when(categoryMapper.selectById(5L)).thenReturn(category);
        when(videoMapper.selectCount(any())).thenReturn(3L);

        BizException exception = assertThrows(BizException.class, () -> categoryService.delete(5L));

        assertEquals(ErrorCode.STATUS_NOT_ALLOWED.getCode(), exception.getCode());
        assertEquals("分类下仍有视频，不能删除", exception.getMessage());
        verify(categoryMapper, never()).deleteById(5L);
    }
}
