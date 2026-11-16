import http from './request'
import type { CategoryItem, CategorySaveRequest } from '@/types/category'

/**
 * 分类列表：GET /api/public/categories。
 * 后台与前台共用该公开接口，返回结果已按 sort 升序（文档 6.2 分类模块）。
 */
export function listCategories(): Promise<CategoryItem[]> {
  return http.get<CategoryItem[]>('/public/categories')
}

/** 新增分类：POST /api/admin/categories，成功返回新分类 id */
export function createCategory(payload: CategorySaveRequest): Promise<number> {
  return http.post<number>('/admin/categories', payload)
}

/** 编辑分类：PUT /api/admin/categories/{id} */
export function updateCategory(id: number, payload: CategorySaveRequest): Promise<null> {
  return http.put<null>(`/admin/categories/${id}`, payload)
}

/** 删除分类：DELETE /api/admin/categories/{id}（分类下存在视频时后端返回 3002） */
export function deleteCategory(id: number): Promise<null> {
  return http.delete<null>(`/admin/categories/${id}`)
}
