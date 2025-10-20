package bg.tuvarna.devicebackend.models.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RenovationCreateVO(
        @NotBlank(message = "Device serial number is required")
        String deviceSerialNumber,
        @NotBlank(message = "Description is required")
        String description,
        @NotNull(message = "Renovation date is required")
        LocalDate renovationDate
) {
}