package bg.tuvarna.devicebackend.models.dtos;

import bg.tuvarna.devicebackend.models.entities.Renovation;

import java.time.LocalDate;

public record RenovationVO(
        Long id,
        String description,
        LocalDate renovationDate,
        DeviceVO device
) {
    public RenovationVO(Renovation renovation){
        this(
                renovation.getId(),
                renovation.getDescription(),
                renovation.getRenovationDate(),
                new DeviceVO(renovation.getDevice())
        );
    }
}