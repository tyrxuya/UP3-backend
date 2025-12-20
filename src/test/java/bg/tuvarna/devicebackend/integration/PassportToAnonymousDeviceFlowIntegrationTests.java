package bg.tuvarna.devicebackend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
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

import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.models.enums.UserRole;
import bg.tuvarna.devicebackend.repositories.UserRepository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PassportToAnonymousDeviceFlowIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static String token;

    // ВАЖНО: форматът ти е SN-AAA-001 (примерно), затова правим SN-AAA-123
    private static final String DEVICE_SERIAL = "SN-AAA-123";

    @BeforeEach
    void setupMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @BeforeAll
    static void beforeAll(
            @Autowired UserRepository userRepository,
            @Autowired PasswordEncoder passwordEncoder,
            @Autowired WebApplicationContext context,
            @Autowired ObjectMapper mapper
    ) throws Exception {

        MockMvc mvcLocal = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // seed admin (уникален, за да няма конфликт с други тестове)
        String adminEmail = "flowC_admin@abv.bg";

        if (userRepository.findByEmailOrPhone(adminEmail).isEmpty()) {
            userRepository.save(User.builder()
                    .fullName("Admin Flow C")
                    .email(adminEmail)
                    .password(passwordEncoder.encode("Admin$12345"))
                    .role(UserRole.ADMIN)
                    .build());
        }

        // login -> token
        MvcResult login = mvcLocal.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "flowC_admin@abv.bg",
                                  "password": "Admin$12345"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        token = mapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
        assertNotNull(token);
    }

    // 1) Create passport (за да може anonymous device да намери passport по serial prefix/range)
    @Test
    @Order(1)
    void createPassport_SeedSerialPrefix_201() throws Exception {
        mvc.perform(post("/api/v1/passports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "AAA",
                                  "model": "AAA-Model",
                                  "serialPrefix": "SN-AAA-",
                                  "fromSerialNumber": 1,
                                  "toSerialNumber": 999,
                                  "warrantyMonths": 24
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("AAA"))
                .andExpect(jsonPath("$.model").value("AAA-Model"));
    }

    // 2) Create anonymous device (в твоето API това се оказа protected -> с token)
    @Test
    @Order(2)
    void createAnonymousDevice_201() throws Exception {
        mvc.perform(post("/api/v1/devices/anonymousDevice")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceSerialNumber": "%s",
                                  "purchaseDate": "2025-01-01"
                                }
                                """.formatted(DEVICE_SERIAL)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.serialNumber").value(DEVICE_SERIAL))
                .andExpect(jsonPath("$.purchaseDate").value("2025-01-01"));
    }

    // 3) Get device (200)
    @Test
    @Order(3)
    void getDevice_200() throws Exception {
        mvc.perform(get("/api/v1/devices/" + DEVICE_SERIAL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serialNumber").value(DEVICE_SERIAL));
    }

    // 4) Update device (ако update endpoint е PUT /api/v1/devices/{serial})
    // Ако при теб не е така — кажи ми само endpoint-а и VO-то и ще го наглася.
    @Test
    @Order(4)
    void updateDevice_200() throws Exception {
        mvc.perform(put("/api/v1/devices/" + DEVICE_SERIAL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "purchaseDate": "2025-02-02",
                                  "comment": "integration updated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serialNumber").value(DEVICE_SERIAL))
                .andExpect(jsonPath("$.purchaseDate").value("2025-02-02"))
                .andExpect(jsonPath("$.comment").value("integration updated"));
    }

    // 5) Duplicate create should fail (400)
    @Test
    @Order(5)
    void createAnonymousDevice_DuplicateSerial_ShouldFail_400() throws Exception {
        mvc.perform(post("/api/v1/devices/anonymousDevice")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceSerialNumber": "%s",
                                  "purchaseDate": "2025-01-01"
                                }
                                """.formatted(DEVICE_SERIAL)))
                .andExpect(status().isBadRequest());
    }
}
