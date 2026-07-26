package com.rzodeczko.presentation.exception;

import com.rzodeczko.application.exception.GeoLocationException;
import com.rzodeczko.application.exception.GeoLocationNetworkException;
import com.rzodeczko.application.exception.GeoLocationUnavailableException;
import com.rzodeczko.application.exception.VpnDetectedException;
import com.rzodeczko.domain.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    public ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> "%s: %s".formatted(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", detail);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(VpnDetectedException.class)
    public ProblemDetail handle(VpnDetectedException e) {
        log.warn("VPN detected: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handle(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(CouponNotFoundException.class)
    public ProblemDetail handle(CouponNotFoundException e) {
        log.warn("Coupon not found: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(CouponCountryMismatchException.class)
    public ProblemDetail handle(CouponCountryMismatchException e) {
        log.warn("Country mismatch: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(CouponAlreadyExistsException.class)
    public ProblemDetail handle(CouponAlreadyExistsException e) {
        log.warn("Coupon already exists: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(CouponExhaustedException.class)
    public ProblemDetail handle(CouponExhaustedException e) {
        log.warn("Coupon exhausted: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(CouponAlreadyUsedByUserException.class)
    public ProblemDetail handle(CouponAlreadyUsedByUserException e) {
        log.warn("Coupon already used by user: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(CouponConcurrentModificationException.class)
    public ProblemDetail handle(CouponConcurrentModificationException e) {
        log.warn("Concurrent modification: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(GeoLocationUnavailableException.class)
    public ProblemDetail handle(GeoLocationUnavailableException e) {
        log.error("Geolocation service unavailable: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Geolocation service is temporarily unavailable"
        );
    }

    @ExceptionHandler(GeoLocationNetworkException.class)
    public ProblemDetail handle(GeoLocationNetworkException e) {
        log.error("Geolocation network error: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Geolocation service is temporarily unavailable"
        );
    }

    @ExceptionHandler(GeoLocationException.class)
    public ProblemDetail handle(GeoLocationException e) {
        log.warn("Geolocation error: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handle(Exception e) {
        log.error("Unexpected error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }
}
