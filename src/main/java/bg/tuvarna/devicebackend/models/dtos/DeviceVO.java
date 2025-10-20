package bg.tuvarna.devicebackend.models.dtos;

import bg.tuvarna.devicebackend.models.entities.Device;

import java.time.LocalDate;
import java.util.List;

public record DeviceVO(
        String serialNumber,
        LocalDate purchaseDate,
        LocalDate warrantyExpirationDate,
        String comment,
        UserVO user,
        PassportVO passport,
        List<RenovationVO> renovations
) {
    public DeviceVO(Device device) {
        this(
                device.getSerialNumber(),
                device.getPurchaseDate(),
                device.getWarrantyExpirationDate(),
                device.getComment(),
                device.getUser() != null ? new UserVO(device.getUser(), false) : null,
                new PassportVO(device.getPassport()),
                device.getRenovations().stream().map(RenovationVO::new).toList()
        );
    }

    public DeviceVO(Device device, boolean loadUser) {
        this(
                device.getSerialNumber(),
                device.getPurchaseDate(),
                device.getWarrantyExpirationDate(),
                device.getComment(),
                loadUser && device.getUser() != null ? new UserVO(device.getUser(), false) : null,
                new PassportVO(device.getPassport()),
                device.getRenovations().stream().map(RenovationVO::new).toList()
        );
    }
}