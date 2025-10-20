package bg.tuvarna.devicebackend.services;

import bg.tuvarna.devicebackend.controllers.exceptions.CustomException;
import bg.tuvarna.devicebackend.models.dtos.PassportCreateVO;
import bg.tuvarna.devicebackend.models.dtos.PassportUpdateVO;
import bg.tuvarna.devicebackend.models.dtos.PassportVO;
import bg.tuvarna.devicebackend.models.entities.Passport;
import bg.tuvarna.devicebackend.models.mappers.PassportMapper;
import bg.tuvarna.devicebackend.repositories.PassportRepository;
import bg.tuvarna.devicebackend.utils.CustomPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PassportServiceTests {
    @Mock
    private PassportRepository passportRepository;
    @InjectMocks
    private PassportService passportService;

    private static Passport passport(Long id, String prefix, int from, int to, int months) {
        return Passport.builder()
                .id(id)
                .name("N" + id)
                .model("M" + id)
                .serialPrefix(prefix)
                .fromSerialNumber(from)
                .toSerialNumber(to)
                .warrantyMonths(months)
                .build();
    }

    @Test
    @DisplayName("save(create): no overlaps → toEntity + save")
    void save_create_ok() {
        PassportCreateVO req = new PassportCreateVO("ime", "model", "SN-", 24, 100, 199);

        when(passportRepository.findByFromSerialNumberBetween("SN-", 100, 199))
                .thenReturn(List.of()); // no overlap

        Passport mapped = passport(null, "SN-", 100, 199, 24);

        try (MockedStatic<PassportMapper> ms = mockStatic(PassportMapper.class)) {
            ms.when(() -> PassportMapper.toEntity(req)).thenReturn(mapped);

            passportService.create(req);

            verify(passportRepository).save(mapped);
            ms.verify(() -> PassportMapper.toEntity(req));
        }
    }

    @Test
    @DisplayName("save(create): overlap exists → throws AlreadyExists")
    void save_create_overlap_throws() {
        PassportCreateVO req = new PassportCreateVO("ime", "model", "SN-", 12, 150, 250);
        when(passportRepository.findByFromSerialNumberBetween("SN-", 150, 250))
                .thenReturn(List.of(passport(1L, "SN-", 150, 250, 12)));

        assertThatThrownBy(() -> passportService.create(req))
                .isInstanceOf(CustomException.class)
                .hasMessage("Serial number already exists");

        verify(passportRepository, never()).save(any());
    }

    @Test
    @DisplayName("save(update): overlaps only with itself → updateEntity + save")
    void save_update_ok_noConflict() {
        Passport existing = passport(10L, "SN-", 100, 199, 24);
        PassportUpdateVO req = new PassportUpdateVO("ime", "model", "SN-", 24, 100, 199);

        when(passportRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(passportRepository.findByFromSerialNumberBetween("SN-", 100, 199))
                .thenReturn(List.of(existing));

        try (MockedStatic<PassportMapper> ms = mockStatic(PassportMapper.class)) {
            ms.when(() -> PassportMapper.updateEntity(existing, req)).then(inv -> null);

            passportService.update(10L, req);

            verify(passportRepository).save(existing);
            ms.verify(() -> PassportMapper.updateEntity(existing, req));
        }
    }

    @Test
    @DisplayName("save(update): overlap with another passport id → throws AlreadyExists")
    void save_update_conflict_throws() {
        Passport existing = passport(10L, "SN-", 100, 199, 24);
        Passport other = passport(11L, "SN-", 180, 260, 12);
        PassportCreateVO req = new PassportCreateVO("ime", "model", "SN-", 24, 150, 250);

        when(passportRepository.findByFromSerialNumberBetween("SN-", 150, 250))
                .thenReturn(List.of(other));

        assertThatThrownBy(() -> passportService.create(req))
                .isInstanceOf(CustomException.class)
                .hasMessage("Serial number already exists");

        verify(passportRepository, never()).save(any());
    }

    @Test
    void findPassportById_present_and_null() {
        when(passportRepository.findById(1L)).thenReturn(Optional.of(passport(1L,"SN-",0,1,12)));
        when(passportRepository.findById(2L)).thenReturn(Optional.empty());

        assertThat(passportService.findPassportById(1L)).isNotNull();
        assertThat(passportService.findPassportById(2L)).isNull();
    }

    @Test
    @DisplayName("findPassportBySerialId: returns passport with matching prefix and serial number in range")
    void findPassportBySerialId_happy() {
        Passport p1 = passport(1L, "SN-", 100, 199, 12);
        Passport p2 = passport(2L, "SN-", 200, 299, 12);
        when(passportRepository.findByFromSerial("SN-150")).thenReturn(List.of(p1, p2));

        Passport result = passportService.findPassportBySerialId("SN-150");

        assertThat(result).isSameAs(p1);
    }

    @Test
    @DisplayName("findPassportBySerialId: skips when tail not numeric; throws if no valid passport found")
    void findPassportBySerialId_nonNumericTail_throws() {
        Passport p1 = passport(1L, "SN-", 100, 199, 12);
        when(passportRepository.findByFromSerial("SN-ABC")).thenReturn(List.of(p1));

        assertThatThrownBy(() -> passportService.findPassportBySerialId("SN-ABC"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("Passport not found for serial number: SN-ABC");
    }

    @Test
    @DisplayName("findPassportBySerialId: no prefix match or out-of-range → throws")
    void findPassportBySerialId_noMatch_throws() {
        when(passportRepository.findByFromSerial("XYZ-999")).thenReturn(List.of());

        assertThatThrownBy(() -> passportService.findPassportBySerialId("XYZ-999"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("Passport not found for serial number: XYZ-999");

        Passport p = passport(1L, "SN-", 100, 199, 12);
        when(passportRepository.findByFromSerial("SN-050")).thenReturn(List.of(p));

        assertThatThrownBy(() -> passportService.findPassportBySerialId("SN-050"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("Passport not found for serial number: SN-050");
    }

    @Test
    @DisplayName("getPassports: maps CustomPage correctly")
    void getPassports_mapsCorrectly() {
        Page<Passport> page = new PageImpl<>(
                List.of(passport(1L,"SN-",100,199,12), passport(2L,"AB",0,50,6)),
                PageRequest.of(1, 5),
                12
        );

        when(passportRepository.findAll(any(PageRequest.class))).thenReturn(page);

        CustomPage<Passport> result = passportService.getPassports(2, 5);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);

        verify(passportRepository).findAll(captor.capture());
        PageRequest usedRequest = captor.getValue();

        assertThat(usedRequest.getPageNumber()).isEqualTo(1);
        assertThat(usedRequest.getPageSize()).isEqualTo(5);
        assertThat(result.getCurrentPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getTotalItems()).isEqualTo(12);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getItems()).hasSize(2);
    }

    @Test
    void getPassportsBySerialPrefix_passthrough() {
        List<Passport> list = List.of(passport(1L,"SN-",100,199,12));
        when(passportRepository.findByFromSerial("SN-123")).thenReturn(list);

        assertThat(passportService.getPassportsBySerialPrefix("SN-123")).isSameAs(list);
    }

    @Test
    @DisplayName("delete: delegates to repository")
    void delete_ok() {
        passportService.delete(7L);
        verify(passportRepository).deleteById(7L);
    }

    @Test
    @DisplayName("delete: runtime → wraps in CustomException('Can't delete passport')")
    void delete_runtime_throwsCustom() {
        doThrow(new RuntimeException("constraint")).when(passportRepository).deleteById(7L);

        assertThatThrownBy(() -> passportService.delete(7L))
                .isInstanceOf(CustomException.class)
                .hasMessage("Can't delete passport");
    }
}
