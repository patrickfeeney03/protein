package com.example.demo.controllers.errors;

import com.example.demo.dedup.ExactDuplicateFoodException;
import com.example.demo.dedup.PossibleDuplicateFoodException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ExactDuplicateFoodException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public DedupErrorResponse handleExactDuplicate(ExactDuplicateFoodException ex) {
        LOGGER.info(
                "Rejected exact duplicate on {} key={} existingId={}",
                requestSummary(),
                ex.getCanonicalProductKey(),
                ex.getExistingFoodId()
        );
        return new DedupErrorResponse(
                "EXACT_DUPLICATE",
                "Food with the same product URL key already exists",
                ex.getCanonicalProductKey(),
                ex.getExistingFoodId(),
                List.of()
        );
    }

    @ExceptionHandler(PossibleDuplicateFoodException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public DedupErrorResponse handlePossibleDuplicate(PossibleDuplicateFoodException ex) {
        LOGGER.info(
                "Rejected possible duplicate on {} candidates={}",
                requestSummary(),
                ex.getCandidates() == null ? 0 : ex.getCandidates().size()
        );
        return new DedupErrorResponse(
                "POSSIBLE_DUPLICATE",
                "Potential duplicate foods found",
                null,
                null,
                ex.getCandidates()
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        var status = ex.getStatusCode();
        var reason = ex.getReason() != null ? ex.getReason() : "No message";
        if (status.is5xxServerError()) {
            LOGGER.error("Handled {} on {}: {}", status, requestSummary(), reason, ex);
        } else {
            LOGGER.info("Handled {} on {}: {}", status, requestSummary(), reason);
        }
        var body = Map.<String, Object>of(
                "error", status.toString(),
                "message", reason
        );
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        var fields = new TreeMap<String, String>();
        for (var error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(
                    error.getField(),
                    error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()
            );
        }
        LOGGER.info("Validation failed on {} fields={}", requestSummary(), fields);
        return Map.of(
                "error", "VALIDATION_ERROR",
                "message", "Request validation failed",
                "fields", fields
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleUnexpected(Exception ex) {
        LOGGER.error("Unexpected error on {}", requestSummary(), ex);
        return Map.of(
                "error", "INTERNAL_SERVER_ERROR",
                "message", "An unexpected error occurred"
        );
    }

    private String requestSummary() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            var request = servletAttrs.getRequest();
            return request.getMethod() + " " + request.getRequestURI();
        }
        return "unknown request";
    }
}
