package com.proyecto_final.ms_account.support;

import com.proyecto_final.ms_account.application.port.in.dto.CreateAccountDto;
import com.proyecto_final.ms_account.application.port.in.dto.UpdateAccountDto;
import com.proyecto_final.ms_account.application.port.out.dto.CustomerInfoDto;
import com.proyecto_final.ms_account.domain.model.*;

import java.time.Instant;
import java.util.List;

/**
 * Object Mother: crea instancias validas y nuevas de Account y sus DTOs para los tests.
 * Cada metodo devuelve una instancia nueva; nunca se comparte una misma referencia entre tests.
 */
public final class AccountMother {

    public static final String VALID_ACCOUNT_ID = "acc-001";

    public static final String VALID_CUSTOMER_ID = "cust-001";

    public static final Instant FIXED_INSTANT = Instant.parse("2026-01-10T10:00:00Z");

    private AccountMother() {
    }

    public static Account activeSavingsAccount() {
        return Account.builder()
                .id(VALID_ACCOUNT_ID)
                .customerId(VALID_CUSTOMER_ID)
                .customerType(CustomerTypeEnum.PERSONAL)
                .type(AccountTypeEnum.SAVINGS)
                .monthlyMovementLimit(10)
                .maintenanceFee(null)
                .movementDayOfMonth(null)
                .holders(List.of())
                .signers(List.of())
                .isActive(true)
                .createdAt(FIXED_INSTANT)
                .updatedAt(FIXED_INSTANT)
                .build();
    }

    public static Account activeCheckingBusinessAccount() {
        return Account.builder()
                .id("acc-002")
                .customerId("cust-002")
                .customerType(CustomerTypeEnum.BUSINESS)
                .type(AccountTypeEnum.CHECKING)
                .monthlyMovementLimit(null)
                .maintenanceFee(new java.math.BigDecimal("15.50"))
                .movementDayOfMonth(null)
                .holders(List.of(samplePerson()))
                .signers(List.of())
                .isActive(true)
                .createdAt(FIXED_INSTANT)
                .updatedAt(FIXED_INSTANT)
                .build();
    }

    public static AccountPerson samplePerson() {
        return new AccountPerson(DocumentTypeEnum.DNI, "12345678", "John Doe");
    }

    public static CustomerInfoDto personalCustomerInfo(String customerId) {
        return new CustomerInfoDto(customerId, CustomerTypeEnum.PERSONAL);
    }

    public static CustomerInfoDto businessCustomerInfo(String customerId) {
        return new CustomerInfoDto(customerId, CustomerTypeEnum.BUSINESS);
    }

    public static CreateAccountDto createSavingsAccountDto(String customerId) {
        return CreateAccountDto.builder()
                .customerId(customerId)
                .type(AccountTypeEnum.SAVINGS)
                .monthlyMovementLimit(10)
                .maintenanceFee(null)
                .movementDayOfMonth(null)
                .holders(List.of())
                .signers(List.of())
                .build();
    }

    public static CreateAccountDto createCheckingAccountDto(String customerId, List<AccountPerson> holders) {
        return CreateAccountDto.builder()
                .customerId(customerId)
                .type(AccountTypeEnum.CHECKING)
                .monthlyMovementLimit(null)
                .maintenanceFee(new java.math.BigDecimal("15.50"))
                .movementDayOfMonth(null)
                .holders(holders)
                .signers(List.of())
                .build();
    }

    public static UpdateAccountDto updateSavingsAccountDto(String accountId) {
        return UpdateAccountDto.builder()
                .accountId(accountId)
                .monthlyMovementLimit(20)
                .maintenanceFee(null)
                .movementDayOfMonth(null)
                .holders(List.of())
                .signers(List.of())
                .build();
    }
}
