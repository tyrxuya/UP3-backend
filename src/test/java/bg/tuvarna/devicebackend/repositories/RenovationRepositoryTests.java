package bg.tuvarna.devicebackend.repositories;

import bg.tuvarna.devicebackend.models.entities.Device;
import bg.tuvarna.devicebackend.models.entities.Renovation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
public class RenovationRepositoryTests {
    @Autowired private RenovationRepository renovationRepository;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private EntityManager entityManager;

    private Device deviceA;

    @BeforeEach
    void setUp() {
        renovationRepository.deleteAll();
        deviceRepository.deleteAll();

        deviceA = new Device();
        deviceA.setSerialNumber("SN-REN-001");
        deviceA.setPurchaseDate(LocalDate.of(2024, 1, 1));
        deviceA.setWarrantyExpirationDate(LocalDate.of(2026, 1, 1));
        deviceA.setComment("Device A");
        deviceA = deviceRepository.save(deviceA);
    }

    private static Renovation renovation(String desc, LocalDate date, Device d) {
        Renovation r = new Renovation();
        r.setDescription(desc);
        r.setRenovationDate(date);
        r.setDevice(d);
        return r;
    }

    @Test
    @DisplayName("save + findById: persists and reads a renovation linked to a device")
    void save_and_findById() {
        Renovation r = renovation("Changed motor", LocalDate.of(2025, 3, 15), deviceA);
        r = renovationRepository.save(r);

        assertThat(r.getId()).isNotNull();

        Renovation persisted = renovationRepository.findById(r.getId()).orElseThrow();
        assertThat(persisted.getDescription()).isEqualTo("Changed motor");
        assertThat(persisted.getDevice().getSerialNumber()).isEqualTo("SN-REN-001");
    }

    @Test
    @DisplayName("update: modifies description and persists changes")
    void update_description() {
        Renovation r = renovationRepository.save(renovation("Init", LocalDate.of(2025, 2, 1), deviceA));

        r.setDescription("Updated description");
        renovationRepository.save(r);

        Renovation after = renovationRepository.findById(r.getId()).orElseThrow();
        assertThat(after.getDescription()).isEqualTo("Updated description");
    }

    @Test
    @DisplayName("deleteById: removes a single renovation")
    void deleteById_removesOne() {
        Renovation r1 = renovationRepository.save(renovation("R1", LocalDate.now(), deviceA));
        Renovation r2 = renovationRepository.save(renovation("R2", LocalDate.now(), deviceA));

        long before = renovationRepository.count();
        renovationRepository.deleteById(r1.getId());
        long after = renovationRepository.count();

        assertThat(before).isEqualTo(2);
        assertThat(after).isEqualTo(1);
        assertThat(renovationRepository.findById(r1.getId())).isEmpty();
        assertThat(renovationRepository.findById(r2.getId())).isPresent();
    }

    @Test
    @DisplayName("findAll(Pageable): supports pagination")
    void findAll_pagination() {
        for (int i = 0; i < 7; i++) {
            renovationRepository.save(renovation("R" + i, LocalDate.of(2025, 1, i + 1), deviceA));
        }

        Page<Renovation> page0 = renovationRepository.findAll(PageRequest.of(0, 3));
        Page<Renovation> page1 = renovationRepository.findAll(PageRequest.of(1, 3));

        assertThat(page0.getContent()).hasSize(3);
        assertThat(page1.getContent()).hasSize(3);
        assertThat(page0.getTotalElements()).isEqualTo(7);
        assertThat(page0.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("save: allows null device (FK nullable)")
    void save_allowsNullDevice() {
        Renovation r = renovation("Standalone", LocalDate.of(2025, 5, 5), null);
        Renovation saved = renovationRepository.save(r);

        assertThat(saved.getId()).isNotNull();
        Renovation fetched = renovationRepository.findById(saved.getId()).orElseThrow();
        assertThat(fetched.getDevice()).isNull();
    }

    @Test
    @DisplayName("save: with unmanaged Device reference fails (ManyToOne has no cascade)")
    void save_withUnmanagedDevice_shouldFail() {
        Device unmanaged = new Device();
        unmanaged.setSerialNumber("SN-UNMANAGED");

        Renovation r = renovation("Attach to unmanaged", LocalDate.now(), unmanaged);

        // Depending on provider, either PersistenceException or InvalidDataAccessApiUsageException may surface
        assertThatThrownBy(() -> renovationRepository.saveAndFlush(r))
                .isInstanceOfAny(PersistenceException.class, InvalidDataAccessApiUsageException.class);
    }

    @Test
    @DisplayName("Cascade remove: deleting Device cascades delete to its renovations (cascade=ALL on Device)")
    void deleteDevice_cascadesToRenovations() {
        Renovation r1 = renovation("R1", LocalDate.now(), deviceA);
        Renovation r2 = renovation("R2", LocalDate.now(), deviceA);

        deviceA.getRenovations().add(r1);
        deviceA.getRenovations().add(r2);

        renovationRepository.save(r1);
        renovationRepository.save(r2);
        entityManager.flush();

        assertThat(renovationRepository.count()).isEqualTo(2);

        deviceRepository.delete(deviceA);
        entityManager.flush();
        entityManager.clear();

        assertThat(renovationRepository.count()).isZero();
    }

    @Test
    @DisplayName("orphanRemoval: removing renovation from device.renovations deletes it")
    void orphanRemoval_removingFromCollectionDeletesChild() {
        Renovation r = renovation("R-orphan", LocalDate.now(), deviceA);

        deviceA.getRenovations().add(r);
        renovationRepository.save(r);
        entityManager.flush();
        assertThat(renovationRepository.count()).isEqualTo(1);

        deviceA.getRenovations().remove(r);
        r.setDevice(null);
        deviceRepository.saveAndFlush(deviceA);
        entityManager.clear();

        assertThat(renovationRepository.count()).isZero();
    }
}