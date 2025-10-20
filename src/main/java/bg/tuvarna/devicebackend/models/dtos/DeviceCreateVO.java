package bg.tuvarna.devicebackend.models.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DeviceCreateVO(
        @NotBlank(message = "Device serial number is required")
        String deviceSerialNumber,
        @NotNull(message = "Device purchase date is required")
        LocalDate purchaseDate
) {
}