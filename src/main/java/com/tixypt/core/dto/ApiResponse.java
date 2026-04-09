package com.tixy.core.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.tixy.core.exception.ErrorResponse;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorResponse error
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(false, null, ErrorResponse.builder()
                .code(code)
                .message(message)
                .build());
    }

    public static ApiResponse<Void> fail(ErrorResponse errorResponse) {
        return new ApiResponse<>(false, null, errorResponse);
    }
}