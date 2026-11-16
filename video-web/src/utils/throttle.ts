/**
 * 节流：wait 毫秒内最多执行一次；窗口期内的最后一次调用会在窗口结束时补发，
 * 保证「最后一次进度」不被丢弃（M2 播放进度上报每 15 秒一次会用到）。
 */
export function throttle<Args extends unknown[]>(
  fn: (...args: Args) => void,
  wait = 200,
): (...args: Args) => void {
  let lastInvoke = 0
  let timer: ReturnType<typeof setTimeout> | null = null
  let pendingArgs: Args | null = null

  const invoke = (args: Args): void => {
    lastInvoke = Date.now()
    pendingArgs = null
    fn(...args)
  }

  return (...args: Args): void => {
    pendingArgs = args
    const remaining = wait - (Date.now() - lastInvoke)

    // 已过冷却期：立即执行，并取消待补发的定时器
    if (remaining <= 0) {
      if (timer !== null) {
        clearTimeout(timer)
        timer = null
      }
      invoke(args)
      return
    }

    // 冷却期内：只保留最后一次参数，窗口结束时补发
    if (timer === null) {
      timer = setTimeout(() => {
        timer = null
        const finalArgs = pendingArgs
        if (finalArgs !== null) {
          invoke(finalArgs)
        }
      }, remaining)
    }
  }
}
