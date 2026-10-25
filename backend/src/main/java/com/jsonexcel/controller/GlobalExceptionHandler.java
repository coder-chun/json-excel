package com.jsonexcel.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理，向前端返回统一的错误结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        return build(HttpStatus.BAD_REQUEST, "请求体不是合法的 JSON");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnknown(Exception e) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误：" + e.getMessage());
    }

    private ResponseEntity<Map<String, String>> build(HttpStatus status, String message) {
        Map<String, String> body = new LinkedHashMap<String, String>();
        body.put("message", message == null ? "未知错误" : message);
        return new ResponseEntity<Map<String, String>>(body, status);
    }
}
