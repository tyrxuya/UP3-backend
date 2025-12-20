package bg.tuvarna.devicebackend.integration;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.junit.jupiter.api.Assertions.*;
        import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
        import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PassportManagementIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static String token;
    private static Long createdPassportId;

    @BeforeEach
    void init() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // seed admin user ако още го няма (уникален email за flow)
        String adminEmail = "flow02_admin@abv.bg";
        if (userRepository.findByEmailOrPhone(adminEmail).isEmpty()) {
            User admin = User.builder()
                    .fullName("Admin Flow02")
                    .email(adminEmail)
                    .password(passwordEncoder.encode("Admin$12345"))
                    .role(UserRole.ADMIN)
                    .build();
            userRepository.save(admin);
        }

        // login admin
        MvcResult login = mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "flow02_admin@abv.bg",
                                  "password": "Admin$12345"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        token = mapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
    }

    @Test @Order(1)
    void createPassport_201_ReturnsId() throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/passports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Seed",
                                  "model": "SeedModel",
                                  "serialPrefix": "SN-AAA-",
                                  "fromSerialNumber": 1,
                                  "toSerialNumber": 999,
                                  "warrantyMonths": 24
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertNotNull(json.get("id"));
        createdPassportId = json.get("id").asLong();
        assertTrue(createdPassportId > 0);
    }

    @Test @Order(2)
    void getBySerialId_Public_200_ReturnsModel() throws Exception {
        mvc.perform(get("/api/v1/passports/getBySerialId/SN-AAA-001"))
                .andExpect(status().isOk())
                // при теб PassportForSerialNumberVO връща {id,name,model} (както видя)
                .andExpect(jsonPath("$.model").value("SeedModel"))
                .andExpect(jsonPath("$.name").value("Seed"));
    }

    @Test @Order(3)
    void getPassports_WithToken_200_ShouldContainCreated() throws Exception {
        mvc.perform(get("/api/v1/passports?page=1&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // тук CustomPage - може да е content/items/data
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SN-AAA-")));
    }

    @Test @Order(4)
    void updatePassport_WithToken_200() throws Exception {
        mvc.perform(put("/api/v1/passports/" + createdPassportId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "SeedUpdated",
                                  "model": "SeedModelUpdated",
                                  "serialPrefix": "SN-AAA-",
                                  "fromSerialNumber": 1,
                                  "toSerialNumber": 999,
                                  "warrantyMonths": 36
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("SeedUpdated"))
                .andExpect(jsonPath("$.model").value("SeedModelUpdated"));
    }

    @Test @Order(5)
    void deletePassport_WithToken_200() throws Exception {
        mvc.perform(delete("/api/v1/passports/" + createdPassportId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
