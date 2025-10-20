package bg.tuvarna.devicebackend.models.dtos;

import bg.tuvarna.devicebackend.models.entities.Passport;


public record PassportVO(
        Long id,
        String name,
        String model,
        String serialPrefix,
        int fromSerialNumber,
        int toSerialNumber,
        int warrantyMonths
) {
    public PassportVO(Passport passport) {
        this(
                passport.getId(),
                passport.getName(),
                passport.getModel(),
                passport.getSerialPrefix(),
                passport.getFromSerialNumber(),
                passport.getToSerialNumber(),
                passport.getWarrantyMonths()
        );
    }
}