package com.vidkeep.module.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增/编辑分类请求（POST/PUT /api/admin/categories）。
 *
 * @param name 分类名称，1..64 字符
 * @param sort 排序值，可空；新增时为空按 0 处理
 */
public record CategorySaveRequest(
        @NotBlank(message = "分类名称不能为空")
        @Size(min = 1, max = 64, message = "分类名称长度需为 1..64")
        String name,

        Integer sort
) {
}
