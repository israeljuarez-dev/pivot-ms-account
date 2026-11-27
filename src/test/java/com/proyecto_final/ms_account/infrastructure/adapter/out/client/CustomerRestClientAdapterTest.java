package com.proyecto_final.ms_account.infrastructure.adapter.out.client;

import com.proyecto_final.ms_account.application.port.out.dto.CustomerInfoDto;
import com.proyecto_final.ms_account.domain.exception.CustomerNotFoundException;
import com.proyecto_final.ms_account.domain.exception.CustomerServiceUnavailableException;
import com.proyecto_final.ms_account.domain.model.CustomerTypeEnum;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CustomerRestClientAdapter usa WebClient directamente, sin un colaborador intermedio:
 * la frontera a simular es el transporte HTTP. Se simula mediante un ExchangeFunction de
 * prueba (la misma tecnica que usa Spring internamente para construir un WebClient de prueba
 * sin levantar red real), devolviendo un ClientResponse fabricado con el status y cuerpo deseados.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerRestClientAdapter")
class CustomerRestClientAdapterTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private CustomerRestClientAdapter customerRestClientAdapter;

    @BeforeEach
    void setUp() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://ms-customer")
                .exchangeFunction(exchangeFunction)
                .build();
        customerRestClientAdapter = new CustomerRestClientAdapter(webClient);
    }

    @Nested
    @DisplayName("findCustomerById")
    class FindCustomerById {

        @Nested
        @Tag("success-case")
        @DisplayName("Casos de exito")
        class SuccessCases {

            @Test
            @DisplayName("findCustomerById - ms-customer responde 200 - mapea el cliente")
            void findCustomerById_msCustomerRespondsOk_mapsCustomer() {
                // Arrange
                when(exchangeFunction.exchange(any())).thenReturn(Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body("{\"id\":\"cust-001\",\"type\":\"PERSONAL\",\"extraField\":\"ignored\"}")
                                .build()));

                // Act
                TestObserver<CustomerInfoDto> testObserver =
                        customerRestClientAdapter.findCustomerById("cust-001").test();

                // Assert
                testObserver.assertComplete();
                CustomerInfoDto customer = testObserver.values().get(0);
                assertAll(
                        () -> assertEquals("cust-001", customer.id()),
                        () -> assertEquals(CustomerTypeEnum.PERSONAL, customer.type())
                );
            }
        }

        @Nested
        @Tag("error-case")
        @DisplayName("Casos de error")
        class ErrorCases {

            @Test
            @DisplayName("findCustomerById - ms-customer responde 404 - lanza CustomerNotFoundException")
            void findCustomerById_msCustomerRespondsNotFound_throwsCustomerNotFoundException() {
                // Arrange
                when(exchangeFunction.exchange(any())).thenReturn(Mono.just(
                        ClientResponse.create(HttpStatus.NOT_FOUND).build()));

                // Act
                TestObserver<CustomerInfoDto> testObserver =
                        customerRestClientAdapter.findCustomerById("missing-id").test();

                // Assert
                // Assert
                testObserver.assertError(error ->
                        error instanceof CustomerNotFoundException
                                && ((CustomerNotFoundException) error).getCustomerId().equals("missing-id")
                );
            }

            @Test
            @DisplayName("findCustomerById - ms-customer responde error de servidor "
                    + "- lanza CustomerServiceUnavailableException")
            void findCustomerById_msCustomerRespondsServerError_throwsCustomerServiceUnavailableException() {
                // Arrange
                when(exchangeFunction.exchange(any())).thenReturn(Mono.just(
                        ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()));

                // Act
                TestObserver<CustomerInfoDto> testObserver =
                        customerRestClientAdapter.findCustomerById("cust-001").test();

                // Assert
                testObserver.assertError(CustomerServiceUnavailableException.class);
            }
        }

        @Nested
        @Tag("unexpected-case")
        @DisplayName("Casos inesperados")
        class UnexpectedCases {

            @Test
            @DisplayName("findCustomerById - ms-customer no es alcanzable "
                    + "- lanza CustomerServiceUnavailableException")
            void findCustomerById_msCustomerNotReachable_throwsCustomerServiceUnavailableException() {
                // Arrange
                when(exchangeFunction.exchange(any())).thenReturn(Mono.error(
                        new WebClientRequestException(
                                new java.io.IOException("Connection refused"),
                                HttpMethod.GET,
                                URI.create("http://ms-customer/customers/cust-001"),
                                new HttpHeaders())));

                // Act
                TestObserver<CustomerInfoDto> testObserver =
                        customerRestClientAdapter.findCustomerById("cust-001").test();

                // Assert
                testObserver.assertError(CustomerServiceUnavailableException.class);
            }
        }
    }
}
