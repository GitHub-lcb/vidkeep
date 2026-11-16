package com.vidkeep.module.category.dto;

import java.time.LocalDateTime;

/**
 * 分类列表项（GET /api/public/categories，字段与前端 src/types/category.ts 的 CategoryItem 对齐）。
 *
 * @param id        分类 ID
 * @param name      分类名称
 * @param sort      排序值
 * @param createdAt 创建时间（由数据库默认值生成）
 */
public record CategoryItemVO(
        Long id,
        String name,
        Integer sort,
        LocalDateTime createdAt
) {
}
