package bg.tuvarna.devicebackend.models.dtos;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DeviceUpdateVO(
        @NotNull(message = "Device purchase date is required")
        LocalDate purchaseDate,
        String comment
) {
}