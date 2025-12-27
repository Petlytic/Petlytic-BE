package com.petlytic.exceptions;

import com.petlytic.models.enums.ResourceType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(ResourceType resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'",
                resourceName.getLabel(),
                fieldName,
                fieldValue));
    }


}
