package bg.tuvarna.devicebackend.models.dtos;

import bg.tuvarna.devicebackend.annotations.ValidEmail;

public record UserUpdateVO(
        String fullName,
        String address,
        String phone,
        @ValidEmail
        String email
) {
}