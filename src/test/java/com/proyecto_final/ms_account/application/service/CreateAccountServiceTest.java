package com.proyecto_final.ms_account.application.service;

import com.proyecto_final.ms_account.application.port.in.dto.CreateAccountDto;
import com.proyecto_final.ms_account.application.port.out.AccountRepositoryPort;
import com.proyecto_final.ms_account.application.port.out.client.CustomerClientPort;
import com.proyecto_final.ms_account.application.validator.AccountRulesValidator;
import com.proyecto_final.ms_account.domain.exception.AccountLimitExceededException;
import com.proyecto_final.ms_account.domain.exception.AccountTypeNotAllowedException;
import com.proyecto_final.ms_account.domain.exception.CustomerNotFoundException;
import com.proyecto_final.ms_account.domain.exception.CustomerServiceUnavailableException;
import com.proyecto_final.ms_account.domain.exception.InvalidAccountConditionsException;
import com.proyecto_final.ms_account.domain.exception.InvalidAccountParticipantsException;
import com.proyecto_final.ms_account.domain.model.Account;
import com.proyecto_final.ms_account.domain.model.AccountTypeEnum;
import com.proyecto_final.ms_account.domain.model.CustomerTypeEnum;
import com.proyecto_final.ms_account.support.AccountMother;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AccountRulesValidator es un colaborador puro sin I/O (solo valida y lanza excepciones de
 * dominio): se usa una instancia real para ejercitar sus ramas reales, no se simula.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateAccountService")
class CreateAccountServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-10T10:00:00Z");

    @Mock
    private AccountRepositoryPort accountRepositoryPort;

    @Mock
    private CustomerClientPort customerClientPort;

    private CreateAccountService createAccountService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        createAccountService = new CreateAccountService(
                accountRepositoryPort, customerClientPort, new AccountRulesValidator(), clock);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Nested
        @Tag("success-case")
        @DisplayName("Casos de exito")
        class SuccessCases {

            @Test
            @DisplayName("create - cliente personal sin cuentas de ahorro previas - retorna la cuenta activa guardada")
            void create_personalCustomerWithoutExistingSavings_returnsSavedActiveAccount() {
                // Arrange
                CreateAccountDto dto = AccountMother.createSavingsAccountDto(AccountMother.VALID_CUSTOMER_ID);
                when(customerClientPort.findCustomerById(AccountMother.VALID_CUSTOMER_ID))
                        .thenReturn(Single.just(AccountMother.personalCustomerInfo(AccountMother.VALID_CUSTOMER_ID)));
                when(accountRepositoryPort.countActiveByCustomerIdAndType(
                        AccountMother.VALID_CUSTOMER_ID, AccountTypeEnum.SAVINGS))
                        .thenReturn(Single.just(0L));
                when(accountRepositoryPort.save(any(Account.class)))
                        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

                // Act
                TestObserver<Account> testObserver = createAccountService.create(dto).test();

                // Assert
                testObserver.assertComplete();
                Account saved = testObserver.values().get(0);
                assertNotNull(saved);
                assertAll(
                        () -> assertEquals(AccountTypeEnum.SAVINGS, saved.getType()),
                        () -> assertEquals(CustomerTypeEnum.PERSONAL, saved.getCustomerType()),
                        () -> assertTrue(saved.isActive()),
                        () -> assertEquals(FIXED_INSTANT, saved.getCreatedAt())
                );
            }
        }

        @Nested
        @Tag("rare-case")
        @DisplayName("Casos poco frecuentes")
        class RareCases {

            @Test
            @DisplayName("create - cuenta corriente para cliente empresarial sin limite definido "
                    + "- guarda la cuenta aunque ya tenga muchas cuentas activas")
            void create_checkingAccountForBusinessCustomerWithoutLimit_savesAccountRegardlessOfCount() {
                // Arrange: CHECKING no define limite para BUSINESS en AccountTypeEnum
                CreateAccountDto dto = AccountMother.createCheckingAccountDto(
                        "cust-002", List.of(AccountMother.samplePerson()));
                when(customerClientPort.findCustomerById("cust-002"))
                        .thenReturn(Single.just(AccountMother.businessCustomerInfo("cust-002")));
                when(accountRepositoryPort.countActiveByCustomerIdAndType("cust-002", AccountTypeEnum.CHECKING))
                        .thenReturn(Single.just(50L));
                when(accountRepositoryPort.save(any(Account.class)))
                        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

                // Act
                TestObserver<Account> testObserver = createAccountService.create(dto).test();

                // Assert
                testObserver.assertComplete();
                testObserver.assertValueCount(1);
            }
        }

        @Nested
        @Tag("error-case")
        @DisplayName("Casos de error")
        class ErrorCases {

            @Test
            @DisplayName("create - condiciones no corresponden al tipo de cuenta "
                    + "- lanza InvalidAccountConditionsException sin consultar ms-customer")
            void create_conditionsDoNotMatchAccountType_throwsInvalidAccountConditionsException() {
                // Arrange: SAVINGS exige monthlyMovementLimit y nada mas; aqui se envia maintenanceFee
                CreateAccountDto dto = CreateAccountDto.builder()
                        .customerId(AccountMother.VALID_CUSTOMER_ID)
                        .type(AccountTypeEnum.SAVINGS)
                        .monthlyMovementLimit(null)
                        .maintenanceFee(new BigDecimal("10.00"))
                        .movementDayOfMonth(null)
                        .holders(List.of())
                        .signers(List.of())
                        .build();

                // Act
                TestObserver<Account> testObserver = createAccountService.create(dto).test();

                // Assert
                testObserver.assertError(InvalidAccountConditionsException.class);
                verify(customerClientPort, never()).findCustomerById(anyString());
            }

            @Test
            @DisplayName("create - cliente inexistente en ms-customer - propaga CustomerNotFoundException")
            void create_customerNotFound_propagatesCustomerNotFoundException() {
                // Arrange
                CreateAccountDto dto = AccountMother.createSavingsAccountDto("missing-customer");
                when(customerClientPort.findCustomerById("missing-customer"))
                        .thenReturn(Single.error(new CustomerNotFoundException("missing-customer")));

                // Act
                TestObserver<Account> testObserver = createAccountService.create(dto).test();

                // Assert
                testObserver.assertError(CustomerNotFoundException.class);
                verify(accountRepositoryPort, never()).save(any());
            }

            @Test
            @DisplayName("create - ms-customer no disponible - propaga CustomerServiceUnavailableException")
            void create_customerServiceUnavailable_propagatesCustomerServiceUnavailableException() {
                // Arrange
                CreateAccountDto dto = AccountMother.createSavingsAccountDto(AccountMother.VALID_CUSTOMER_ID);
                when(customerClientPort.findCustomerById(AccountMother.VALID_CUSTOMER_ID))
                        .thenReturn(Single.error(new CustomerServiceUnavailableException("timeout")));

                // Act
                TestObserver<Account> testObserver = createAccountService.create(dto).test();

                // Assert
                testObserver.assertError(CustomerServiceUnavailableException.class);
            }

            @Test
            @DisplayName("create - tipo de cuenta no ofrecido al tipo de cliente "
                    + "- lanza AccountTypeNotAllowedException sin guardar")
            void create_accountTypeNotAllowedForCustomerType_throwsAccountTypeNotAllowedExceptionWithoutSaving() {
                // Arrange: SAVINGS solo se ofrece a PERSONAL, aqui el cliente es BUSINESS
                CreateAccountDto dto = AccountMother.createSavingsAccountDto("cust-002");
                when(customerClientPort.findCustomerById("cust-002"))
                        .thenReturn(Single.just(AccountMother.businessCustomerInfo("cust-002")));

                // Act
                TestObserver<Account> testObserver = createAccountService.create(dto).test();

                // Assert
                testObserver.assertError(AccountTypeNotAllowedException.class);
                verify(accountRepositoryPort, never()).save(any());
            }

            @Test
            @DisplayName("create - titulares invalidos para el tipo de cliente "
                    + "- lanza InvalidAccountParticipantsException sin guardar")
            void create_invalidParticipantsForCustomerType_throwsInvalidAccountParticipantsExceptionWithoutSaving() {
                // Arrange: BUSINESS exige al menos un titular, aqui se envia la lista vacia
                CreateAccountDto dto = AccountMother.createCheckingAccountDto("cust-002", List.of());
                when(customerClientPort.findCustomerById("cust-002"))
                        .thenReturn(Single.just(AccountMother.businessCustomerInfo("cust-002")));

                // Act
                TestObserver<Account> testObserver = createAccountService.create(dto).test();

                // Assert
                testObserver.assertError(InvalidAccountParticipantsException.class);
                verify(accountRepositoryPort, never()).save(any());
            }

            @Test
            @DisplayName("create - cliente ya alcanzo el maximo de cuentas de ese tipo "
                    + "- lanza AccountLimitExceededException sin guardar")
            void create_accountLimitAlreadyReached_throwsAccountLimitExceededExceptionWithoutSaving() {
                // Arrange: SAVINGS permite como maximo 1 cuenta activa por cliente personal
                CreateAccountDto dto = AccountMother.createSavingsAccountDto(AccountMother.VALID_CUSTOMER_ID);
                when(customerClientPort.findCustomerById(AccountMother.VALID_CUSTOMER_ID))
                        .thenReturn(Single.just(AccountMother.personalCustomerInfo(AccountMother.VALID_CUSTOMER_ID)));
                when(accountRepositoryPort.countActiveByCustomerIdAndType(
                        AccountMother.VALID_CUSTOMER_ID, AccountTypeEnum.SAVINGS))
                        .thenReturn(Single.just(1L));

                // Act
                TestObserver<Account> testObserver = createAccountService.create(dto).test();

                // Assert
                testObserver.assertError(AccountLimitExceededException.class);
                verify(accountRepositoryPort, never()).save(any());
            }
        }

        @Nested
        @Tag("unexpected-case")
        @DisplayName("Casos inesperados")
        class UnexpectedCases {

            @Test
            @DisplayName("create - el conteo de cuentas activas falla con error no declarado "
                    + "- propaga el error sin guardar")
            void create_countActiveAccountsFailsWithUnexpectedError_propagatesErrorWithoutSaving() {
                // Arrange
                CreateAccountDto dto = AccountMother.createSavingsAccountDto(AccountMother.VALID_CUSTOMER_ID);
                RuntimeException databaseError = new RuntimeException("Mongo connection lost");
                when(customerClientPort.findCustomerById(AccountMother.VALID_CUSTOMER_ID))
                        .thenReturn(Single.just(AccountMother.personalCustomerInfo(AccountMother.VALID_CUSTOMER_ID)));
                when(accountRepositoryPort.countActiveByCustomerIdAndType(
                        AccountMother.VALID_CUSTOMER_ID, AccountTypeEnum.SAVINGS))
                        .thenReturn(Single.error(databaseError));

                // Act
                TestObserver<Account> testObserver = createAccountService.create(dto).test();

                // Assert
                testObserver.assertError(databaseError);
                verify(accountRepositoryPort, never()).save(any());
            }

            @Test
            @DisplayName("create - el guardado falla con error no declarado - propaga el error")
            void create_saveFailsWithUnexpectedError_propagatesError() {
                // Arrange
                CreateAccountDto dto = AccountMother.createSavingsAccountDto(AccountMother.VALID_CUSTOMER_ID);
                RuntimeException databaseError = new RuntimeException("Mongo write timeout");
                when(customerClientPort.findCustomerById(AccountMother.VALID_CUSTOMER_ID))
                        .thenReturn(Single.just(AccountMother.personalCustomerInfo(AccountMother.VALID_CUSTOMER_ID)));
                when(accountRepositoryPort.countActiveByCustomerIdAndType(
                        AccountMother.VALID_CUSTOMER_ID, AccountTypeEnum.SAVINGS))
                        .thenReturn(Single.just(0L));
                when(accountRepositoryPort.save(any(Account.class))).thenReturn(Single.error(databaseError));

                // Act
                TestObserver<Account> testObserver = createAccountService.create(dto).test();

                // Assert
                testObserver.assertError(databaseError);
            }
        }
    }
}
