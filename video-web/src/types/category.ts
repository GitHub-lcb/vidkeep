/** 分类（GET /api/public/categories） */
export interface CategoryItem {
  id: number
  name: string
  sort: number
  createdAt?: string
}

/** 新增/编辑分类请求（POST/PUT /api/admin/categories） */
export interface CategorySaveRequest {
  name: string
  sort?: number
}
