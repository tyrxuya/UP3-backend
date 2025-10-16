package bg.tuvarna.devicebackend.repositories;

import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.models.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
public class UserRepositoryTests {
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User userJohnUser = User.builder()
                .fullName("John Doe")
                .address("Varna, Bulgaria")
                .phone("+359888000111")
                .email("john@example.com")
                .password("{noop}pwd")
                .role(UserRole.USER)
                .build();

        User userAliceAdmin = User.builder()
                .fullName("Alice Admin")
                .address("Sofia, Bulgaria")
                .phone("+359888000222")
                .email("alice@corp.com")
                .password("{noop}pwd")
                .role(UserRole.ADMIN)
                .build();

        User userBobiUser = User.builder()
                .fullName("Bobi Petrov")
                .address("Plovdiv, Bulgaria")
                .phone("+359888000333")
                .email("bobi@petrov.bg")
                .password("{noop}pwd")
                .role(UserRole.USER)
                .build();

        userRepository.save(userJohnUser);
        userRepository.save(userAliceAdmin);
        userRepository.save(userBobiUser);
    }

    // -------------------- ORIGINAL TESTS --------------------

    @Test
    @DisplayName("getByEmail returns the exact user by email")
    void getByEmail_returnsUser() {
        User found = userRepository.getByEmail("john@example.com");
        assertThat(found).isNotNull();
        assertThat(found.getFullName()).isEqualTo("John Doe");
        assertThat(found.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("getByPhone returns the exact user by phone")
    void getByPhone_returnsUser() {
        User found = userRepository.getByPhone("+359888000333");
        assertThat(found).isNotNull();
        assertThat(found.getEmail()).isEqualTo("bobi@petrov.bg");
    }

    @Test
    @DisplayName("findByEmailOrPhone matches by email OR by phone")
    void findByEmailOrPhone_matchesByEither() {
        Optional<User> byEmail = userRepository.findByEmailOrPhone("john@example.com");
        Optional<User> byPhone = userRepository.findByEmailOrPhone("+359888000333");
        Optional<User> none = userRepository.findByEmailOrPhone("not-here");

        assertThat(byEmail).isPresent().get().extracting(User::getFullName).isEqualTo("John Doe");
        assertThat(byPhone).isPresent().get().extracting(User::getEmail).isEqualTo("bobi@petrov.bg");
        assertThat(none).isNotPresent();
    }

    @Test
    @DisplayName("searchBy(null, pageable) returns only non-ADMIN users (sorted)")
    void searchBy_null_returnsNonAdminsPaged() {
        Page<User> page = userRepository.searchBy(
                null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "fullName"))
        );

        assertThat(page.getContent())
                .extracting(User::getRole)
                .doesNotContain(UserRole.ADMIN);

        assertThat(page.getTotalElements()).isEqualTo(2); // John + Bobi only
        assertThat(page.getContent())
                .extracting(User::getFullName)
                .containsExactly("Bobi Petrov", "John Doe"); // sorted by fullName asc
    }

    @Test
    @DisplayName("searchBy matches case-insensitively across user fields (name/email fragments)")
    void searchBy_matchesAcrossFields_caseInsensitive() {
        Page<User> byName = userRepository.searchBy("john", PageRequest.of(0, 10));
        assertThat(byName.getContent())
                .extracting(User::getEmail)
                .contains("john@example.com");

        Page<User> byEmailFragment = userRepository.searchBy("PETROV", PageRequest.of(0, 10));
        assertThat(byEmailFragment.getContent())
                .extracting(User::getEmail)
                .contains("bobi@petrov.bg");
    }

    @Test
    @DisplayName("getAllUsers returns only non-ADMIN users (paged)")
    void getAllUsers_excludesAdmins() {
        Page<User> page = userRepository.getAllUsers(PageRequest.of(0, 5));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .allMatch(u -> u.getRole() != UserRole.ADMIN);
    }

    @Test
    @DisplayName("getByEmail returns null when not found")
    void getByEmail_notFound_returnsNull() {
        User notFound = userRepository.getByEmail("nope@nowhere.test");
        assertThat(notFound).isNull();
    }

    @Test
    @DisplayName("getByPhone returns null when not found")
    void getByPhone_notFound_returnsNull() {
        User notFound = userRepository.getByPhone("+111222333");
        assertThat(notFound).isNull();
    }

    @Test
    @DisplayName("findByEmailOrPhone is case-sensitive for email (collation-dependent) and exact for phone")
    void findByEmailOrPhone_caseSensitivity() {
        Optional<User> exactEmail = userRepository.findByEmailOrPhone("john@example.com");
        assertThat(exactEmail).isPresent();

        Optional<User> diffCaseEmail = userRepository.findByEmailOrPhone("JOHN@example.com");
        assertThat(diffCaseEmail).isNotPresent();

        Optional<User> byPhone = userRepository.findByEmailOrPhone("+359888000333");
        assertThat(byPhone).isPresent().get()
                .extracting(User::getEmail).isEqualTo("bobi@petrov.bg");
    }

    @Test
    @DisplayName("searchBy(null) returns all non-ADMIN users")
    void searchBy_null_returnsAllNonAdmins() {
        Page<User> page = userRepository.searchBy(null, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(u -> u.getRole() != UserRole.ADMIN);
    }

    @Test
    @DisplayName("searchBy(\"\") behaves like 'match all' over non-ADMIN (LIKE '%%%' on many fields)")
    void searchBy_emptyString_matchesAllNonAdmins() {
        Page<User> page = userRepository.searchBy("", PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(u -> u.getRole() != UserRole.ADMIN);
    }

    @Test
    @DisplayName("searchBy matches by address (case-insensitive)")
    void searchBy_matchesByAddress() {
        Page<User> page = userRepository.searchBy("varna", PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(User::getEmail).contains("john@example.com");
    }

    @Test
    @DisplayName("searchBy matches by phone substring")
    void searchBy_matchesByPhoneSubstring() {
        Page<User> page = userRepository.searchBy("000333", PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(User::getEmail).contains("bobi@petrov.bg");
    }

    @Test
    @DisplayName("searchBy excludes ADMIN even when the term matches admin fields")
    void searchBy_excludesAdminEvenWhenMatching() {
        Page<User> page = userRepository.searchBy("alice", PageRequest.of(0, 10));
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("searchBy pagination + sorting are consistent and non-overlapping")
    void searchBy_paginationAndSorting() {
        // Seed more non-admins for paging checks
        List<User> extra = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            extra.add(User.builder()
                    .fullName("User " + i)
                    .address("BG " + i)
                    .phone("+35955555" + i)
                    .email("u" + i + "@mail.bg")
                    .password("pwd")
                    .role(UserRole.USER)
                    .build());
        }
        userRepository.saveAll(extra);

        // searchBy("") -> all non-admins, sort by email asc, pages of 5
        PageRequest p0 = PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "email"));
        PageRequest p1 = PageRequest.of(1, 5, Sort.by(Sort.Direction.ASC, "email"));

        Page<User> page0 = userRepository.searchBy("", p0);
        Page<User> page1 = userRepository.searchBy("", p1);

        assertThat(page0.getContent()).hasSize(5);
        assertThat(page1.getContent()).hasSize(5);

        // Verify non-overlap across pages
        String lastOfPage0 = page0.getContent().get(4).getEmail();
        String firstOfPage1 = page1.getContent().get(0).getEmail();
        assertThat(lastOfPage0).isLessThan(firstOfPage1);

        // total elements = 2 initial non-admins + 12 extra = 14
        assertThat(page0.getTotalElements()).isEqualTo(14);
        assertThat(page0.getContent()).allMatch(u -> u.getRole() != UserRole.ADMIN);
        assertThat(page1.getContent()).allMatch(u -> u.getRole() != UserRole.ADMIN);
    }

    @Test
    @DisplayName("getAllUsers respects paging & sorting and excludes ADMIN")
    void getAllUsers_paging() {
        userRepository.save(User.builder()
                .fullName("Zed Z")
                .address("Ruse")
                .phone("+359000001")
                .email("zed@example.com")
                .password("pwd")
                .role(UserRole.USER)
                .build());

        userRepository.save(User.builder()
                .fullName("Aaron A")
                .address("Burgas")
                .phone("+359000002")
                .email("aaron@example.com")
                .password("pwd")
                .role(UserRole.USER)
                .build());

        Page<User> p0 = userRepository.getAllUsers(PageRequest.of(0, 2, Sort.by("fullName").ascending()));
        Page<User> p1 = userRepository.getAllUsers(PageRequest.of(1, 2, Sort.by("fullName").ascending()));

        // Total = initial 2 users (non-admin) + 2 added = 4
        assertThat(p0.getTotalElements()).isEqualTo(4);
        assertThat(p0.getContent()).allMatch(u -> u.getRole() != UserRole.ADMIN);
        assertThat(p1.getContent()).allMatch(u -> u.getRole() != UserRole.ADMIN);

        // Verify sorting by fullName asc between pages
        String lastP0 = p0.getContent().get(p0.getNumberOfElements() - 1).getFullName();
        String firstP1 = p1.getContent().get(0).getFullName();
        assertThat(lastP0).isLessThanOrEqualTo(firstP1);
    }
}