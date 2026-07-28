package de.toengi.cili.dto.customer;

import de.toengi.cili.model.enums.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CustomerDto(
    Long id,
    String name,
    String firstName,
    String email,
    String mobilePhone,
    LocalDate birthDate,
    Integer memberId,
    Gender gender,
    Boolean informalAddress,
    Long sponsorUserId,
    boolean consentGranted,
    LocalDateTime consentGrantedAt,
    LocalDateTime consentRevokedAt,
    LocalDateTime createdAt
) {}
