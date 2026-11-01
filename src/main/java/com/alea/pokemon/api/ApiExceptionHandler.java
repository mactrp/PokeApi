package com.alea.pokemon.api;

import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.alea.pokemon.service.SnapshotNotReadyException;

/** Maps failures to RFC 9457 problem details. */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Returns 400 for a bad request parameter; for enums it lists the accepted values. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleInvalidParameter(MethodArgumentTypeMismatchException exception) {
        String detail = "Invalid value '%s' for parameter '%s'.".formatted(exception.getValue(), exception.getName());
        // Only enum parameters get the list of allowed values.
        Class<?> type = exception.getRequiredType();
        if (type != null && type.isEnum()) {
            detail += " Allowed values: " + Arrays.toString(type.getEnumConstants());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid request parameter");
        return problem;
    }

    /** Returns 503 while the first catalogue snapshot is still loading. */
    @ExceptionHandler(SnapshotNotReadyException.class)
    ProblemDetail handleNotReady(SnapshotNotReadyException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problem.setTitle("Service not ready");
        return problem;
    }
}
