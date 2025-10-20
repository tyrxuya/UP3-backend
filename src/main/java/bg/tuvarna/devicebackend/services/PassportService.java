package bg.tuvarna.devicebackend.services;

import bg.tuvarna.devicebackend.controllers.exceptions.CustomException;
import bg.tuvarna.devicebackend.controllers.exceptions.ErrorCode;
import bg.tuvarna.devicebackend.models.dtos.PassportCreateVO;
import bg.tuvarna.devicebackend.models.dtos.PassportUpdateVO;
import bg.tuvarna.devicebackend.models.entities.Passport;
import bg.tuvarna.devicebackend.models.mappers.PassportMapper;
import bg.tuvarna.devicebackend.repositories.PassportRepository;
import bg.tuvarna.devicebackend.utils.CustomPage;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@AllArgsConstructor
public class PassportService {
    private final PassportRepository passportRepository;

    public Passport create(PassportCreateVO passportCreateVO) {
        List<Passport> passports = passportRepository.findByFromSerialNumberBetween(passportCreateVO.serialPrefix(), passportCreateVO.fromSerialNumber(), passportCreateVO.toSerialNumber());
        if (!passports.isEmpty()) {
            throw new CustomException("Serial number already exists", ErrorCode.AlreadyExists);
        }

        Passport passport = PassportMapper.toEntity(passportCreateVO);

        return passportRepository.save(passport);
    }

    public Passport update(Long id, PassportUpdateVO passportUpdateVO) {
        Passport passport = findPassportById(id);

        if (passport == null) {
            throw new CustomException("Passport not found", ErrorCode.EntityNotFound);
        }

        String serialPrefix = passportUpdateVO.serialPrefix() != null ? passportUpdateVO.serialPrefix() : passport.getSerialPrefix();
        int fromSerialNumber = passportUpdateVO.fromSerialNumber() != null ? passportUpdateVO.fromSerialNumber() : passport.getFromSerialNumber();
        int toSerialNumber = passportUpdateVO.toSerialNumber() != null ? passportUpdateVO.toSerialNumber() : passport.getToSerialNumber();

        List<Passport> passports = passportRepository.findByFromSerialNumberBetween(serialPrefix, fromSerialNumber, toSerialNumber);
        if (!passports.isEmpty() && passports.stream().anyMatch(p -> !Objects.equals(p.getId(), passport.getId()))) {
            throw new CustomException("Serial number already exists", ErrorCode.AlreadyExists);
        }

        PassportMapper.updateEntity(passport, passportUpdateVO);

        return passportRepository.save(passport);
    }

    public Passport findPassportById(Long id) {
        return passportRepository.findById(id).orElse(null);
    }

    public Passport findPassportBySerialId(String serialId) {
        List<Passport> passports = getPassportsBySerialPrefix(serialId);
        
        for (Passport passport : passports) {
            int serialNumber;
            try {
                serialNumber = Integer.parseInt(serialId.split(passport.getSerialPrefix())[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (serialNumber >= passport.getFromSerialNumber() && serialNumber <= passport.getToSerialNumber()) {
                return passport;
            }
        }

        throw new CustomException("Passport not found for serial number: " + serialId, ErrorCode.Failed);
    }

    public CustomPage<Passport> getPassports(int page, int size) {
        Page<Passport> passports = passportRepository.findAll(PageRequest.of(page - 1, size));

        CustomPage<Passport> customPage = new CustomPage<>();
        customPage.setTotalPages(passports.getTotalPages());
        customPage.setCurrentPage(passports.getNumber() + 1);
        customPage.setSize(passports.getSize());
        customPage.setTotalItems(passports.getTotalElements());
        customPage.setItems(passports.getContent());

        return customPage;
    }

    public List<Passport> getPassportsBySerialPrefix(String serialId) {
        return passportRepository.findByFromSerial(serialId);
    }

    public void delete(Long id) {
        try {
            passportRepository.deleteById(id);
        } catch (RuntimeException e) {
            throw new CustomException("Can't delete passport", ErrorCode.Failed);
        }
    }
}