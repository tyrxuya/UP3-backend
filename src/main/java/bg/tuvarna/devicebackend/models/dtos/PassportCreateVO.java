package bg.tuvarna.devicebackend.models.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PassportCreateVO(
        @NotBlank(message = "Name is required")
        String name,
        @NotBlank(message = "Model is required")
        String model,
        @NotBlank(message = "Serial prefix is required")
        String serialPrefix,
        @NotNull(message = "Warranty months is required")
        @Min(0)
        Integer warrantyMonths,
        @NotNull(message = "From serial number is required")
        @Min(0)
        Integer fromSerialNumber,
        @NotNull(message = "To serial number is required")
        @Min(0)
        Integer toSerialNumber
) {
}

