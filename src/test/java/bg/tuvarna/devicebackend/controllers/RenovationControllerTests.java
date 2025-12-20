package bg.tuvarna.devicebackend.controllers;

import bg.tuvarna.devicebackend.models.entities.Device;
import bg.tuvarna.devicebackend.models.entities.Passport;
import bg.tuvarna.devicebackend.models.entities.Renovation;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.models.enums.UserRole;
import bg.tuvarna.devicebackend.repositories.DeviceRepository;
import bg.tuvarna.devicebackend.repositories.PassportRepository;
import bg.tuvarna.devicebackend.repositories.RenovationRepository;
import bg.tuvarna.devicebackend.repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RenovationControllerTests {

    @Autowired private MockMvc mvc;
    @Autowired private WebApplicationContext context;

    @Autowired private ObjectMapper mapper;

    @Autowired private UserRepository userRepository;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private PassportRepository passportRepository;
    @Autowired private RenovationRepository renovationRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // login като ADMIN (като при devices)
        User user = User.builder()
                .fullName("gosho")
                .email("gosho@abv.bg")
                .password(passwordEncoder.encode("Az$um_GOSHO123"))
                .role(UserRole.ADMIN)
                .build();
        userRepository.save(user);

        MvcResult loginResult = mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "gosho@abv.bg",
                                  "password": "Az$um_GOSHO123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        token = mapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();
    }

    @AfterEach
    void tearDown() {
        renovationRepository.deleteAll();
        deviceRepository.deleteAll();
        passportRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ----------- HELPERS -----------

    private Device seedDevice(String serial) {
        Passport p = Passport.builder()
                .name("Seed")
                .model("SeedModel")
                .serialPrefix("SN-AAA-")
                .fromSerialNumber(1)
                .toSerialNumber(999)
                .warrantyMonths(24)
                .build();
        passportRepository.save(p);

        Device d = new Device();
        d.setSerialNumber(serial);
        d.setPurchaseDate(LocalDate.of(2025, 1, 1));
        d.setPassport(p);
        d.setWarrantyExpirationDate(LocalDate.of(2027, 1, 1));
        return deviceRepository.save(d);
    }

    // ----------- TESTS -----------

    @Test
    void saveRenovation_WithoutToken_ShouldFail401() throws Exception {
        mvc.perform(post("/api/v1/renovations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceSerialNumber": "SN-AAA-001",
                                  "description": "cleaning",
                                  "renovationDate": "2025-06-01"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void saveRenovation_Success_ShouldReturn201_AndPersist() throws Exception {
        seedDevice("SN-AAA-001");

        mvc.perform(post("/api/v1/renovations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceSerialNumber": "SN-AAA-001",
                                  "description": "Battery replaced",
                                  "renovationDate": "2025-06-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                // тук jsonPath-овете зависят от RenovationVO ти;
                // ако има id/description/renovationDate - това е safe:
                .andExpect(jsonPath("$.description").value("Battery replaced"))
                .andExpect(jsonPath("$.renovationDate").value("2025-06-01"));

        assertEquals(1, renovationRepository.count());
    }

    @Test
    void saveRenovation_InvalidPayload_MissingDeviceSerial_ShouldReturn400() throws Exception {
        mvc.perform(post("/api/v1/renovations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Battery replaced",
                                  "renovationDate": "2025-06-01"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saveRenovation_InvalidPayload_MissingRenovationDate_ShouldReturn400() throws Exception {
        mvc.perform(post("/api/v1/renovations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceSerialNumber": "SN-AAA-001",
                                  "description": "Battery replaced"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saveRenovation_DeviceNotExists_ShouldFail() throws Exception {
        mvc.perform(post("/api/v1/renovations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceSerialNumber": "SN-AAA-999",
                                  "description": "Battery replaced",
                                  "renovationDate": "2025-06-01"
                                }
                                """))
                // тук зависи как DeviceService.isDeviceExists хвърля CustomException и как се мапва:
                // ако е @ControllerAdvice -> 400, иначе може 500.
                .andExpect(status().is4xxClientError());
    }
}
