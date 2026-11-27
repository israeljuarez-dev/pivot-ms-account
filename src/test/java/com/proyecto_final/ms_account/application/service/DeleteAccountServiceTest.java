package com.proyecto_final.ms_account.application.service;

import com.proyecto_final.ms_account.application.port.in.uses_cases.FindAccountByIdUseCase;
import com.proyecto_final.ms_account.application.port.out.AccountRepositoryPort;
import com.proyecto_final.ms_account.domain.exception.AccountNotFoundException;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeleteAccountService depende de FindAccountByIdUseCase como colaborador (frontera de
 * aplicacion) y se simula; su logica propia ya se prueba en FindAccountByIdServiceTest.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteAccountService")
class DeleteAccountServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-10T10:00:00Z");

    @Mock
    private FindAccountByIdUseCase findAccountByIdUseCase;

    @Mock
    private AccountRepositoryPort accountRepositoryPort;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    private DeleteAccountService deleteAccountService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        deleteAccountService = new DeleteAccountService(findAccountByIdUseCase, accountRepositoryPort, clock);
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Nested
        @Tag("success-case")
        @DisplayName("Casos de exito")
        class SuccessCases {

            @Test
            @DisplayName("delete - cuenta activa existente - la guarda inactiva y completa")
            void delete_existingActiveAccount_savesItInactiveAndCompletes() {
                // Arrange
                Account existing = AccountMother.activeSavingsAccount();
                when(findAccountByIdUseCase.findById(existing.getId())).thenReturn(Single.just(existing));
                when(accountRepositoryPort.save(any(Account.class)))
                        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

                // Act
                TestObserver<Void> testObserver = deleteAccountService.delete(existing.getId()).test();

                // Assert
                testObserver.assertComplete();
                testObserver.assertNoErrors();
                verify(accountRepositoryPort).save(accountCaptor.capture());
                Account saved = accountCaptor.getValue();
                assertAll(
                        () -> assertEquals(existing.getId(), saved.getId()),
                        () -> assertFalse(saved.isActive()),
                        () -> assertEquals(FIXED_INSTANT, saved.getUpdatedAt())
                );
            }
        }

        @Nested
        @Tag("error-case")
        @DisplayName("Casos de error")
        class ErrorCases {

            @Test
            @DisplayName("delete - cuenta inexistente o inactiva - propaga AccountNotFoundException sin guardar")
            void delete_accountNotFoundOrInactive_propagatesAccountNotFoundExceptionWithoutSaving() {
                // Arrange
                when(findAccountByIdUseCase.findById("missing-id"))
                        .thenReturn(Single.error(new AccountNotFoundException("missing-id")));

                // Act
                TestObserver<Void> testObserver = deleteAccountService.delete("missing-id").test();

                // Assert
                testObserver.assertError(AccountNotFoundException.class);
                verify(accountRepositoryPort, never()).save(any());
            }
        }

        @Nested
        @Tag("unexpected-case")
        @DisplayName("Casos inesperados")
        class UnexpectedCases {

            @Test
            @DisplayName("delete - el guardado falla con error no declarado - propaga el error")
            void delete_saveFailsWithUnexpectedError_propagatesError() {
                // Arrange
                Account existing = AccountMother.activeSavingsAccount();
                RuntimeException databaseError = new RuntimeException("Mongo write timeout");
                when(findAccountByIdUseCase.findById(existing.getId())).thenReturn(Single.just(existing));
                when(accountRepositoryPort.save(any(Account.class))).thenReturn(Single.error(databaseError));

                // Act
                TestObserver<Void> testObserver = deleteAccountService.delete(existing.getId()).test();

                // Assert
                testObserver.assertError(databaseError);
            }
        }
    }
}
