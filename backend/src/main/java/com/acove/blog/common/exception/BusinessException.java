package com.acove.blog.common.exception;

import lombok.Getter;

/**
 * 业务异常：service 层用它表达"请求不合法/资源不存在"等可预期错误，
 * 由 {@link GlobalExceptionHandler} 统一转换成 Result 返回给前端。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 返回给前端的业务码，默认 400。 */
    private final int code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
