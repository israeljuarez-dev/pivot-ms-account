package com.proyecto_final.ms_account.application.service;

import com.proyecto_final.ms_account.application.port.in.dto.UpdateAccountDto;
import com.proyecto_final.ms_account.application.port.in.uses_cases.FindAccountByIdUseCase;
import com.proyecto_final.ms_account.application.port.out.AccountRepositoryPort;
import com.proyecto_final.ms_account.application.validator.AccountRulesValidator;
import com.proyecto_final.ms_account.domain.exception.AccountNotFoundException;
import com.proyecto_final.ms_account.domain.exception.InvalidAccountConditionsException;
import com.proyecto_final.ms_account.domain.exception.InvalidAccountParticipantsException;
import com.proyecto_final.ms_account.domain.model.Account;
import com.proyecto_final.ms_account.support.AccountMother;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateAccountService")
class UpdateAccountServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-10T10:00:00Z");

    @Mock
    private FindAccountByIdUseCase findAccountByIdUseCase;

    @Mock
    private AccountRepositoryPort accountRepositoryPort;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    private UpdateAccountService updateAccountService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        updateAccountService = new UpdateAccountService(
                findAccountByIdUseCase, accountRepositoryPort, new AccountRulesValidator(), clock);
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Nested
        @Tag("success-case")
        @DisplayName("Casos de exito")
        class SuccessCases {

            @Test
            @DisplayName("update - cuenta de ahorro existente con condiciones validas "
                    + "- actualiza el limite mensual y conserva la identidad")
            void update_existingSavingsAccountWithValidConditions_updatesLimitAndKeepsIdentity() {
                // Arrange
                Account existing = AccountMother.activeSavingsAccount();
                UpdateAccountDto dto = AccountMother.updateSavingsAccountDto(existing.getId());
                when(findAccountByIdUseCase.findById(existing.getId())).thenReturn(Single.just(existing));
                when(accountRepositoryPort.save(any(Account.class)))
                        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

                // Act
                TestObserver<Account> testObserver = updateAccountService.update(dto).test();

                // Assert
                testObserver.assertComplete();
                verify(accountRepositoryPort).save(accountCaptor.capture());
                Account saved = accountCaptor.getValue();
                assertAll(
                        () -> assertEquals(existing.getId(), saved.getId()),
                        () -> assertEquals(existing.getType(), saved.getType()),
                        () -> assertEquals(dto.monthlyMovementLimit(), saved.getMonthlyMovementLimit()),
                        () -> assertEquals(FIXED_INSTANT, saved.getUpdatedAt())
                );
            }
        }

        @Nested
        @Tag("error-case")
        @DisplayName("Casos de error")
        class ErrorCases {

            @Test
            @DisplayName("update - cuenta inexistente o inactiva - propaga AccountNotFoundException sin guardar")
            void update_accountNotFoundOrInactive_propagatesAccountNotFoundExceptionWithoutSaving() {
                // Arrange
                UpdateAccountDto dto = AccountMother.updateSavingsAccountDto("missing-id");
                when(findAccountByIdUseCase.findById("missing-id"))
                        .thenReturn(Single.error(new AccountNotFoundException("missing-id")));

                // Act
                TestObserver<Account> testObserver = updateAccountService.update(dto).test();

                // Assert
                testObserver.assertError(AccountNotFoundException.class);
                verify(accountRepositoryPort, never()).save(any());
            }

            @Test
            @DisplayName("update - condiciones no corresponden al tipo de la cuenta "
                    + "- lanza InvalidAccountConditionsException sin guardar")
            void update_conditionsDoNotMatchAccountType_throwsInvalidAccountConditionsExceptionWithoutSaving() {
                // Arrange: la cuenta es de ahorro (SAVINGS) pero se envia maintenanceFee, propia de CHECKING
                Account existing = AccountMother.activeSavingsAccount();
                UpdateAccountDto dto = UpdateAccountDto.builder()
                        .accountId(existing.getId())
                        .monthlyMovementLimit(null)
                        .maintenanceFee(new BigDecimal("5.00"))
                        .movementDayOfMonth(null)
                        .holders(List.of())
                        .signers(List.of())
                        .build();
                when(findAccountByIdUseCase.findById(existing.getId())).thenReturn(Single.just(existing));

                // Act
                TestObserver<Account> testObserver = updateAccountService.update(dto).test();

                // Assert
                testObserver.assertError(InvalidAccountConditionsException.class);
                verify(accountRepositoryPort, never()).save(any());
            }

            @Test
            @DisplayName("update - titulares invalidos para el tipo de cliente de la cuenta "
                    + "- lanza InvalidAccountParticipantsException sin guardar")
            void update_invalidParticipantsForAccountCustomerType_throwsInvalidAccountParticipantsExceptionWithoutSaving() {
                // Arrange: la cuenta es de un cliente BUSINESS, que exige al menos un titular
                Account existing = AccountMother.activeCheckingBusinessAccount();
                UpdateAccountDto dto = UpdateAccountDto.builder()
                        .accountId(existing.getId())
                        .monthlyMovementLimit(null)
                        .maintenanceFee(new BigDecimal("20.00"))
                        .movementDayOfMonth(null)
                        .holders(List.of())
                        .signers(List.of())
                        .build();
                when(findAccountByIdUseCase.findById(existing.getId())).thenReturn(Single.just(existing));

                // Act
                TestObserver<Account> testObserver = updateAccountService.update(dto).test();

                // Assert
                testObserver.assertError(InvalidAccountParticipantsException.class);
                verify(accountRepositoryPort, never()).save(any());
            }
        }

        @Nested
        @Tag("unexpected-case")
        @DisplayName("Casos inesperados")
        class UnexpectedCases {

            @Test
            @DisplayName("update - el guardado falla con error no declarado - propaga el error")
            void update_saveFailsWithUnexpectedError_propagatesError() {
                // Arrange
                Account existing = AccountMother.activeSavingsAccount();
                UpdateAccountDto dto = AccountMother.updateSavingsAccountDto(existing.getId());
                RuntimeException databaseError = new RuntimeException("Mongo write timeout");
                when(findAccountByIdUseCase.findById(existing.getId())).thenReturn(Single.just(existing));
                when(accountRepositoryPort.save(any(Account.class))).thenReturn(Single.error(databaseError));

                // Act
                TestObserver<Account> testObserver = updateAccountService.update(dto).test();

                // Assert
                testObserver.assertError(databaseError);
            }
        }
    }
}
