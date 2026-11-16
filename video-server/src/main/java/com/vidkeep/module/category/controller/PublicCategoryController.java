package com.vidkeep.module.category.controller;

import com.vidkeep.common.Result;
import com.vidkeep.module.category.dto.CategoryItemVO;
import com.vidkeep.module.category.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类公开接口（文档 6.2 分类模块）：免鉴权。
 */
@RestController
@RequestMapping("/api/public/categories")
public class PublicCategoryController {

    private final CategoryService categoryService;

    public PublicCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 分类列表：GET /api/public/categories，按 sort asc、id asc。
     */
    @GetMapping
    public Result<List<CategoryItemVO>> list() {
        return Result.ok(categoryService.listAll());
    }
}
