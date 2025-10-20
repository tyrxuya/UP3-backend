package bg.tuvarna.devicebackend.services;

import bg.tuvarna.devicebackend.controllers.exceptions.CustomException;
import bg.tuvarna.devicebackend.controllers.exceptions.ErrorCode;
import bg.tuvarna.devicebackend.models.dtos.RenovationCreateVO;
import bg.tuvarna.devicebackend.models.dtos.UserCreateVO;
import bg.tuvarna.devicebackend.models.entities.Device;
import bg.tuvarna.devicebackend.models.entities.Renovation;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.repositories.RenovationRepository;
import bg.tuvarna.devicebackend.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public class RenovationServiceTests {
    @Mock
    private RenovationRepository renovationRepository;
    @Mock
    private DeviceService deviceService;

    @InjectMocks
    private RenovationService renovationService;

    private static Device device(String sn) {
        Device d = new Device();
        d.setSerialNumber(sn);
        return d;
    }

    @Test
    @DisplayName("save: when device exists → maps fields and persists renovation")
    void save_ok_persistsRenovation() {
        RenovationCreateVO vo = mock(RenovationCreateVO.class);
        when(vo.deviceSerialNumber()).thenReturn("SN-OK-001");
        when(vo.description()).thenReturn("Changed motor");
        when(vo.renovationDate()).thenReturn(LocalDate.of(2025, 3, 15));

        Device found = device("SN-OK-001");
        when(deviceService.isDeviceExists("SN-OK-001")).thenReturn(found);

        ArgumentCaptor<Renovation> renCaptor = ArgumentCaptor.forClass(Renovation.class);

        renovationService.save(vo);

        verify(renovationRepository).save(renCaptor.capture());
        Renovation saved = renCaptor.getValue();

        assertThat(saved.getDevice()).isSameAs(found);
        assertThat(saved.getDescription()).isEqualTo("Changed motor");
        assertThat(saved.getRenovationDate()).isEqualTo(LocalDate.of(2025, 3, 15));
    }

    @Test
    @DisplayName("save: when deviceService says not registered → throws and does not save")
    void save_deviceMissing_throws_noSave() {
        RenovationCreateVO vo = mock(RenovationCreateVO.class);
        when(vo.deviceSerialNumber()).thenReturn("SN-MISS-001");

        when(deviceService.isDeviceExists("SN-MISS-001"))
                .thenThrow(new CustomException("Device not registered", ErrorCode.NotRegistered));

        assertThatThrownBy(() -> renovationService.save(vo))
                .isInstanceOf(CustomException.class)
                .hasMessage("Device not registered");

        verify(renovationRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: allows null description/date (service passes values through as-is)")
    void save_allowsNulls_passThrough() {
        RenovationCreateVO vo = mock(RenovationCreateVO.class);
        when(vo.deviceSerialNumber()).thenReturn("SN-NULLS");
        when(vo.description()).thenReturn(null);
        when(vo.renovationDate()).thenReturn(null);

        Device found = device("SN-NULLS");
        when(deviceService.isDeviceExists("SN-NULLS")).thenReturn(found);

        ArgumentCaptor<Renovation> renCaptor = ArgumentCaptor.forClass(Renovation.class);

        renovationService.save(vo);

        verify(renovationRepository).save(renCaptor.capture());
        Renovation saved = renCaptor.getValue();

        assertThat(saved.getDevice()).isSameAs(found);
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getRenovationDate()).isNull();
    }

    @Test
    @DisplayName("save: when repository.save throws, the exception propagates and nothing is swallowed")
    void save_repoThrows_propagates() {
        RenovationCreateVO vo = mock(RenovationCreateVO.class);
        when(vo.deviceSerialNumber()).thenReturn("SN-ERR");
        when(vo.description()).thenReturn("desc");
        when(vo.renovationDate()).thenReturn(LocalDate.now());

        when(deviceService.isDeviceExists("SN-ERR")).thenReturn(new Device());
        doThrow(new RuntimeException("DB down")).when(renovationRepository).save(any(Renovation.class));

        assertThatThrownBy(() -> renovationService.save(vo))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB down");
    }

    @Test
    @DisplayName("save: empty-string description is allowed and saved as-is")
    void save_allowsEmptyDescription() {
        RenovationCreateVO vo = mock(RenovationCreateVO.class);
        when(vo.deviceSerialNumber()).thenReturn("SN-EMPTY");
        when(vo.description()).thenReturn("");
        when(vo.renovationDate()).thenReturn(LocalDate.of(2025, 1, 1));

        Device d = new Device(); d.setSerialNumber("SN-EMPTY");
        when(deviceService.isDeviceExists("SN-EMPTY")).thenReturn(d);

        ArgumentCaptor<Renovation> captor = ArgumentCaptor.forClass(Renovation.class);

        renovationService.save(vo);

        verify(renovationRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEmpty();
    }

    @Test
    @DisplayName("save: repository.save is called exactly once")
    void save_callsRepoOnce() {
        RenovationCreateVO vo = mock(RenovationCreateVO.class);
        when(vo.deviceSerialNumber()).thenReturn("SN-ONCE");
        when(deviceService.isDeviceExists("SN-ONCE")).thenReturn(new Device());

        renovationService.save(vo);

        verify(renovationRepository, times(1)).save(any(Renovation.class));
        verifyNoMoreInteractions(renovationRepository);
    }
}