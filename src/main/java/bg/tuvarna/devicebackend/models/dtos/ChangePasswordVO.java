package bg.tuvarna.devicebackend.models.dtos;

import bg.tuvarna.devicebackend.annotations.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordVO(
        @NotBlank(message = "Old password is required")
        String oldPassword,
        @ValidPassword(
                message = "Password must be at least 8 characters long, contain at least one uppercase letter, " +
                        "one lowercase letter, one digit, and one special character"
        )
        @NotBlank(message = "New password is required")
        String newPassword
) {
}