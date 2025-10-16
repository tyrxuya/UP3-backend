package bg.tuvarna.devicebackend.repositories;

import bg.tuvarna.devicebackend.models.entities.Device;
import bg.tuvarna.devicebackend.models.entities.Passport;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.models.enums.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class DeviceRepositoryTests {

    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    private User john;

    @BeforeEach
    void setUp() {
        deviceRepository.deleteAll();
        userRepository.deleteAll();

        john = userRepository.save(User.builder()
                .fullName("John Doe")
                .address("Varna, Bulgaria")
                .phone("+359888000111")
                .email("john@example.com")
                .password("{noop}pwd")
                .role(UserRole.USER)
                .build());

        User bobi = userRepository.save(User.builder()
                .fullName("Bobi Petrov")
                .address("Plovdiv, Bulgaria")
                .phone("+359888000333")
                .email("bobi@petrov.bg")
                .password("{noop}pwd")
                .role(UserRole.USER)
                .build());

        deviceRepository.save(device("SN-AAA-001", LocalDate.of(2024, 1, 1), john, "first"));
        deviceRepository.save(device("SN-AAA-002", LocalDate.of(2024, 2, 2), john, "second"));
        deviceRepository.save(device("sn-bbb-003", LocalDate.of(2024, 3, 3), bobi, "third")); // lowercase to test case-insensitive search
        deviceRepository.save(device("SN-XYZ-999", LocalDate.of(2024, 4, 4), null, "orphan"));
    }

    private static Device device(String sn, LocalDate purchaseDate, User owner, String comment) {
        Device d = new Device();
        d.setSerialNumber(sn);
        d.setPurchaseDate(purchaseDate);
        d.setWarrantyExpirationDate(purchaseDate.plusYears(2));
        d.setComment(comment);
        d.setUser(owner);
        return d;
    }

    @Test
    @DisplayName("deleteBySerialNumber removes the device when it exists")
    void deleteBySerialNumber_existing() {
        long before = deviceRepository.count();
        deviceRepository.deleteBySerialNumber("SN-AAA-002");
        long after = deviceRepository.count();

        entityManager.clear();

        assertThat(before).isEqualTo(4);
        assertThat(after).isEqualTo(3);
        assertThat(deviceRepository.findById("SN-AAA-002")).isEmpty();
    }

    @Test
    @DisplayName("deleteBySerialNumber on a non-existent SN is a no-op")
    void deleteBySerialNumber_nonExisting_noop() {
        long before = deviceRepository.count();
        deviceRepository.deleteBySerialNumber("NOPE-000");
        long after = deviceRepository.count();

        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("findAll(null, pageable) returns all devices (no filter)")
    void findAll_null_returnsAll() {
        Page<Device> page = deviceRepository.findAll(
                (String) null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "serialNumber"))
        );

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent())
                .extracting(Device::getSerialNumber)
                .containsExactly("SN-AAA-001", "SN-AAA-002", "SN-XYZ-999", "sn-bbb-003"); // ASCII order
    }

    @Test
    @DisplayName("findAll(\"\") behaves as match-all due to LIKE '%%%'")
    void findAll_emptyString_matchesAll() {
        Page<Device> page = deviceRepository.findAll(
                "",
                PageRequest.of(0, 10)
        );
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("findAll matches case-insensitively by serialNumber")
    void findAll_matchesBySerial_caseInsensitive() {
        Page<Device> p1 = deviceRepository.findAll("aaa", PageRequest.of(0, 10));
        assertThat(p1.getContent())
                .extracting(Device::getSerialNumber)
                .containsExactlyInAnyOrder("SN-AAA-001", "SN-AAA-002");

        Page<Device> p2 = deviceRepository.findAll("BBB", PageRequest.of(0, 10));
        assertThat(p2.getContent())
                .extracting(Device::getSerialNumber)
                .containsExactly("sn-bbb-003");
    }

    @Test
    @DisplayName("findAll matches by joined user fields: fullName, email, phone, address")
    void findAll_matchesByUserFields() {
        // by fullName
        Page<Device> byName = deviceRepository.findAll("john", PageRequest.of(0, 10));
        assertThat(byName.getContent())
                .extracting(Device::getSerialNumber)
                .containsExactlyInAnyOrder("SN-AAA-001", "SN-AAA-002");

        // by email fragment
        Page<Device> byEmail = deviceRepository.findAll("PETROV", PageRequest.of(0, 10));
        assertThat(byEmail.getContent())
                .extracting(Device::getSerialNumber)
                .containsExactly("sn-bbb-003");

        // by phone substring
        Page<Device> byPhone = deviceRepository.findAll("000333", PageRequest.of(0, 10));
        assertThat(byPhone.getContent())
                .extracting(Device::getSerialNumber)
                .containsExactly("sn-bbb-003");

        // by address fragment
        Page<Device> byAddr = deviceRepository.findAll("varna", PageRequest.of(0, 10));
        assertThat(byAddr.getContent())
                .extracting(Device::getSerialNumber)
                .containsExactlyInAnyOrder("SN-AAA-001", "SN-AAA-002");
    }

    @Test
    @DisplayName("getAllDevices returns all devices (paged), no filter")
    void getAllDevices_returnsAllPaged() {
        Page<Device> page = deviceRepository.getAllDevices(PageRequest.of(0, 2, Sort.by("serialNumber").ascending()));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).hasSize(2);

        // next page should continue without overlap
        Page<Device> page1 = deviceRepository.getAllDevices(PageRequest.of(1, 2, Sort.by("serialNumber").ascending()));
        String lastP0 = page.getContent().get(page.getNumberOfElements() - 1).getSerialNumber();
        String firstP1 = page1.getContent().get(0).getSerialNumber();
        assertThat(lastP0).isLessThanOrEqualTo(firstP1);
    }

    @Test
    @DisplayName("findAll pagination + sorting are stable and non-overlapping")
    void findAll_paginationSorting_stable() {
        // seed more devices to exercise paging
        List<Device> extra = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            extra.add(device("SN-PAGE-" + String.format("%03d", i), LocalDate.of(2025, 1, 1), john, "e" + i));
        }
        deviceRepository.saveAll(extra);

        PageRequest p0 = PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "serialNumber"));
        PageRequest p1 = PageRequest.of(1, 5, Sort.by(Sort.Direction.ASC, "serialNumber"));

        Page<Device> page0 = deviceRepository.findAll("", p0); // match-all
        Page<Device> page1 = deviceRepository.findAll("", p1);

        assertThat(page0.getContent()).hasSize(5);
        assertThat(page1.getContent()).hasSize(5);

        String lastOf0 = page0.getContent().get(4).getSerialNumber();
        String firstOf1 = page1.getContent().get(0).getSerialNumber();
        assertThat(lastOf0).isLessThan(firstOf1);

        // total = initial 4 + extra 12 = 16
        assertThat(page0.getTotalElements()).isEqualTo(16);
    }

    @Test
    @DisplayName("findAll with term that matches nothing returns an empty page")
    void findAll_noMatch_returnsEmpty() {
        Page<Device> page = deviceRepository.findAll("no-such-term-xyz", PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("findAll matches by joined user EMAIL case-insensitively")
    void findAll_matchesByUserEmail_caseInsensitive() {
        Page<Device> page = deviceRepository.findAll("JOHN@EXAMPLE.COM", PageRequest.of(0, 10));
        assertThat(page.getContent())
                .extracting(Device::getSerialNumber)
                .containsExactlyInAnyOrder("SN-AAA-001", "SN-AAA-002");
    }

    @Test
    @DisplayName("findAll supports DESC sorting by serialNumber")
    void findAll_sorting_desc() {
        Page<Device> page = deviceRepository.findAll(
                "",
                PageRequest.of(0, 4, Sort.by(Sort.Direction.DESC, "serialNumber"))
        );
        assertThat(page.getContent())
                .extracting(Device::getSerialNumber)
                .containsExactly("sn-bbb-003", "SN-XYZ-999", "SN-AAA-002", "SN-AAA-001");
    }

    @Test
    @DisplayName("getAllDevices: requesting a page beyond the last returns empty content")
    void getAllDevices_beyondLastPage_returnsEmpty() {
        // 4 devices in setup → with page size 3 → pages: index 0 and 1; index 2 should be empty
        Page<Device> p2 = deviceRepository.getAllDevices(PageRequest.of(2, 3, Sort.by("serialNumber")));
        assertThat(p2.getContent()).isEmpty();
        assertThat(p2.getTotalElements()).isEqualTo(4);
        assertThat(p2.getTotalPages()).isEqualTo(2); // ceil(4/3)=2
    }

    @Test
    @DisplayName("deleteBySerialNumber is idempotent (deleting twice leaves count unchanged)")
    void deleteBySerialNumber_idempotent() {
        deviceRepository.deleteBySerialNumber("SN-AAA-001");
        long countAfterFirst = deviceRepository.count();
        deviceRepository.deleteBySerialNumber("SN-AAA-001"); // again
        long countAfterSecond = deviceRepository.count();

        assertThat(countAfterFirst).isEqualTo(3);
        assertThat(countAfterSecond).isEqualTo(3);
    }

    @Test
    @DisplayName("save on existing serialNumber updates the entity (upsert semantics)")
    void save_existingSerial_updates() {
        // precondition
        Device before = deviceRepository.findById("SN-AAA-002").orElseThrow();
        assertThat(before.getComment()).isEqualTo("second");

        // update comment and warranty
        before.setComment("updated-comment");
        before.setWarrantyExpirationDate(before.getPurchaseDate().plusYears(3));
        deviceRepository.save(before);

        Device after = deviceRepository.findById("SN-AAA-002").orElseThrow();
        assertThat(after.getComment()).isEqualTo("updated-comment");
        assertThat(after.getWarrantyExpirationDate()).isEqualTo(before.getPurchaseDate().plusYears(3));
    }

    @Test
    @DisplayName("findAll can return devices with null user (search by serial only)")
    void findAll_nullUser_serialOnly() {
        Page<Device> page = deviceRepository.findAll("SN-XYZ-999", PageRequest.of(0, 10));
        assertThat(page.getContent())
                .extracting(Device::getSerialNumber)
                .containsExactly("SN-XYZ-999");
    }
}
