package io.rdfforge.common.exception;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Registers {@link GlobalExceptionHandler} for every Spring MVC service that
 * depends on rdf-forge-common, without each service needing to widen its
 * {@code @ComponentScan} to include {@code io.rdfforge.common}.
 *
 * <p>Prior to this, {@code ResourceNotFoundException} fell through to the
 * container and surfaced as HTTP 500 (Project not found -> 500 Internal
 * Server Error) in the standalone stack, because @RestControllerAdvice is
 * a component-scan stereotype and the services only scanned their own
 * packages + {@code io.rdfforge.engine}.
 */
@AutoConfiguration
@ConditionalOnClass(RestControllerAdvice.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ExceptionHandlerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
