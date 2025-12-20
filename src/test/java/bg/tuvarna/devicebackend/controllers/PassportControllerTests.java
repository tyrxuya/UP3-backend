package bg.tuvarna.devicebackend.controllers;

import bg.tuvarna.devicebackend.models.entities.Passport;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.models.enums.UserRole;
import bg.tuvarna.devicebackend.repositories.PassportRepository;
import bg.tuvarna.devicebackend.repositories.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PassportControllerTests {

    @Autowired private MockMvc mvc;
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;

    @Autowired private UserRepository userRepository;
    @Autowired private PassportRepository passportRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        User admin = User.builder()
                .fullName("admin")
                .email("admin@abv.bg")
                .password(passwordEncoder.encode("Az$um_ADMIN123"))
                .role(UserRole.ADMIN)
                .build();
        userRepository.save(admin);

        MvcResult loginResult = mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin@abv.bg",
                                  "password": "Az$um_ADMIN123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = mapper.readTree(loginResult.getResponse().getContentAsString());
        token = json.get("token").asText();
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @AfterEach
    void tearDown() {
        passportRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------------- CREATE ----------------

    @Test
    void createPassport_WithoutToken_ShouldFail401() throws Exception {
        mvc.perform(post("/api/v1/passports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "P1",
                                  "model": "M1",
                                  "serialPrefix": "SN-AAA-",
                                  "fromSerialNumber": 1,
                                  "toSerialNumber": 999,
                                  "warrantyMonths": 24
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPassport_Success_ShouldReturn201_AndLocationHeader() throws Exception {
        mvc.perform(post("/api/v1/passports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "P1",
                                  "model": "M1",
                                  "serialPrefix": "SN-AAA-",
                                  "fromSerialNumber": 1,
                                  "toSerialNumber": 999,
                                  "warrantyMonths": 24
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.serialPrefix").value("SN-AAA-"))
                .andExpect(jsonPath("$.fromSerialNumber").value(1))
                .andExpect(jsonPath("$.toSerialNumber").value(999))
                .andExpect(jsonPath("$.warrantyMonths").value(24));

        assertEquals(1, passportRepository.count());
    }

    @Test
    void createPassport_InvalidPayload_ShouldFail400() throws Exception {
        // нарочно липсват полета
        mvc.perform(post("/api/v1/passports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "serialPrefix": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // ---------------- UPDATE ----------------

    @Test
    void updatePassport_WithoutToken_ShouldFail401() throws Exception {
        Passport p = seedPassport("SN-AAA-");

        mvc.perform(put("/api/v1/passports/" + p.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated",
                                  "model": "M2",
                                  "serialPrefix": "SN-AAA-",
                                  "fromSerialNumber": 10,
                                  "toSerialNumber": 200,
                                  "warrantyMonths": 36
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePassport_Success_ShouldReturn200() throws Exception {
        Passport p = seedPassport("SN-AAA-");

        mvc.perform(put("/api/v1/passports/" + p.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated",
                                  "model": "M2",
                                  "serialPrefix": "SN-AAA-",
                                  "fromSerialNumber": 10,
                                  "toSerialNumber": 200,
                                  "warrantyMonths": 36
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serialPrefix").value("SN-AAA-"))
                .andExpect(jsonPath("$.fromSerialNumber").value(10))
                .andExpect(jsonPath("$.toSerialNumber").value(200))
                .andExpect(jsonPath("$.warrantyMonths").value(36));

        Passport updated = passportRepository.findById(p.getId()).orElseThrow();
        assertEquals("Updated", updated.getName());
        assertEquals("M2", updated.getModel());
        assertEquals(36, updated.getWarrantyMonths());
    }

    @Test
    void updatePassport_NotExistingId_ShouldFail400() throws Exception {
        mvc.perform(put("/api/v1/passports/999999")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated",
                                  "model": "M2",
                                  "serialPrefix": "SN-AAA-",
                                  "fromSerialNumber": 10,
                                  "toSerialNumber": 200,
                                  "warrantyMonths": 36
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // ---------------- GET PAGED ----------------

    @Test
    void getPassports_WithoutToken_ShouldFail401() throws Exception {
        mvc.perform(get("/api/v1/passports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPassports_Success_ShouldReturnCustomPage() throws Exception {
        seedPassport("SN-AAA-");
        seedPassport("SN-BBB-");

        MvcResult res = mvc.perform(get("/api/v1/passports?page=1&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = mapper.readTree(res.getResponse().getContentAsString());

        // както при Device tests – намираме array-а независимо дали се казва content/items/data
        JsonNode listNode =
                root.has("content") ? root.get("content") :
                        root.has("items") ? root.get("items") :
                                root.has("data") ? root.get("data") :
                                        (root.isArray() ? root : null);

        assertNotNull(listNode, "Cannot locate list node in CustomPage JSON");
        assertTrue(listNode.isArray(), "List node is not an array");
        assertTrue(listNode.size() >= 2);
    }

    // ---------------- DELETE ----------------

    @Test
    void deletePassport_WithoutToken_ShouldFail401() throws Exception {
        Passport p = seedPassport("SN-AAA-");

        mvc.perform(delete("/api/v1/passports/" + p.getId()))
                .andExpect(status().isUnauthorized());

        assertTrue(passportRepository.findById(p.getId()).isPresent());
    }

    @Test
    void deletePassport_Success_ShouldReturn200() throws Exception {
        Passport p = seedPassport("SN-AAA-");

        mvc.perform(delete("/api/v1/passports/" + p.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertTrue(passportRepository.findById(p.getId()).isEmpty());
    }

    // ---------------- GET BY SERIAL ID (PUBLIC) ----------------

    @Test
    void getPassportForSerialId_Success_Public_NoTokenNeeded() throws Exception {
        Passport saved = seedPassport("SN-AAA-"); // prefix тук е само за намиране, не се връща в DTO-то

        mvc.perform(get("/api/v1/passports/getBySerialId/SN-AAA-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Seed"))
                .andExpect(jsonPath("$.model").value("SeedModel"));
    }

    @Test
    void getPassportForSerialId_NotFound_ShouldFail400OrWhateverBackendReturns() throws Exception {
        // няма seed -> трябва да хвърли CustomException
        mvc.perform(get("/api/v1/passports/getBySerialId/SN-AAA-001"))
                .andExpect(status().isBadRequest());
    }

    // ---------------- helpers ----------------

    private Passport seedPassport(String serialPrefix) {
        Passport p = Passport.builder()
                .name("Seed")
                .model("SeedModel")
                .serialPrefix(serialPrefix)
                .fromSerialNumber(1)
                .toSerialNumber(999)
                .warrantyMonths(24)
                .build();
        return passportRepository.save(p);
    }
}
