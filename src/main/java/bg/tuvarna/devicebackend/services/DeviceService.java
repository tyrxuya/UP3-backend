package bg.tuvarna.devicebackend.services;

import bg.tuvarna.devicebackend.controllers.exceptions.CustomException;
import bg.tuvarna.devicebackend.controllers.exceptions.ErrorCode;
import bg.tuvarna.devicebackend.models.dtos.DeviceCreateVO;
import bg.tuvarna.devicebackend.models.dtos.DeviceUpdateVO;
import bg.tuvarna.devicebackend.models.entities.Device;
import bg.tuvarna.devicebackend.models.entities.Passport;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.repositories.DeviceRepository;
import bg.tuvarna.devicebackend.utils.CustomPage;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@AllArgsConstructor
public class DeviceService {
    private final DeviceRepository deviceRepository;
    private final PassportService passportService;

    public Device registerDevice(String serialId, LocalDate purchaseDate, User user) {
        try {
            Passport passport = passportService.findPassportBySerialId(serialId);

            Device device = new Device();
            device.setSerialNumber(serialId);
            device.setPassport(passport);
            device.setUser(user);
            device.setPurchaseDate(purchaseDate);
            device.setWarrantyExpirationDate(purchaseDate.plusMonths(passport.getWarrantyMonths()).plusMonths(12));

            return deviceRepository.save(device);
        } catch (RuntimeException e) {
            throw new CustomException("Invalid serial number", ErrorCode.Failed);
        }
    }

    public Device findDevice(String id) {
        return deviceRepository.findById(id).orElse(null);
    }

    public Device isDeviceExists(String id) {
        if (!deviceRepository.existsById(id))
            throw new CustomException("Device not registered", ErrorCode.NotRegistered);
        return findDevice(id);
    }

    public Device registerNewDevice(DeviceCreateVO deviceCreateVO, User user) {
        alreadyExist(deviceCreateVO.deviceSerialNumber());

        if (user == null) {
            throw new CustomException("User not found", ErrorCode.EntityNotFound);
        }

        return registerDevice(deviceCreateVO.deviceSerialNumber(), deviceCreateVO.purchaseDate(), user);
    }

    public void alreadyExist(String serialNumber) {
        if (findDevice(serialNumber) != null)
            throw new CustomException("Device already registered", ErrorCode.AlreadyExists);
    }

    public Device updateDevice(String serialNumber, DeviceUpdateVO device) {
        Device deviceToUpdate = deviceRepository.findById(serialNumber).orElseThrow(() -> new CustomException("Device not found", ErrorCode.EntityNotFound));

        deviceToUpdate.setPurchaseDate(device.purchaseDate());

        LocalDate warrantyDate = device.purchaseDate().plusMonths(deviceToUpdate.getPassport().getWarrantyMonths());


        if (deviceToUpdate.getUser() != null) {
            warrantyDate = warrantyDate.plusMonths(12);
        }

        deviceToUpdate.setWarrantyExpirationDate(warrantyDate);
        deviceToUpdate.setComment(device.comment());

        return deviceRepository.save(deviceToUpdate);
    }

    @Transactional
    public void deleteDevice(String serialNumber) {
        try {
            deviceRepository.deleteBySerialNumber(serialNumber);
        } catch (RuntimeException e) {
            throw new CustomException("Cannot delete device: renovations exist", ErrorCode.Failed);
        }
    }

    public Device addAnonymousDevice(DeviceCreateVO device) {
        alreadyExist(device.deviceSerialNumber());
        try {
            Passport passport = passportService.findPassportBySerialId(device.deviceSerialNumber());

            Device deviceToAdd = new Device();
            deviceToAdd.setSerialNumber(device.deviceSerialNumber());
            deviceToAdd.setPurchaseDate(device.purchaseDate());
            deviceToAdd.setPassport(passport);
            deviceToAdd.setWarrantyExpirationDate(device.purchaseDate().plusMonths(passport.getWarrantyMonths()));

            return deviceRepository.save(deviceToAdd);
        } catch (RuntimeException e) {
            throw new CustomException("Invalid serial number", ErrorCode.Failed);
        }
    }

    public CustomPage<Device> getDevices(String searchBy, int page, int size) {
        Page<Device> devicePage;
        if (searchBy == null) {
            devicePage = deviceRepository.getAllDevices(PageRequest.of(page - 1, size));
        } else {
            devicePage = deviceRepository.findAll(searchBy, PageRequest.of(page - 1, size));
        }

        CustomPage<Device> customPage = new CustomPage<>();
        customPage.setItems(devicePage.getContent());
        customPage.setTotalItems(devicePage.getTotalElements());
        customPage.setTotalPages(devicePage.getTotalPages());
        customPage.setCurrentPage(page);
        customPage.setSize(size);

        return customPage;
    }
}