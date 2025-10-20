package bg.tuvarna.devicebackend.models.dtos;

import bg.tuvarna.devicebackend.annotations.ValidEmail;
import bg.tuvarna.devicebackend.annotations.ValidPassword;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UserCreateVO(
        @NotBlank(message = "Full name is required")
        String fullName,
        @ValidPassword
        @NotBlank(message = "Password is required")
        String password,
        @ValidEmail
        @NotBlank(message = "Email is required")
        String email,
        @NotBlank(message = "Phone number is required")
        String phone,
        String address,
        LocalDate purchaseDate,
        String deviceSerialNumber
) {
}