package com.alea.pokemon.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ProblemDetail;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.alea.pokemon.domain.FormsMode;
import com.alea.pokemon.service.SnapshotNotReadyException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    /** Any method will do: the handler never looks at the parameter, Spring just requires one. */
    @SuppressWarnings("unused")
    private static void anyHandlerMethod(String forms) {
    }

    private static MethodArgumentTypeMismatchException mismatch(Object value, Class<?> requiredType) {
        try {
            MethodParameter parameter = new MethodParameter(
                    ApiExceptionHandlerTest.class.getDeclaredMethod("anyHandlerMethod", String.class), 0);
            return new MethodArgumentTypeMismatchException(value, requiredType, "forms", parameter, null);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void listsTheAllowedValuesWhenTheParameterIsAnEnum() {
        ProblemDetail problem = handler.handleInvalidParameter(mismatch("everything", FormsMode.class));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Invalid request parameter");
        assertThat(problem.getDetail())
                .isEqualTo("Invalid value 'everything' for parameter 'forms'. Allowed values: [DEFAULT_ONLY, ALL, BEST_PER_SPECIES]");
    }

    @Test
    void doesNotListValuesWhenTheParameterIsNotAnEnum() {
        ProblemDetail problem = handler.handleInvalidParameter(mismatch("abc", Integer.class));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("Invalid value 'abc' for parameter 'forms'.");
    }

    @Test
    void doesNotListValuesWhenTheRequiredTypeIsUnknown() {
        ProblemDetail problem = handler.handleInvalidParameter(mismatch("abc", null));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("Invalid value 'abc' for parameter 'forms'.");
    }

    @Test
    void answers503WhenTheCatalogueIsNotLoaded() {
        ProblemDetail problem = handler.handleNotReady(new SnapshotNotReadyException());

        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getTitle()).isEqualTo("Service not ready");
    }
}
