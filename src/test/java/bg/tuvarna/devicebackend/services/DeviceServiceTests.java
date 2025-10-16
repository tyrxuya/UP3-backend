package bg.tuvarna.devicebackend.services;

import bg.tuvarna.devicebackend.controllers.execptions.CustomException;
import bg.tuvarna.devicebackend.models.dtos.DeviceCreateVO;
import bg.tuvarna.devicebackend.models.dtos.DeviceUpdateVO;
import bg.tuvarna.devicebackend.models.entities.Device;
import bg.tuvarna.devicebackend.models.entities.Passport;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.models.enums.UserRole;
import bg.tuvarna.devicebackend.repositories.DeviceRepository;
import bg.tuvarna.devicebackend.repositories.UserRepository;
import bg.tuvarna.devicebackend.utils.CustomPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class DeviceServiceTests {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private PassportService passportService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DeviceService deviceService;

    private static User user(Long id, String name, String email) {
        return User.builder()
                .id(id)
                .fullName(name)
                .email(email)
                .phone("+359")
                .address("addr")
                .role(UserRole.USER)
                .password("{enc}")
                .build();
    }

    private static Device device(String sn, LocalDate purchase, int warrantyMonths, User owner) {
        Device d = new Device();
        d.setSerialNumber(sn);
        d.setPurchaseDate(purchase);
        d.setWarrantyExpirationDate(purchase.plusMonths(warrantyMonths));
        d.setUser(owner);
        return d;
    }

    private static Passport passport(int warrantyMonths, String name, String model) {
        return Passport.builder()
                .id(1L)
                .name(name)
                .model(model)
                .serialPrefix("SN-")
                .fromSerialNumber(0)
                .toSerialNumber(9999)
                .warrantyMonths(warrantyMonths)
                .build();
    }

    @Test
    @DisplayName("registerDevice: saves device with passport, user and warranty = purchase + warrantyMonths + 12")
    void registerDevice_ok() {
        String sn = "SN-ABC";
        LocalDate purchase = LocalDate.of(2025, 1, 15);
        User owner = user(5L, "Ivan", "ivan@example.com");
        Passport passport = passport(24, "Cool", "ModelX");

        when(passportService.findPassportBySerialId(sn)).thenReturn(passport);

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);

        deviceService.registerDevice(sn, purchase, owner);

        verify(deviceRepository).save(deviceCaptor.capture());
        Device saved = deviceCaptor.getValue();

        assertThat(saved.getSerialNumber()).isEqualTo(sn);
        assertThat(saved.getPassport()).isEqualTo(passport);
        assertThat(saved.getUser()).isEqualTo(owner);
        assertThat(saved.getWarrantyExpirationDate()).isEqualTo(purchase.plusMonths(36)); // 24 + 12
    }

    @Test
    @DisplayName("registerDevice: maps runtime errors to CustomException('Invalid serial number')")
    void registerDevice_passportLookupFails_mapsToCustomException() {
        when(passportService.findPassportBySerialId("BAD")).thenThrow(new RuntimeException("nope"));

        assertThatThrownBy(() -> deviceService.registerDevice("BAD", LocalDate.now(), user(1L,"u","e")))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid serial number");

        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    @DisplayName("registerDevice: passport with 0 warrantyMonths still adds +12 when user present")
    void registerDevice_zeroWarrantyMonths_stillAddsConsumer12() {
        String sn = "SN-ZERO";
        LocalDate purchase = LocalDate.of(2025, 6, 1);
        User owner = user(1L, "U", "e@x");
        Passport passport = passport(0, "Zero", "None");

        when(passportService.findPassportBySerialId(sn)).thenReturn(passport);

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        deviceService.registerDevice(sn, purchase, owner);

        verify(deviceRepository).save(deviceCaptor.capture());
        Device saved = deviceCaptor.getValue();
        assertThat(saved.getWarrantyExpirationDate()).isEqualTo(purchase.plusMonths(12));
    }

    @Test
    void findDevice_presentAbsent() {
        Device d = device("SN1", LocalDate.now(), 12, null);
        when(deviceRepository.findById("SN1")).thenReturn(Optional.of(d));
        when(deviceRepository.findById("SN2")).thenReturn(Optional.empty());

        assertThat(deviceService.findDevice("SN1")).isSameAs(d);
        assertThat(deviceService.findDevice("SN2")).isNull();
    }

    @Test
    void isDeviceExists_ok() {
        Device d = device("SN1", LocalDate.now(), 12, null);
        when(deviceRepository.existsById("SN1")).thenReturn(true);
        when(deviceRepository.findById("SN1")).thenReturn(Optional.of(d));

        assertThat(deviceService.isDeviceExists("SN1")).isSameAs(d);
    }

    @Test
    void isDeviceExists_notExists_throws() {
        when(deviceRepository.existsById("NOPE")).thenReturn(false);

        assertThatThrownBy(() -> deviceService.isDeviceExists("NOPE"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Device not registered");
    }

    @Test
    void registerNewDevice_ok() {
        when(deviceRepository.findById("SN-X")).thenReturn(Optional.empty());
        User owner = user(10L, "Jane", "j@x");
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(passportService.findPassportBySerialId("SN-X")).thenReturn(passport(18, "Name", "Model"));

        DeviceCreateVO vo = new DeviceCreateVO("SN-X", LocalDate.of(2025, 2, 1), 10L);

        deviceService.registerNewDevice(vo);

        verify(deviceRepository).save(argThat(d ->
                d.getSerialNumber().equals("SN-X") &&
                        d.getUser().equals(owner) &&
                        d.getWarrantyExpirationDate().equals(LocalDate.of(2025,2,1).plusMonths(30)) // 18 + 12
        ));
    }

    @Test
    void registerNewDevice_alreadyExists_throws() {
        when(deviceRepository.findById("SN-X")).thenReturn(Optional.of(new Device()));
        DeviceCreateVO vo = new DeviceCreateVO("SN-X", LocalDate.now(), 10L);

        assertThatThrownBy(() -> deviceService.registerNewDevice(vo))
                .isInstanceOf(CustomException.class)
                .hasMessage("Device already registered");
    }

    @Test
    void registerNewDevice_userNotFound_throws() {
        when(deviceRepository.findById("SN-X")).thenReturn(Optional.empty());
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        DeviceCreateVO vo = new DeviceCreateVO("SN-X", LocalDate.now(), 99L);

        assertThatThrownBy(() -> deviceService.registerNewDevice(vo))
                .isInstanceOf(CustomException.class)
                .hasMessage("User not found");
    }

    @Test
    void alreadyExist_behaviour() {
        when(deviceRepository.findById("EXISTS")).thenReturn(Optional.of(new Device()));
        when(deviceRepository.findById("FREE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.alreadyExist("EXISTS"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Device already registered");

        deviceService.alreadyExist("FREE"); // no exception
    }

    @Test
    @DisplayName("updateDevice: with null user → warranty = purchase + warrantyMonths")
    void updateDevice_nullUser() {
        LocalDate newPurchase = LocalDate.of(2025, 3, 10);
        Device existing = device("SN1", LocalDate.of(2024,1,1), 12, null);
        existing.setPassport(passport(20, "Test", "Model"));
        existing.setComment("old");

        when(deviceRepository.findById("SN1")).thenReturn(Optional.of(existing));

        DeviceUpdateVO vo = new DeviceUpdateVO("SN1", newPurchase, "new comment");

        deviceService.updateDevice(vo);

        assertThat(existing.getWarrantyExpirationDate()).isEqualTo(newPurchase.plusMonths(20));
        assertThat(existing.getComment()).isEqualTo("new comment");
    }

    @Test
    @DisplayName("updateDevice: with user → warranty = purchase + warrantyMonths + 12")
    void updateDevice_withUser() {
        LocalDate newPurchase = LocalDate.of(2025, 4, 5);
        Device existing = device("SN2", LocalDate.of(2024,1,1), 12, user(1L,"U","e@x"));
        existing.setPassport(passport(6, "Test", "Model"));

        when(deviceRepository.findById("SN2")).thenReturn(Optional.of(existing));

        DeviceUpdateVO vo = new DeviceUpdateVO("SN2", newPurchase, "note");

        deviceService.updateDevice(vo);

        assertThat(existing.getWarrantyExpirationDate()).isEqualTo(newPurchase.plusMonths(18));
    }

    @Test
    @DisplayName("updateDevice: allows comment = null")
    void updateDevice_allowsNullComment() {
        Device existing = device("SN3", LocalDate.of(2024,1,1), 12, null);
        existing.setPassport(passport(12, "P", "M"));
        existing.setComment("old");
        when(deviceRepository.findById("SN3")).thenReturn(Optional.of(existing));

        DeviceUpdateVO vo = new DeviceUpdateVO("SN3", LocalDate.of(2025,1,1), null);

        deviceService.updateDevice(vo);

        assertThat(existing.getComment()).isNull();
        assertThat(existing.getWarrantyExpirationDate()).isEqualTo(LocalDate.of(2025,1,1).plusMonths(12));
    }

    @Test
    void updateDevice_notFound_throws() {
        when(deviceRepository.findById("NOPE")).thenReturn(Optional.empty());
        DeviceUpdateVO vo = new DeviceUpdateVO("NOPE", LocalDate.now(), "x");

        assertThatThrownBy(() -> deviceService.updateDevice(vo))
                .isInstanceOf(CustomException.class)
                .hasMessage("Device not found");
    }

    @Test
    void deleteDevice_ok() {
        deviceService.deleteDevice("SN1");
        verify(deviceRepository).deleteBySerialNumber("SN1");
    }

    @Test
    void deleteDevice_repoThrows_mapsToCustom() {
        doThrow(new RuntimeException("constraint")).when(deviceRepository).deleteBySerialNumber("SN1");

        assertThatThrownBy(() -> deviceService.deleteDevice("SN1"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Renovations exits");
    }

    @Test
    @DisplayName("addAnonymousDevice: creates device without user, warranty = purchase + warrantyMonths (no +12)")
    void addAnonymousDevice_ok() {
        when(deviceRepository.findById("SN-A")).thenReturn(Optional.empty());
        when(passportService.findPassportBySerialId("SN-A")).thenReturn(passport(9, "X", "Y"));

        DeviceCreateVO vo = new DeviceCreateVO("SN-A", LocalDate.of(2025, 5, 1), null);

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);

        deviceService.addAnonymousDevice(vo);

        verify(deviceRepository).save(deviceCaptor.capture());
        Device saved = deviceCaptor.getValue();

        assertThat(saved.getUser()).isNull();
        assertThat(saved.getWarrantyExpirationDate()).isEqualTo(LocalDate.of(2025,5,1).plusMonths(9));
    }

    @Test
    void addAnonymousDevice_passportFail_mapsToCustom() {
        when(deviceRepository.findById("BAD")).thenReturn(Optional.empty());
        when(passportService.findPassportBySerialId("BAD")).thenThrow(new RuntimeException("nope"));

        DeviceCreateVO vo = new DeviceCreateVO("BAD", LocalDate.now(), null);

        assertThatThrownBy(() -> deviceService.addAnonymousDevice(vo))
                .isInstanceOf(CustomException.class)
                .hasMessage("Invalid serial number");

        verify(deviceRepository, never()).save(any());
    }

    @Test
    void getDevices_nullSearch_usesGetAllDevices() {
        Page<Device> page = new PageImpl<>(
                List.of(device("SN1", LocalDate.now(), 12, null),
                        device("SN2", LocalDate.now(), 12, null)),
                PageRequest.of(1, 5),
                12
        );

        when(deviceRepository.getAllDevices(any(PageRequest.class))).thenReturn(page);

        CustomPage<Device> result = deviceService.getDevices(null, 2, 5);

        verify(deviceRepository).getAllDevices(argThat(pr -> pr.getPageNumber() == 1 && pr.getPageSize() == 5));
        assertThat(result.getCurrentPage()).isEqualTo(2);
        assertThat(result.getTotalItems()).isEqualTo(12);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getItems()).hasSize(2);
    }

    @Test
    void getDevices_withSearch_usesFindAll() {
        Page<Device> page = new PageImpl<>(
                List.of(device("SN-K", LocalDate.now(), 12, null)),
                PageRequest.of(0, 10),
                1
        );

        when(deviceRepository.findAll(eq("KEEP"), any(PageRequest.class))).thenReturn(page);

        CustomPage<Device> result = deviceService.getDevices("KEEP", 1, 10);

        verify(deviceRepository).findAll(eq("KEEP"), argThat(pr -> pr.getPageNumber() == 0 && pr.getPageSize() == 10));
        assertThat(result.getItems()).hasSize(1);
    }
}
