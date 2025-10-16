package bg.tuvarna.devicebackend.repositories;

import bg.tuvarna.devicebackend.models.entities.Passport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class PassportRepositoryTests {

    @Autowired
    private PassportRepository passportRepository;

    private Passport p1_sn_100_199;
    private Passport p2_sn_200_299;
    private Passport p4_xyz_1000_2000;

    @BeforeEach
    void setUp() {
        passportRepository.deleteAll();

        p1_sn_100_199 = passportRepository.save(Passport.builder()
                .name("Series A")
                .model("M1")
                .serialPrefix("SN-")
                .fromSerialNumber(100)
                .toSerialNumber(199)
                .warrantyMonths(12)
                .build());

        p2_sn_200_299 = passportRepository.save(Passport.builder()
                .name("Series B")
                .model("M2")
                .serialPrefix("SN-")
                .fromSerialNumber(200)
                .toSerialNumber(299)
                .warrantyMonths(24)
                .build());

        p4_xyz_1000_2000 = passportRepository.save(Passport.builder()
                .name("X Series")
                .model("X1000")
                .serialPrefix("XYZ-")
                .fromSerialNumber(1000)
                .toSerialNumber(2000)
                .warrantyMonths(36)
                .build());
    }

    // ---------------- findByFromSerialNumberBetween ----------------

    @Test
    @DisplayName("findByFromSerialNumberBetween: matches passports with the same prefix where FROM or TO falls within [start,end]")
    void findByFromSerialNumberBetween_basicOverlap() {
        // Window: [150, 250] should include:
        // p1 (to=199 in range) and p2 (from=200 in range) — both with prefix SN-
        List<Passport> result = passportRepository.findByFromSerialNumberBetween("SN-", 150, 250);

        assertThat(result)
                .extracting(Passport::getId)
                .containsExactlyInAnyOrder(p1_sn_100_199.getId(), p2_sn_200_299.getId());
    }

    @Test
    @DisplayName("findByFromSerialNumberBetween: inclusive edge values (BETWEEN includes endpoints)")
    void findByFromSerialNumberBetween_inclusiveEdges() {
        // Window: [199, 200] should include p1 (to=199) and p2 (from=200)
        List<Passport> result = passportRepository.findByFromSerialNumberBetween("SN-", 199, 200);

        assertThat(result)
                .extracting(Passport::getId)
                .containsExactlyInAnyOrder(p1_sn_100_199.getId(), p2_sn_200_299.getId());
    }

    @Test
    @DisplayName("findByFromSerialNumberBetween: respects prefix — no match if different prefix")
    void findByFromSerialNumberBetween_wrongPrefix_noMatch() {
        List<Passport> result = passportRepository.findByFromSerialNumberBetween("ZZZ-", 0, 5000);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByFromSerialNumberBetween: prefix supports LIKE — wildcard pattern can match")
    void findByFromSerialNumberBetween_prefixWithWildcard() {
        // The query uses "p.serialPrefix like :serialPrefix".
        // Using pattern "SN-%" should still match "SN-".
        List<Passport> result = passportRepository.findByFromSerialNumberBetween("SN-%", 0, 500);

        assertThat(result)
                .extracting(Passport::getId)
                .containsExactlyInAnyOrder(p1_sn_100_199.getId(), p2_sn_200_299.getId());
    }

    @Test
    @DisplayName("findByFromSerialNumberBetween: returns empty when window doesn't intersect any FROM/TO for given prefix")
    void findByFromSerialNumberBetween_noOverlap() {
        // For "SN-" ranges [100-199] and [200-299], a window before them shouldn't match
        List<Passport> result = passportRepository.findByFromSerialNumberBetween("SN-", 0, 50);
        assertThat(result).isEmpty();
    }

    // ---------------- findByFromSerial (serialId LIKE CONCAT(prefix, '%')) ----------------

    @Test
    @DisplayName("findByFromSerial: returns passports whose serialPrefix is a prefix of the provided serialId")
    void findByFromSerial_basic() {
        // serialId starts with "SN-" → both SN- passports match
        List<Passport> result = passportRepository.findByFromSerial("SN-12345");

        assertThat(result)
                .extracting(Passport::getId)
                .containsExactlyInAnyOrder(p1_sn_100_199.getId(), p2_sn_200_299.getId());
    }

    @Test
    @DisplayName("findByFromSerial: matches a different prefix")
    void findByFromSerial_otherPrefix() {
        List<Passport> result = passportRepository.findByFromSerial("XYZ-1500ABC");
        assertThat(result)
                .extracting(Passport::getId)
                .containsExactly(p4_xyz_1000_2000.getId());
    }

    @Test
    @DisplayName("findByFromSerial: returns empty when serialId does not start with any known prefix")
    void findByFromSerial_noMatch() {
        List<Passport> result = passportRepository.findByFromSerial("NOPE-0001");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByFromSerial: case sensitivity follows DB collation (H2 default is case-sensitive)")
    void findByFromSerial_caseSensitivityNote() {
        // "sn-123" will NOT match "SN-" under H2 default since LIKE is case-sensitive.
        List<Passport> result = passportRepository.findByFromSerial("sn-123");
        assertThat(result).isEmpty();
    }
}
