package com.proyecto_final.ms_account.application.service;

import com.proyecto_final.ms_account.application.port.out.AccountRepositoryPort;
import com.proyecto_final.ms_account.domain.model.Account;
import com.proyecto_final.ms_account.support.AccountMother;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
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
@DisplayName("FindAllAccountsService")
class FindAllAccountsServiceTest {

    @Mock
    private AccountRepositoryPort accountRepositoryPort;

    private FindAllAccountsService findAllAccountsService;

    @BeforeEach
    void setUp() {
        findAllAccountsService = new FindAllAccountsService(accountRepositoryPort);
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Nested
        @Tag("success-case")
        @DisplayName("Casos de exito")
        class SuccessCases {

            @Test
            @DisplayName("findAll - existen cuentas activas - retorna todas las cuentas")
            void findAll_activeAccountsExist_returnsAllAccounts() {
                // Arrange
                Account first = AccountMother.activeSavingsAccount();
                Account second = AccountMother.activeCheckingBusinessAccount();
                when(accountRepositoryPort.findAllActive()).thenReturn(Flowable.just(first, second));

                // Act
                TestSubscriber<Account> testSubscriber = findAllAccountsService.findAll().test();

                // Assert
                testSubscriber.assertComplete();
                testSubscriber.assertValues(first, second);
            }
        }

        @Nested
        @Tag("rare-case")
        @DisplayName("Casos poco frecuentes")
        class RareCases {

            @Test
            @DisplayName("findAll - no existen cuentas activas - retorna un flujo vacio")
            void findAll_noActiveAccounts_returnsEmptyFlow() {
                // Arrange
                when(accountRepositoryPort.findAllActive()).thenReturn(Flowable.empty());

                // Act
                TestSubscriber<Account> testSubscriber = findAllAccountsService.findAll().test();

                // Assert
                testSubscriber.assertComplete();
                testSubscriber.assertNoValues();
            }
        }

        @Nested
        @Tag("unexpected-case")
        @DisplayName("Casos inesperados")
        class UnexpectedCases {

            @Test
            @DisplayName("findAll - el repositorio falla con error no declarado - propaga el error")
            void findAll_repositoryFailsWithUnexpectedError_propagatesError() {
                // Arrange
                RuntimeException databaseError = new RuntimeException("Mongo connection lost");
                when(accountRepositoryPort.findAllActive()).thenReturn(Flowable.error(databaseError));

                // Act
                TestSubscriber<Account> testSubscriber = findAllAccountsService.findAll().test();

                // Assert
                testSubscriber.assertError(databaseError);
            }
        }
    }
}
