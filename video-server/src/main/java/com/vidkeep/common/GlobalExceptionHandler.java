package com.vidkeep.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理：把异常统一收敛为 Result，避免堆栈直接暴露给前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常：code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, firstFieldError(e.getBindingResult().getFieldError()));
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, firstFieldError(e.getBindingResult().getFieldError()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .orElse(ErrorCode.PARAM_INVALID.getMessage());
        log.warn("参数校验失败：{}", message);
        return Result.fail(ErrorCode.PARAM_INVALID, message);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public Result<Void> handleBadRequest(Exception e) {
        log.warn("请求参数格式不正确：{}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID, "请求参数格式不正确");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("请求体超过上限：{}", e.getMessage());
        return Result.fail(ErrorCode.PAYLOAD_TOO_LARGE);
    }

    /**
     * 方法级权限（@PreAuthorize）拒绝时走这里；匿名请求的拒绝由 Security 的 EntryPoint 处理。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("访问被拒绝：{}", e.getMessage());
        return Result.fail(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未处理异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }

    private String firstFieldError(FieldError fieldError) {
        if (fieldError == null) {
            return ErrorCode.PARAM_INVALID.getMessage();
        }
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
