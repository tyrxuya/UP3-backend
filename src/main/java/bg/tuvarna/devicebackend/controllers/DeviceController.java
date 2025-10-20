package bg.tuvarna.devicebackend.controllers;

import bg.tuvarna.devicebackend.controllers.exceptions.ErrorResponse;
import bg.tuvarna.devicebackend.models.dtos.DeviceCreateVO;
import bg.tuvarna.devicebackend.models.dtos.DeviceUpdateVO;
import bg.tuvarna.devicebackend.models.dtos.DeviceVO;
import bg.tuvarna.devicebackend.models.entities.Device;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.services.DeviceService;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/devices")
@AllArgsConstructor
public class DeviceController {
    private final DeviceService deviceService;

    @Operation(
            description = "Return device by id for logged in user.",
            summary = "Return device by id"
    )
    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public DeviceVO findDevice(@PathVariable String id) {
        return new DeviceVO(deviceService.findDevice(id));
    }

    @Operation(
            description = "Checks if device exists, which means the user is registered.",
            summary = "Checks if device exists"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Device is registered."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Device not registered.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/exists/{id}")
    public ResponseEntity<DeviceVO> isDeviceExists(@PathVariable String id) {
        return ResponseEntity.ok(new DeviceVO(deviceService.isDeviceExists(id)));
    }

    @Operation(
            description = "Register device for logged in user.",
            summary = "Register device for logged in user"
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully registered device.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = DeviceVO.class)
                            )
                    )
            }
    )
    @PostMapping()
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DeviceVO> addDevice(
            @RequestBody @Valid DeviceCreateVO device,
            @AuthenticationPrincipal User user
    ) {
        Device saved = deviceService.registerNewDevice(device, user);

        return ResponseEntity.created(
                        ServletUriComponentsBuilder
                                .fromCurrentRequest()
                                .path("/{id}")
                                .buildAndExpand(saved.getSerialNumber())
                                .toUri()
                )
                .body(new DeviceVO(saved));
    }

    @Operation(
            summary = "Returns devices.",
            description = "Returns devices based on search."
    )
    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CustomPage<Device>> getDevices(
            @RequestParam(required = false) String searchBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(deviceService.getDevices(searchBy, page, size));
    }

    @Operation(
            description = "Register device for logged in user.",
            summary = "Register device for logged in user"
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Device is registered.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = DeviceVO.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Device already registered.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class)
                            )
                    )
            }
    )
    @PostMapping("/anonymousDevice")
    public ResponseEntity<DeviceVO> addAnonymousDevice(@RequestBody @Valid DeviceCreateVO device) {
        Device saved = deviceService.addAnonymousDevice(device);

        return ResponseEntity.created(
                        ServletUriComponentsBuilder
                                .fromCurrentRequest()
                                .path("/{id}")
                                .buildAndExpand(saved.getSerialNumber())
                                .toUri()
                )
                .body(new DeviceVO(saved));
    }

    @Operation(
            description = "Update device date by admin.",
            summary = "Update device date by admin."
    )
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "Device is registered."),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Device not exist.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class)
                            )
                    )
            }
    )
    @PutMapping("/{serialNumber}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DeviceVO> updateDevice(
            @PathVariable String serialNumber,
            @RequestBody @Valid DeviceUpdateVO device
    ) {
        return ResponseEntity.ok(new DeviceVO(deviceService.updateDevice(serialNumber, device)));
    }

    @Operation(
            description = "Delete device date by admin.",
            summary = "Delete device date by admin."
    )
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "Device is deleted."),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Device has existing renovations.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class)
                            )
                    )
            }
    )
    @DeleteMapping("/{serialNumber}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteDevice(@PathVariable String serialNumber) {
        deviceService.deleteDevice(serialNumber);
        return ResponseEntity.ok().build();
    }
}