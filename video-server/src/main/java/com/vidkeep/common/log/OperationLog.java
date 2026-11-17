package com.vidkeep.common.log;

/**
 * 管理员操作日志注解占位。
 *
 * <p>当前 P1 采用「管理端写操作自动审计」策略（见 {@link OperationLogAspect}），
 * 该注解保留给后续需要自定义 module/action 描述的方法使用。</p>
 */
public @interface OperationLog {

    /** 模块名，如「视频」 */
    String module() default "";

    /** 动作名，如「删除视频」 */
    String action() default "";
}
