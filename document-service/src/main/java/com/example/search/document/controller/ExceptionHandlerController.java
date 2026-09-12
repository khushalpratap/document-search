package com.example.search.document.controller;

import com.example.search.common.ApiError;
import com.example.search.document.service.DocumentService.DocumentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestControllerAdvice
public class ExceptionHandlerController {

    @ExceptionHandler(DocumentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound(DocumentNotFoundException ex) {
        return new ApiError(Instant.now(), 404, "Not Found", ex.getMessage(), "");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badRequest(IllegalArgumentException ex) {
        return new ApiError(Instant.now(), 400, "Bad Request", ex.getMessage(), "");
    }
}
