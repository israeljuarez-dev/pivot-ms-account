package com.proyecto_final.ms_account.application.service;

import com.proyecto_final.ms_account.application.port.out.AccountRepositoryPort;
import com.proyecto_final.ms_account.domain.exception.AccountNotFoundException;
import com.proyecto_final.ms_account.domain.model.Account;
import com.proyecto_final.ms_account.support.AccountMother;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FindAccountByIdService")
class FindAccountByIdServiceTest {

    @Mock
    private AccountRepositoryPort accountRepositoryPort;

    private FindAccountByIdService findAccountByIdService;

    @BeforeEach
    void setUp() {
        findAccountByIdService = new FindAccountByIdService(accountRepositoryPort);
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Nested
        @Tag("success-case")
        @DisplayName("Casos de exito")
        class SuccessCases {

            @Test
            @DisplayName("findById - cuenta activa existente - retorna la cuenta")
            void findById_existingActiveAccount_returnsAccount() {
                // Arrange
                Account account = AccountMother.activeSavingsAccount();
                when(accountRepositoryPort.findActiveById(AccountMother.VALID_ACCOUNT_ID))
                        .thenReturn(Maybe.just(account));

                // Act
                TestObserver<Account> testObserver =
                        findAccountByIdService.findById(AccountMother.VALID_ACCOUNT_ID).test();

                // Assert
                testObserver.assertComplete();
                testObserver.assertValue(account);
            }
        }

        @Nested
        @Tag("error-case")
        @DisplayName("Casos de error")
        class ErrorCases {

            @Test
            @DisplayName("findById - cuenta inexistente o inactiva - lanza AccountNotFoundException")
            void findById_accountNotFoundOrInactive_throwsAccountNotFoundException() {
                // Arrange
                when(accountRepositoryPort.findActiveById("missing-id")).thenReturn(Maybe.empty());

                // Act
                TestObserver<Account> testObserver = findAccountByIdService.findById("missing-id").test();

                // Assert
                testObserver.assertError(error ->
                        error instanceof AccountNotFoundException
                                && ((AccountNotFoundException) error).getAccountId().equals("missing-id")
                );
            }
        }

        @Nested
        @Tag("unexpected-case")
        @DisplayName("Casos inesperados")
        class UnexpectedCases {

            @Test
            @DisplayName("findById - el repositorio falla con error no declarado - propaga el error")
            void findById_repositoryFailsWithUnexpectedError_propagatesError() {
                // Arrange
                RuntimeException databaseError = new RuntimeException("Mongo connection lost");
                when(accountRepositoryPort.findActiveById(AccountMother.VALID_ACCOUNT_ID))
                        .thenReturn(Maybe.error(databaseError));

                // Act
                TestObserver<Account> testObserver =
                        findAccountByIdService.findById(AccountMother.VALID_ACCOUNT_ID).test();

                // Assert
                testObserver.assertError(databaseError);
            }
        }
    }
}
