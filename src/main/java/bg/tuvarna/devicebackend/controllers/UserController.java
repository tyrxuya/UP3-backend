package bg.tuvarna.devicebackend.controllers;

import bg.tuvarna.devicebackend.controllers.exceptions.ErrorResponse;
import bg.tuvarna.devicebackend.models.dtos.*;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.services.UserService;
import bg.tuvarna.devicebackend.utils.CustomPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@AllArgsConstructor
public class UserController {
    private final UserService userService;

    @Operation(
            summary = "Register user.",
            description = "Body of user should have an unique and not blank email and phone number, " +
                    "password should not be blank as well."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully, register the profile."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Email already registered or phone already registered.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping("/registration")
    public ResponseEntity<Void> registration(@RequestBody @Valid UserCreateVO userRegistration) {
        userService.register(userRegistration);

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Login user.",
            description = "User can log in with email/phone and password. " +
                    "If user is already logged in, it will return the token and user details."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully logged in.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponseDTO.class)
                    )
            )
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> userLogin(
            @RequestBody @Valid UserLoginDTO dto,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Returns users.",
            description = "Returns users based on search."
    )
    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CustomPage<UserListing>> getUsers(
            @RequestParam(required = false) String searchBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(userService.getUsers(searchBy, page, size));
    }

    @Operation(
            summary = "Update user.",
            description = "Update user."
    )
    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserVO> updateUser(
            @PathVariable Long id,
            @RequestBody @Valid UserUpdateVO userUpdateVO
    ) {
        User updatedUser = userService.updateUser(id, userUpdateVO);

        return ResponseEntity.ok(new UserVO(updatedUser));
    }

    @Operation(
            summary = "Update password.",
            description = "Update password."
    )
    @PutMapping("/{id}/changePassword")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> changePassword(
            @PathVariable Long id,
            @RequestBody @Valid ChangePasswordVO changePasswordVO
    ) {
        userService.updatePassword(id, changePasswordVO);

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Login user.",
            description = "it will returns the user details."
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully logged in.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UserVO.class)
                            )
                    )
            }
    )
    @GetMapping("/getUser")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserVO> getUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new UserVO(user));
    }
}