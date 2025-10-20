package bg.tuvarna.devicebackend.services;

import bg.tuvarna.devicebackend.controllers.exceptions.CustomException;
import bg.tuvarna.devicebackend.models.dtos.ChangePasswordVO;
import bg.tuvarna.devicebackend.models.dtos.UserCreateVO;
import bg.tuvarna.devicebackend.models.dtos.UserListing;
import bg.tuvarna.devicebackend.models.dtos.UserUpdateVO;
import bg.tuvarna.devicebackend.models.entities.Device;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.models.enums.UserRole;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTests {
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private DeviceService deviceService;

    @InjectMocks
    private UserService userService;

    private UserCreateVO createVO;

    @BeforeEach
    void init() {
        createVO = new UserCreateVO(
                "Ivan",
                "secretPwd",
                "ivan@example.com",
                "+359888000111",
                "Varna",
                LocalDate.of(2024, 12, 31),
                "SN-ABC-123"
        );
    }

    private static User user(Long id, String fullName, String email, String phone, UserRole role) {
        User u = User.builder()
                .id(id)
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .address("addr")
                .password("{enc}x")
                .role(role)
                .build();
        return u;
    }

    @Test
    @DisplayName("register: throws when email already taken")
    void register_emailTaken() {
        when(userRepository.getByEmail("ivan@example.com")).thenReturn(user(99L, "Somebody", "ivan@example.com", "X", UserRole.USER));

        assertThatThrownBy(() -> userService.register(createVO))
                .isInstanceOf(CustomException.class)
                .hasMessage("Email already taken");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(deviceService);
    }

    @Test
    @DisplayName("register: throws when phone already taken")
    void register_phoneTaken() {
        when(userRepository.getByEmail("ivan@example.com")).thenReturn(null);
        when(userRepository.getByPhone("+359888000111")).thenReturn(user(88L, "Somebody", "x@x", "+359888000111", UserRole.USER));

        assertThatThrownBy(() -> userService.register(createVO))
                .isInstanceOf(CustomException.class)
                .hasMessage("Phone already taken");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(deviceService);
    }

    @Test
    @DisplayName("register: success encodes password, saves user, checks device existence, registers device")
    void register_success() {
        when(userRepository.getByEmail("ivan@example.com")).thenReturn(null);
        when(userRepository.getByPhone("+359888000111")).thenReturn(null);
        when(passwordEncoder.encode("secretPwd")).thenReturn("{enc}secretPwd");

        AtomicReference<User> savedRef = new AtomicReference<>();
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            savedRef.set(u);
            return u;
        });

        Device device = new Device();
        device.setSerialNumber("SN-ABC-123");

        doNothing().when(deviceService).alreadyExist("SN-ABC-123");
        when(deviceService.registerDevice(
                anyString(),
                any(LocalDate.class),
                any(User.class)
        )).thenReturn(device);

        userService.register(createVO);

        assertThat(savedRef.get().getPassword()).isEqualTo("{enc}secretPwd");

        verify(deviceService).alreadyExist("SN-ABC-123");
        verify(deviceService).registerDevice("SN-ABC-123", LocalDate.of(2024, 12, 31), savedRef.get());

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void isEmailTaken_true_false() {
        when(userRepository.getByEmail("x@x")).thenReturn(user(1L, "A", "x@x", "p", UserRole.USER))
                .thenReturn(null);

        assertThat(userService.isEmailTaken("x@x")).isTrue();
        assertThat(userService.isEmailTaken("x@x")).isFalse();
    }

    @Test
    void isPhoneTaken_true_false() {
        when(userRepository.getByPhone("p")).thenReturn(user(1L, "A", "e", "p", UserRole.USER))
                .thenReturn(null);

        assertThat(userService.isPhoneTaken("p")).isTrue();
        assertThat(userService.isPhoneTaken("p")).isFalse();
    }

    @Test
    @DisplayName("getUserById: returns user when present")
    void getUserById_ok() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "Ok", "ok@ok", "p", UserRole.USER)));
        User u = userService.getUserById(7L);
        assertThat(u.getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("getUserById: throws when missing")
    void getUserById_notFound() {
        when(userRepository.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(8L))
                .isInstanceOf(CustomException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("getUserByUsername: returns user when present (email or phone)")
    void getUserByUsername_ok() {
        when(userRepository.findByEmailOrPhone("ivan@example.com"))
                .thenReturn(Optional.of(user(3L, "Ivan", "ivan@example.com", "p", UserRole.USER)));

        User u = userService.getUserByUsername("ivan@example.com");
        assertThat(u.getEmail()).isEqualTo("ivan@example.com");
    }

    @Test
    @DisplayName("getUserByUsername: throws when missing")
    void getUserByUsername_notFound() {
        when(userRepository.findByEmailOrPhone("who")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByUsername("who"))
                .isInstanceOf(CustomException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("getUsers(null, page, size): calls getAllUsers with (page-1), returns proper metadata")
    void getUsers_nullSearch_usesGetAllUsers() {
        List<User> content = List.of(
                user(1L, "A", "a@a", "p1", UserRole.USER),
                user(2L, "B", "b@b", "p2", UserRole.USER)
        );
        Page<User> page = new PageImpl<>(content, PageRequest.of(1, 5), 12); // page index 1 -> "2nd page"
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(userRepository.getAllUsers(any(Pageable.class))).thenReturn(page);

        CustomPage<UserListing> result = userService.getUsers(null, 2, 5);

        verify(userRepository).getAllUsers(pageableCaptor.capture());
        Pageable used = pageableCaptor.getValue();
        assertThat(used.getPageNumber()).isEqualTo(1);
        assertThat(used.getPageSize()).isEqualTo(5);

        assertThat(result.getCurrentPage()).isEqualTo(2); // service adds +1
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getTotalItems()).isEqualTo(12);
        assertThat(result.getTotalPages()).isEqualTo((int) Math.ceil(12.0 / 5)); // 3
    }

    @Test
    @DisplayName("updateUser: admin guard blocks updates")
    void updateUser_adminGuard() {
        UserUpdateVO vo = new UserUpdateVO("Admin", "addr", "p", "admin@x");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "Admin", "admin@x", "p", UserRole.ADMIN)));

        assertThatThrownBy(() -> userService.updateUser(1L, vo))
                .isInstanceOf(CustomException.class)
                .hasMessage("Admin password can't be changed");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUser: throws when new email is taken by another user")
    void updateUser_emailTakenByOther() {
        User current = user(2L, "U", "me@x", "111", UserRole.USER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(current));
        when(userRepository.getByEmail("new@x")).thenReturn(user(99L, "Other", "new@x", "999", UserRole.USER));

        UserUpdateVO vo = new UserUpdateVO("U2", "addr2", "111", "new@x");

        assertThatThrownBy(() -> userService.updateUser(2L, vo))
                .isInstanceOf(CustomException.class)
                .hasMessage("Email already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUser: throws when new phone is taken by another user")
    void updateUser_phoneTakenByOther() {
        User current = user(3L, "U", "me@x", "111", UserRole.USER);
        when(userRepository.findById(3L)).thenReturn(Optional.of(current));
        when(userRepository.getByEmail("me@x")).thenReturn(current); // unchanged email -> allowed
        when(userRepository.getByPhone("222")).thenReturn(user(77L, "Other", "o@x", "222", UserRole.USER));

        UserUpdateVO vo = new UserUpdateVO("U2", "addr2", "222", "me@x");

        assertThatThrownBy(() -> userService.updateUser(3L, vo))
                .isInstanceOf(CustomException.class)
                .hasMessage("Phone already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUser: succeeds when no conflicts and saves updated fields")
    void updateUser_success() {
        User current = user(4L, "Old", "old@x", "111", UserRole.USER);
        when(userRepository.findById(4L)).thenReturn(Optional.of(current));
        when(userRepository.getByEmail("new@x")).thenReturn(null);
        when(userRepository.getByPhone("222")).thenReturn(null);

        UserUpdateVO vo = new UserUpdateVO("New Name", "New Addr", "222", "new@x");

        userService.updateUser(4l, vo);

        assertThat(current.getFullName()).isEqualTo("New Name");
        assertThat(current.getAddress()).isEqualTo("New Addr");
        assertThat(current.getPhone()).isEqualTo("222");
        assertThat(current.getEmail()).isEqualTo("new@x");
        verify(userRepository).save(current);
    }

    @Test
    @DisplayName("updatePassword: admin guard blocks password change")
    void updatePassword_adminGuard() {
        User admin = user(5L, "Admin", "a@x", "p", UserRole.ADMIN);
        when(userRepository.findById(5L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.updatePassword(5L, new ChangePasswordVO("old", "new")))
                .isInstanceOf(CustomException.class)
                .hasMessage("Admin password can't be changed");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updatePassword: when old matches, encode and save new password")
    void updatePassword_ok() {
        User u = user(6L, "U", "u@x", "p", UserRole.USER);
        u.setPassword("{enc}OLD");
        when(userRepository.findById(6L)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("old", "{enc}OLD")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("{enc}NEW");

        userService.updatePassword(6L, new ChangePasswordVO("old", "new"));

        assertThat(u.getPassword()).isEqualTo("{enc}NEW");
        verify(userRepository).save(u);
    }

    @Test
    @DisplayName("updatePassword: when old doesn't match, throw validation error")
    void updatePassword_wrongOld() {
        User u = user(7L, "U", "u@x", "p", UserRole.USER);
        u.setPassword("{enc}OLD");
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("wrong", "{enc}OLD")).thenReturn(false);

        assertThatThrownBy(() -> userService.updatePassword(7L, new ChangePasswordVO("wrong", "new")))
                .isInstanceOf(CustomException.class)
                .hasMessage("Old password didn't match");

        verify(userRepository, never()).save(any());
    }
}