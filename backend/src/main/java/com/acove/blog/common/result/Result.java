package com.acove.blog.common.result;

import lombok.Data;

@Data 
public class Result<T> {
    int code;
    String message;
    T data;

    private Result() {
    }

    /**
     * Returns a success result with the given data.
     * @param <T> the type of the data
     * @param data the data to be included in the result
     * @return a Result object representing a successful operation with the provided data
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("Success");
        result.setData(data);
        return result;
    }

    /**
     * Returns a success result without any data.
     * @param <T> the type of the data
     * @return a Result object representing a successful operation without any data
     */
    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("Success");
        return result;
    }

    /**
     * Returns an error result with the given code and message.
     * @param <T> the type of the data
     * @param code the error code
     * @param message the error message
     * @return a Result object representing an error with the provided code and message
     */
    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
