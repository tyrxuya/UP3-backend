package bg.tuvarna.devicebackend.models.mappers;

import bg.tuvarna.devicebackend.models.dtos.PassportCreateVO;
import bg.tuvarna.devicebackend.models.dtos.PassportUpdateVO;
import bg.tuvarna.devicebackend.models.entities.Passport;

public class PassportMapper {
    public static Passport toEntity(PassportCreateVO passportCreateVO) {
        return Passport.builder()
                .name(passportCreateVO.name())
                .model(passportCreateVO.model())
                .serialPrefix(passportCreateVO.serialPrefix())
                .warrantyMonths(passportCreateVO.warrantyMonths())
                .fromSerialNumber(passportCreateVO.fromSerialNumber())
                .toSerialNumber(passportCreateVO.toSerialNumber())
                .build();
    }

    public static void updateEntity(Passport passport, PassportUpdateVO passportUpdateVO) {
        if (passportUpdateVO.name() != null && !passportUpdateVO.name().isBlank()) {
            passport.setName(passportUpdateVO.name());
        }

        if (passportUpdateVO.model() != null && !passportUpdateVO.model().isBlank()) {
            passport.setModel(passportUpdateVO.model());
        }

        if (passportUpdateVO.serialPrefix() != null && !passportUpdateVO.serialPrefix().isBlank()) {
            passport.setSerialPrefix(passportUpdateVO.serialPrefix());
        }

        if (passportUpdateVO.warrantyMonths() != null) {
            passport.setWarrantyMonths(passportUpdateVO.warrantyMonths());
        }

        if (passportUpdateVO.fromSerialNumber() != null) {
            passport.setFromSerialNumber(passportUpdateVO.fromSerialNumber());
        }

        if (passportUpdateVO.toSerialNumber() != null) {
            passport.setToSerialNumber(passportUpdateVO.toSerialNumber());
        }
    }
}