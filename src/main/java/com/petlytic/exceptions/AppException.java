package com.petlytic.exceptions;

import com.petlytic.models.enums.ErrorCode;
import com.petlytic.models.enums.ResourceType;
import lombok.Getter;

import java.util.Arrays;

@Getter
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode, Object... args) {
        super(String.format(errorCode.getMessage(),
                Arrays.stream(args)
                        .map(arg -> {
                            if (arg instanceof ResourceType) {
                                return ((ResourceType) arg).getLabel();
                            }
                            return arg;
                        })
                        .toArray()));

        this.errorCode = errorCode;
    }
}
