package com.edu.common.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.edu.common.result.Result;

public class CommonFallbackHandler {

    public static <T> Result<T> blockHandler(String message, BlockException ex) {
        return Result.error(429, message != null ? message : "系统繁忙，请稍后重试");
    }

    public static <T> Result<T> fallback(String message, Throwable ex) {
        return Result.error(500, message != null ? message : ("服务降级：" + ex.getMessage()));
    }
}
