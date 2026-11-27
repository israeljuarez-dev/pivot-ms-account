package com.proyecto_final.ms_account.infrastructure.adapter.in.rest.controller;

import com.proyecto_final.ms_account.application.port.in.uses_cases.CreateAccountUseCase;
import com.proyecto_final.ms_account.application.port.in.uses_cases.DeleteAccountUseCase;
import com.proyecto_final.ms_account.application.port.in.uses_cases.FindAccountByIdUseCase;
import com.proyecto_final.ms_account.application.port.in.uses_cases.FindAllAccountsUseCase;
import com.proyecto_final.ms_account.application.port.in.uses_cases.UpdateAccountUseCase;
import com.proyecto_final.ms_account.domain.exception.AccountLimitExceededException;
import com.proyecto_final.ms_account.domain.exception.AccountNotFoundException;
import com.proyecto_final.ms_account.domain.exception.CustomerNotFoundException;
import com.proyecto_final.ms_account.domain.model.Account;
import com.proyecto_final.ms_account.domain.model.AccountTypeEnum;
import com.proyecto_final.ms_account.infrastructure.adapter.in.rest.dto.request.CreateAccountRequestDto;
import com.proyecto_final.ms_account.infrastructure.adapter.in.rest.dto.request.UpdateAccountRequestDto;
import com.proyecto_final.ms_account.infrastructure.adapter.in.rest.dto.response.AccountResponseDto;
import com.proyecto_final.ms_account.infrastructure.adapter.in.rest.exception.GlobalExceptionHandler;
import com.proyecto_final.ms_account.infrastructure.adapter.in.rest.mapper.AccountRestMapper;
import com.proyecto_final.ms_account.support.AccountMother;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountController")
class AccountControllerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-10T10:00:00Z");

    @Mock
    private CreateAccountUseCase createAccountUseCase;

    @Mock
    private FindAllAccountsUseCase findAllAccountsUseCase;

    @Mock
    private FindAccountByIdUseCase findAccountByIdUseCase;

    @Mock
    private UpdateAccountUseCase updateAccountUseCase;

    @Mock
    private DeleteAccountUseCase deleteAccountUseCase;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        AccountRestMapper accountRestMapper = new AccountRestMapper();
        AccountController accountController = new AccountController(
                createAccountUseCase,
                findAllAccountsUseCase,
                findAccountByIdUseCase,
                updateAccountUseCase,
                deleteAccountUseCase,
                accountRestMapper);
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

        webTestClient = WebTestClient.bindToController(accountController)
                .controllerAdvice(new GlobalExceptionHandler(fixedClock))
                .build();
    }

    @Nested
    @DisplayName("POST /accounts")
    class Create {

        @Nested
        @Tag("success-case")
        @DisplayName("Casos de exito")
        class SuccessCases {

            @Test
            @DisplayName("create - solicitud valida - retorna 201 con la cuenta creada")
            void create_validRequest_returns201WithCreatedAccount() {
                // Arrange
                CreateAccountRequestDto request = CreateAccountRequestDto.builder()
                        .customerId(AccountMother.VALID_CUSTOMER_ID)
                        .type(AccountTypeEnum.SAVINGS)
                        .monthlyMovementLimit(10)
                        .holders(List.of())
                        .signers(List.of())
                        .build();
                Account created = AccountMother.activeSavingsAccount();
                when(createAccountUseCase.create(any())).thenReturn(Single.just(created));

                // Act + Assert
                webTestClient.post().uri("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)
                        .exchange()
                        .expectStatus().isCreated()
                        .expectBody(AccountResponseDto.class)
                        .value(response -> {
                            assert response != null;
                            assertEquals(created.getId(), response.id());
                        });
            }
        }

        @Nested
        @Tag("error-case")
        @DisplayName("Casos de error")
        class ErrorCases {

            @Test
            @DisplayName("create - cliente inexistente - retorna 422")
            void create_customerNotFound_returns422() {
                // Arrange
                CreateAccountRequestDto request = CreateAccountRequestDto.builder()
                        .customerId("missing-customer")
                        .type(AccountTypeEnum.SAVINGS)
                        .monthlyMovementLimit(10)
                        .holders(List.of())
                        .signers(List.of())
                        .build();
                when(createAccountUseCase.create(any()))
                        .thenReturn(Single.error(new CustomerNotFoundException("missing-customer")));

                // Act + Assert
                webTestClient.post().uri("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)
                        .exchange()
                        .expectStatus().isEqualTo(422);
            }

            @Test
            @DisplayName("create - limite de cuentas alcanzado - retorna 409")
            void create_accountLimitExceeded_returns409() {
                // Arrange
                CreateAccountRequestDto request = CreateAccountRequestDto.builder()
                        .customerId(AccountMother.VALID_CUSTOMER_ID)
                        .type(AccountTypeEnum.SAVINGS)
                        .monthlyMovementLimit(10)
                        .holders(List.of())
                        .signers(List.of())
                        .build();
                when(createAccountUseCase.create(any())).thenReturn(Single.error(
                        new AccountLimitExceededException(AccountMother.VALID_CUSTOMER_ID, AccountTypeEnum.SAVINGS)));

                // Act + Assert
                webTestClient.post().uri("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)
                        .exchange()
                        .expectStatus().isEqualTo(409);
            }
        }
    }

    @Nested
    @DisplayName("GET /accounts")
    class FindAll {

        @Nested
        @Tag("success-case")
        @DisplayName("Casos de exito")
        class SuccessCases {

            @Test
            @DisplayName("findAll - existen cuentas - retorna 200 con la lista")
            void findAll_accountsExist_returns200WithList() {
                // Arrange
                when(findAllAccountsUseCase.findAll()).thenReturn(Flowable.just(
                        AccountMother.activeSavingsAccount(), AccountMother.activeCheckingBusinessAccount()));

                // Act + Assert
                webTestClient.get().uri("/accounts")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBodyList(AccountResponseDto.class)
                        .hasSize(2);
            }
        }

        @Nested
        @Tag("rare-case")
        @DisplayName("Casos poco frecuentes")
        class RareCases {

            @Test
            @DisplayName("findAll - no existen cuentas - retorna 200 con lista vacia")
            void findAll_noAccounts_returns200WithEmptyList() {
                // Arrange
                when(findAllAccountsUseCase.findAll()).thenReturn(Flowable.empty());

                // Act + Assert
                webTestClient.get().uri("/accounts")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBodyList(AccountResponseDto.class)
                        .hasSize(0);
            }
        }
    }

    @Nested
    @DisplayName("GET /accounts/{accountId}")
    class FindById {

        @Nested
        @Tag("success-case")
        @DisplayName("Casos de exito")
        class SuccessCases {

            @Test
            @DisplayName("findById - cuenta existente - retorna 200 con la cuenta")
            void findById_existingAccount_returns200WithAccount() {
                // Arrange
                Account account = AccountMother.activeSavingsAccount();
                when(findAccountByIdUseCase.findById(account.getId())).thenReturn(Single.just(account));

                // Act + Assert
                webTestClient.get().uri("/accounts/{accountId}", account.getId())
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(AccountResponseDto.class)
                        .value(response -> {
                            assert response != null;
                            assertEquals(account.getId(), response.id());
                        });
            }
        }

        @Nested
        @Tag("error-case")
        @DisplayName("Casos de error")
        class ErrorCases {

            @Test
            @DisplayName("findById - cuenta inexistente - retorna 404")
            void findById_missingAccount_returns404() {
                // Arrange
                when(findAccountByIdUseCase.findById("missing-id"))
                        .thenReturn(Single.error(new AccountNotFoundException("missing-id")));

                // Act + Assert
                webTestClient.get().uri("/accounts/{accountId}", "missing-id")
                        .exchange()
                        .expectStatus().isNotFound();
            }
        }
    }

    @Nested
    @DisplayName("PUT /accounts/{accountId}")
    class Update {

        @Nested
        @Tag("success-case")
        @DisplayName("Casos de exito")
        class SuccessCases {

            @Test
            @DisplayName("update - solicitud valida - retorna 200 con la cuenta actualizada")
            void update_validRequest_returns200WithUpdatedAccount() {
                // Arrange
                Account updated = AccountMother.activeSavingsAccount().toBuilder()
                        .monthlyMovementLimit(25)
                        .build();
                UpdateAccountRequestDto request = UpdateAccountRequestDto.builder()
                        .monthlyMovementLimit(25)
                        .holders(List.of())
                        .signers(List.of())
                        .build();
                when(updateAccountUseCase.update(any())).thenReturn(Single.just(updated));

                // Act + Assert
                webTestClient.put().uri("/accounts/{accountId}", updated.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(AccountResponseDto.class)
                        .value(response -> {
                            assert response != null;
                            assertEquals(25, response.monthlyMovementLimit());
                        });
            }
        }

        @Nested
        @Tag("error-case")
        @DisplayName("Casos de error")
        class ErrorCases {

            @Test
            @DisplayName("update - cuenta inexistente - retorna 404")
            void update_missingAccount_returns404() {
                // Arrange
                UpdateAccountRequestDto request = UpdateAccountRequestDto.builder()
                        .monthlyMovementLimit(25)
                        .holders(List.of())
                        .signers(List.of())
                        .build();
                when(updateAccountUseCase.update(any()))
                        .thenReturn(Single.error(new AccountNotFoundException("missing-id")));

                // Act + Assert
                webTestClient.put().uri("/accounts/{accountId}", "missing-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)
                        .exchange()
                        .expectStatus().isNotFound();
            }
        }
    }

    @Nested
    @DisplayName("DELETE /accounts/{accountId}")
    class Delete {

        @Nested
        @Tag("success-case")
        @DisplayName("Casos de exito")
        class SuccessCases {

            @Test
            @DisplayName("delete - cuenta existente - retorna 204 sin contenido")
            void delete_existingAccount_returns204NoContent() {
                // Arrange
                when(deleteAccountUseCase.delete(AccountMother.VALID_ACCOUNT_ID))
                        .thenReturn(Completable.complete());

                // Act + Assert
                webTestClient.delete().uri("/accounts/{accountId}", AccountMother.VALID_ACCOUNT_ID)
                        .exchange()
                        .expectStatus().isNoContent()
                        .expectBody().isEmpty();
            }
        }

        @Nested
        @Tag("error-case")
        @DisplayName("Casos de error")
        class ErrorCases {

            @Test
            @DisplayName("delete - cuenta inexistente - retorna 404")
            void delete_missingAccount_returns404() {
                // Arrange
                when(deleteAccountUseCase.delete("missing-id"))
                        .thenReturn(Completable.error(new AccountNotFoundException("missing-id")));

                // Act + Assert
                webTestClient.delete().uri("/accounts/{accountId}", "missing-id")
                        .exchange()
                        .expectStatus().isNotFound();
            }
        }
    }
}
