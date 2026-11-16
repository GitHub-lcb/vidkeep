import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ERROR_CODE, type Result } from '@/types/api'
import { clearAuthStorage, readToken } from '@/utils/storage'

/** 未登录 / Token 失效的业务码（文档 6.1 错误码表） */
export const UNAUTHORIZED_CODE = ERROR_CODE.UNAUTHORIZED

/** 后台登录页地址。这里用 window.location 跳转，避免 request ←→ router 循环依赖 */
export const LOGIN_PATH = '/admin/login'

/**
 * 业务错误：后端返回 code !== 0 时抛出，携带业务码与后端 message。
 * 调用方通过 isBizError 收窄后展示 message。
 */
export class BizError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'BizError'
    this.code = code
  }
}

export function isBizError(value: unknown): value is BizError {
  return value instanceof BizError
}

/** 运行时判断是否为统一响应体（后续下载类接口可能返回裸二进制） */
function isResult(value: unknown): value is Result<unknown> {
  if (typeof value !== 'object' || value === null) return false
  const record = value as Record<string, unknown>
  return typeof record.code === 'number' && typeof record.message === 'string'
}

/** 并发请求同时收到 2001 时，只跳转一次登录页 */
let redirecting = false

function redirectToLogin(): void {
  // 先清本地登录态：Token 已失效，留着只会让守卫误判
  clearAuthStorage()
  if (redirecting) return
  const { pathname, search, hash } = window.location
  if (pathname.startsWith(LOGIN_PATH)) return
  redirecting = true
  const redirect = encodeURIComponent(`${pathname}${search}${hash}`)
  window.location.replace(`${LOGIN_PATH}?redirect=${redirect}`)
}

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 20_000,
})

// 请求拦截器：从 localStorage 取 Token 注入 Authorization
service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = readToken()
  if (token.length > 0) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：解包 Result —— 成功返回 data，失败抛 BizError
//
// 说明：axios 1.x 的响应拦截器签名要求返回 AxiosResponse，而本项目约定拦截器直接返回解开后的
// 业务 data（调用方通过下方 request<T> 的泛型声明真实类型）。这里的 as AxiosResponse 只是
// 向类型系统表达该运行时约定，截断在 http 封装内部，不会泄漏到业务代码。
service.interceptors.response.use(
  (response) => {
    const payload: unknown = response.data
    // 非统一响应体直接放行，交给调用方按需处理
    if (!isResult(payload)) return payload as AxiosResponse
    if (payload.code === ERROR_CODE.SUCCESS) return payload.data as AxiosResponse
    if (payload.code === UNAUTHORIZED_CODE) redirectToLogin()
    return Promise.reject(
      new BizError(payload.code, payload.message.length > 0 ? payload.message : '请求失败'),
    )
  },
  (error: unknown) => {
    // 框架级错误（404/500）也可能带统一响应体，优先取业务码与 message
    if (error instanceof AxiosError) {
      const payload: unknown = error.response?.data
      if (isResult(payload)) {
        if (payload.code === UNAUTHORIZED_CODE) redirectToLogin()
        return Promise.reject(
          new BizError(payload.code, payload.message.length > 0 ? payload.message : error.message),
        )
      }
      if (error.code === 'ECONNABORTED') {
        return Promise.reject(new BizError(-1, '请求超时，请稍后重试'))
      }
      const status = error.response?.status
      return Promise.reject(
        new BizError(
          status ?? -1,
          status === undefined ? '网络异常，请检查网络连接' : `请求失败（HTTP ${status}）`,
        ),
      )
    }
    return Promise.reject(new BizError(-1, '请求失败，请稍后重试'))
  },
)

/**
 * 泛型请求封装：拦截器已解包，建议把 T 声明为「后端 data 的类型」。
 * 例：http.get<UserInfo>('/auth/me') 实际拿到的是 Result.data。
 */
function request<T>(config: AxiosRequestConfig): Promise<T> {
  return service.request<unknown, T>(config)
}

export const http = {
  get<T>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'GET', params })
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'POST', data })
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'PUT', data })
  },
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'PATCH', data })
  },
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, url, method: 'DELETE' })
  },
}

export default http
