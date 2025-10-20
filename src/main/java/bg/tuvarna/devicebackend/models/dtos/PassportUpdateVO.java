package bg.tuvarna.devicebackend.models.dtos;

public record PassportUpdateVO(
        String name,
        String model,
        String serialPrefix,
        Integer warrantyMonths,
        Integer fromSerialNumber,
        Integer toSerialNumber
) {
}

