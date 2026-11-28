package com.proyecto_final.ms_account.infrastructure.adapter.out.client;

import com.proyecto_final.ms_account.application.port.out.client.CustomerClientPort;
import com.proyecto_final.ms_account.application.port.out.dto.CustomerInfoDto;
import com.proyecto_final.ms_account.domain.exception.CustomerNotFoundException;
import com.proyecto_final.ms_account.domain.exception.CustomerServiceUnavailableException;
import com.proyecto_final.ms_account.infrastructure.adapter.out.client.dto.CustomerClientResponseDto;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

/**
 * Implementa el puerto de salida de clientes consultando la API REST de ms-customer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerRestClientAdapter implements CustomerClientPort {

    private static final String SERVICE_NAME = "ms-customer";

    private final WebClient customerWebClient;

    /**
     * {@inheritDoc}
     */
    @CircuitBreaker(name = SERVICE_NAME, fallbackMethod = "findCustomerByIdFallback")
    @RateLimiter(name = SERVICE_NAME, fallbackMethod = "findCustomerByIdFallback")
    @TimeLimiter(name = SERVICE_NAME, fallbackMethod = "findCustomerByIdFallback")
    @Override
    public Single<CustomerInfoDto> findCustomerById(String customerId) {
        Mono<CustomerInfoDto> customer = customerWebClient.get()
                .uri("/customers/{customerId}", customerId)
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), response -> {
                    log.warn("Customer not found in ms-customer, id {}", customerId);
                    return Mono.error(new CustomerNotFoundException(customerId));
                })
                .onStatus(HttpStatusCode::is5xxServerError, response -> {
                    log.error("ms-customer answered with server error {}", response.statusCode().value());
                    return Mono.error(new CustomerServiceUnavailableException(
                            "received status " + response.statusCode().value()));
                })
                .bodyToMono(CustomerClientResponseDto.class)
                .map(response -> new CustomerInfoDto(response.id(), response.type()))
                .onErrorMap(WebClientRequestException.class, exception -> {
                    log.error("ms-customer is not reachable", exception);
                    return new CustomerServiceUnavailableException(exception.getMessage());
                });

        return Single.fromPublisher(customer);
    }

    private Single<CustomerInfoDto> findCustomerByIdFallback(String customerId, Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            log.error("Circuit breaker OPEN para ms-customer, id {}", customerId);
            return Single.error(new CustomerServiceUnavailableException("circuit breaker open"));
        }
        if (throwable instanceof TimeoutException) {
            log.error("ms-customer no respondió dentro del timeout configurado, id {}", customerId);
            return Single.error(new CustomerServiceUnavailableException("timeout"));
        }

        log.warn("findCustomerById fallback, id {}: {}", customerId, throwable.getMessage());
        return Single.error(throwable);
    }
}
