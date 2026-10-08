package com.acove.blog.common.exception;

import com.acove.blog.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j 
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handle MethodArgumentNotValidException thrown when validation on an argument annotated with @Valid fails.
     * @param ex the MethodArgumentNotValidException thrown during validation
     * @return a Result object containing the error code and validation error message
     */
    @ExceptionHandler (MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldError().getDefaultMessage();
        log.error("Validation error: {}", errorMessage);
        return Result.error(400, errorMessage);
    }

    /**
     * Handle BusinessException thrown by the service layer.
     * @param ex the BusinessException carrying the business code and message
     * @return a Result object containing that code and message
     */
    @ExceptionHandler (BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        log.warn("Business error: {}", ex.getMessage());
        return Result.error(ex.getCode(), ex.getMessage());
    }

    /**
     * Handle a required request parameter that the client did not send.
     * @param ex the MissingServletRequestParameterException naming the absent parameter
     * @return a Result object with code 400
     */
    @ExceptionHandler (MissingServletRequestParameterException.class)
    public Result<Void> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName());
        return Result.error(400, "缺少必要参数：" + ex.getParameterName());
    }

    /**
     * Handle a body that is missing or cannot be parsed into the target type.
     * @param ex the HttpMessageNotReadableException thrown while reading the request body
     * @return a Result object with code 400
     */
    @ExceptionHandler (HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return Result.error(400, "请求体格式不正确");
    }

    /**
     * Handle an uploaded file larger than the configured multipart limit.
     * @param ex the MaxUploadSizeExceededException thrown by the multipart resolver
     * @return a Result object with code 400
     */
    @ExceptionHandler (MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.warn("Uploaded file is too large: {}", ex.getMessage());
        return Result.error(400, "上传的图片过大，头像不能超过 5MB");
    }

    @ExceptionHandler (Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("Internal server error: ", ex);
        return Result.error(500, "System busy, please try again later.");
    }
}
