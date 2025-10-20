package bg.tuvarna.devicebackend.services;

import bg.tuvarna.devicebackend.controllers.exceptions.CustomException;
import bg.tuvarna.devicebackend.controllers.exceptions.ErrorCode;
import bg.tuvarna.devicebackend.models.dtos.ChangePasswordVO;
import bg.tuvarna.devicebackend.models.dtos.UserCreateVO;
import bg.tuvarna.devicebackend.models.dtos.UserListing;
import bg.tuvarna.devicebackend.models.dtos.UserUpdateVO;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.models.enums.UserRole;
import bg.tuvarna.devicebackend.repositories.UserRepository;
import bg.tuvarna.devicebackend.utils.CustomPage;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeviceService deviceService;

    public void register(UserCreateVO userCreateVO) {
        if (isEmailTaken(userCreateVO.email())) {
            throw new CustomException("Email already taken", ErrorCode.AlreadyExists);
        }
        if (isPhoneTaken(userCreateVO.phone())) {
            throw new CustomException("Phone already taken", ErrorCode.AlreadyExists);
        }

        User user = new User(userCreateVO);

        user.setPassword(passwordEncoder.encode(userCreateVO.password()));
        user = userRepository.saveAndFlush(user);

        if (
                userCreateVO.deviceSerialNumber() == null || userCreateVO.deviceSerialNumber().isBlank()
                || userCreateVO.purchaseDate() == null
        ) {
            return;
        }

        try {
            deviceService.alreadyExist(userCreateVO.deviceSerialNumber());
            deviceService.registerDevice(userCreateVO.deviceSerialNumber(), userCreateVO.purchaseDate(), user);
        } catch (CustomException e) {
            userRepository.delete(user);
            throw e;
        }
    }

    public boolean isEmailTaken(String email) {
        User user = userRepository.getByEmail(email);
        return user != null;
    }

    public boolean isPhoneTaken(String phone) {
        User user = userRepository.getByPhone(phone);
        return user != null;
    }

    public User getUserById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new CustomException("User not found", ErrorCode.EntityNotFound));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByEmailOrPhone(username).orElseThrow(() -> new CustomException("User not found", ErrorCode.EntityNotFound));
    }

    public CustomPage<UserListing> getUsers(String searchBy, int page, int size) {
        Page<User> userPage;
        if (searchBy == null) {
            userPage = userRepository.getAllUsers(PageRequest.of(page - 1, size));
        } else {
            userPage = userRepository.searchBy(searchBy, PageRequest.of(page - 1, size));
        }

        CustomPage<UserListing> customPage = new CustomPage<>();
        customPage.setTotalPages(userPage.getTotalPages());
        customPage.setCurrentPage(userPage.getNumber() + 1);
        customPage.setSize(userPage.getSize());
        customPage.setTotalItems(userPage.getTotalElements());

        customPage.setItems(userPage
                .stream()
                .peek(user -> {
                            if (searchBy != null) {
                                user.setDevices(user.getDevices().stream().filter(
                                        device -> device.getSerialNumber().contains(searchBy)
                                ).collect(Collectors.toList()));
                            }
                        }
                )
                .map(UserListing::new)
                .toList()
        );

        return customPage;
    }

    public User updateUser(Long id, UserUpdateVO userUpdateVO) {
        User user = getUserById(id);

        if (user.getRole() == UserRole.ADMIN) {
            throw new CustomException("Admin password can't be changed", ErrorCode.Validation);
        }

        if (isEmailTaken(userUpdateVO.email()) && !user.getEmail().equals(userUpdateVO.email())) {
            throw new CustomException("Email already taken", ErrorCode.AlreadyExists);
        }

        if (isPhoneTaken(userUpdateVO.phone()) && !user.getPhone().equals(userUpdateVO.phone())) {
            throw new CustomException("Phone already taken", ErrorCode.AlreadyExists);
        }

        user.setFullName(userUpdateVO.fullName());
        user.setAddress(userUpdateVO.address());
        user.setPhone(userUpdateVO.phone());
        user.setEmail(userUpdateVO.email());

        return userRepository.save(user);
    }

    public void updatePassword(Long id, ChangePasswordVO passwordVO) {
        User user = getUserById(id);
        if (user.getRole() == UserRole.ADMIN) {
            throw new CustomException("Admin password can't be changed", ErrorCode.Validation);
        }
        if (passwordEncoder.matches(passwordVO.oldPassword(), user.getPassword())) {
            user.setPassword(passwordEncoder.encode(passwordVO.newPassword()));
            userRepository.save(user);
        } else {
            throw new CustomException("Old password didn't match", ErrorCode.Validation);
        }
    }
}