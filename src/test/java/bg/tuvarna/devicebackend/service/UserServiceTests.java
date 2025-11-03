package bg.tuvarna.devicebackend.service;

import bg.tuvarna.devicebackend.controllers.exceptions.CustomException;
import bg.tuvarna.devicebackend.models.dtos.UserCreateVO;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.repositories.UserRepository;
import bg.tuvarna.devicebackend.services.DeviceService;
import bg.tuvarna.devicebackend.services.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public class UserServiceTests {
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private PasswordEncoder passwordEncoder;
    @MockBean
    private DeviceService deviceService;
    @Autowired
    private UserService userService;

    @Test
    public void registerUserShouldThrowPhoneExistsException() {
        UserCreateVO userCreateVO = new UserCreateVO(
                "Ivan",
                "123",
                "Email",
                "+123",
                "adress",
                LocalDate.now(),
                "123451"
        );

        when(userRepository.getByPhone("+123")).thenReturn(new User());

        CustomException ex = assertThrows(
                CustomException.class,
                ()-> userService.register(userCreateVO)
        );

        assertEquals("Phone already taken", ex.getMessage());
    }

    @Test
    public void registerUserShouldThrowEmailExistsException() {
        UserCreateVO userCreateVO = new UserCreateVO(
                "Ivan",
                "123",
                "Email",
                "+123",
                "adress",
                LocalDate.now(),
                "123451"
        );

        when(userRepository.getByEmail("Email")).thenReturn(new User());

        CustomException ex = assertThrows(
                CustomException.class,
                ()-> userService.register(userCreateVO)
        );

        assertEquals("Email already taken", ex.getMessage());
    }
}