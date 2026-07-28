package de.toengi.cili.dto.customer;

import de.toengi.cili.model.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateCustomerRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 100) String firstName,
    @NotBlank @Email @Size(max = 255) String email,
    @Size(max = 50) String mobilePhone,
    @Past LocalDate birthDate,
    @Min(100000) @Max(999999) Integer memberId,
    Gender gender,
    Boolean informalAddress
) {}
