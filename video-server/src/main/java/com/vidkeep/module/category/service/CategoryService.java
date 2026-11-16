package com.vidkeep.module.category.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vidkeep.common.BizException;
import com.vidkeep.common.ErrorCode;
import com.vidkeep.module.category.dto.CategoryItemVO;
import com.vidkeep.module.category.dto.CategorySaveRequest;
import com.vidkeep.module.category.entity.Category;
import com.vidkeep.module.category.mapper.CategoryMapper;
import com.vidkeep.module.video.entity.Video;
import com.vidkeep.module.video.mapper.VideoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分类服务：公开列表 + 管理端 CRUD（文档 6.2 分类模块）。
 *
 * <p>删除保护：分类下仍有视频时禁止删除（错误码 3002）。</p>
 */
@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryMapper categoryMapper;
    private final VideoMapper videoMapper;

    public CategoryService(CategoryMapper categoryMapper, VideoMapper videoMapper) {
        this.categoryMapper = categoryMapper;
        this.videoMapper = videoMapper;
    }

    /**
     * 分类列表：按 sort asc、id asc 排序（公开接口与管理端共用）。
     */
    public List<CategoryItemVO> listAll() {
        List<Category> categories = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        return categories.stream()
                .map(category -> new CategoryItemVO(
                        category.getId(),
                        category.getName(),
                        category.getSort(),
                        category.getCreatedAt()))
                .toList();
    }

    /**
     * 新增分类：名称唯一，重名返回 1001。
     *
     * @return 新分类 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(CategorySaveRequest request) {
        String name = request.name().trim();
        checkNameUnique(name, null);
        Category category = new Category();
        category.setName(name);
        category.setSort(request.sort() == null ? 0 : request.sort());
        categoryMapper.insert(category);
        log.info("新增分类：id={}, name={}, sort={}", category.getId(), name, category.getSort());
        return category.getId();
    }

    /**
     * 编辑分类：名称唯一（排除自身），sort 为空表示不修改。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CategorySaveRequest request) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        String name = request.name().trim();
        checkNameUnique(name, id);
        // updateById 默认忽略 null 字段：sort 为 null 时保持原值
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSort(request.sort());
        categoryMapper.updateById(category);
        log.info("更新分类：id={}, name={}", id, name);
    }

    /**
     * 删除分类：分类下仍有视频时返回 3002（文档接口契约）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        Long videoCount = videoMapper.selectCount(Wrappers.<Video>lambdaQuery()
                .eq(Video::getCategoryId, id));
        if (videoCount != null && videoCount > 0) {
            throw new BizException(ErrorCode.STATUS_NOT_ALLOWED, "分类下仍有视频，不能删除");
        }
        categoryMapper.deleteById(id);
        log.info("删除分类：id={}, name={}", id, existing.getName());
    }

    /**
     * 名称唯一校验：重名抛 1001（前端提示"分类名称已存在"）。
     *
     * @param excludeId 编辑时排除自身 ID，新增传 null
     */
    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Category> wrapper = Wrappers.<Category>lambdaQuery()
                .eq(Category::getName, name);
        if (excludeId != null) {
            wrapper.ne(Category::getId, excludeId);
        }
        Long count = categoryMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类名称已存在");
        }
    }
}
