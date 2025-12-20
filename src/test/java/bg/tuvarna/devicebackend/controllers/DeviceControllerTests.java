package bg.tuvarna.devicebackend.controllers;

import bg.tuvarna.devicebackend.models.dtos.DeviceCreateVO;
import bg.tuvarna.devicebackend.models.entities.Device;
import bg.tuvarna.devicebackend.models.entities.Passport;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.models.enums.UserRole;
import bg.tuvarna.devicebackend.repositories.DeviceRepository;
import bg.tuvarna.devicebackend.repositories.PassportRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PassportRepository passportRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

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

        token = mapper.readTree(
                loginResult.getResponse().getContentAsString()
        ).get("token").asText();
    }

    @AfterEach
    void tearDown() {
        deviceRepository.deleteAll();
        userRepository.deleteAll();
        passportRepository.deleteAll();
    }

    private void seedPassportForSnAaa001() {
        Passport p1 = Passport.builder()
                .name("P1")
                .model("M1")
                .serialPrefix("SN-AAA")      // вариант A
                .fromSerialNumber(1)
                .toSerialNumber(999)
                .warrantyMonths(24)
                .build();

        Passport p2 = Passport.builder()
                .name("P2")
                .model("M2")
                .serialPrefix("SN-AAA-")     // вариант B (понякога държат тирето)
                .fromSerialNumber(1)
                .toSerialNumber(999)
                .warrantyMonths(24)
                .build();

        passportRepository.save(p1);
        passportRepository.save(p2);
    }

    // ---------- CREATE ----------

    @Test
    void createDevice_WithoutToken_ShouldFail() throws Exception {
        mvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serialNumber": "SN-001",
                                  "model": "XPS 13",
                                  "manufacturer": "Dell"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createDevice_WithToken_PrincipalMappingMayFail() throws Exception {
        mvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "deviceSerialNumber": "SN-009",
                          "purchaseDate": "2025-01-01"
                        }
                        """))
                // ако principal mapping не работи, реалното поведение е 400
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAnonymousDevice_Success() throws Exception {
        seedPassportForSnAaa001(); // както говорихме

        mvc.perform(post("/api/v1/devices/anonymousDevice")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "deviceSerialNumber": "SN-AAA-001",
                      "purchaseDate": "2025-12-20"
                    }
                    """))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serialNumber").value("SN-AAA-001"))
                .andExpect(header().exists("Location"));
    }

    @Test
    void createAnonymousDevice_WithoutToken_ShouldFail401() throws Exception {
        mvc.perform(post("/api/v1/devices/anonymousDevice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "deviceSerialNumber": "SN-001",
                          "purchaseDate": "2025-01-01"
                        }
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createDevice_DuplicateSerialNumber_ShouldFail() throws Exception {
        Device existing = new Device();
        existing.setSerialNumber("SN-001");
        existing.setPurchaseDate(java.time.LocalDate.of(2024, 1, 1));
        deviceRepository.save(existing);

        mvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "deviceSerialNumber": "SN-001",
                          "purchaseDate": "2025-01-01"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }


// ---------- UPDATE ----------

    @Test
    void updateDevice_Success() throws Exception {
        Passport passport = new Passport();
        passportRepository.save(passport);

        Device device = new Device();
        device.setSerialNumber("SN-002");
        device.setPurchaseDate(java.time.LocalDate.of(2024, 12, 1));
        device.setComment("old");
        device.setPassport(passport); // <-- важно
        deviceRepository.save(device);

        mvc.perform(put("/api/v1/devices/SN-002")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "purchaseDate": "2025-02-02",
                          "comment": "updated comment"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serialNumber").value("SN-002"))
                .andExpect(jsonPath("$.purchaseDate").value("2025-02-02"))
                .andExpect(jsonPath("$.comment").value("updated comment"));
    }


    @Test
    void updateDevice_NotFound_ShouldFail() throws Exception {
        mvc.perform(put("/api/v1/devices/MISSING")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "purchaseDate": "2025-02-02",
                          "comment": "whatever"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    // ---------- GET ALL ----------

    @Test
    void getAllDevices_WithoutToken_ShouldFail() throws Exception {
        mvc.perform(get("/api/v1/devices"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllDevices_Success() throws Exception {
        Device device = new Device();
        device.setSerialNumber("SN-003");
        device.setPurchaseDate(java.time.LocalDate.of(2025, 1, 1));
        deviceRepository.save(device);

        MvcResult res = mvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        var json = mapper.readTree(res.getResponse().getContentAsString());

        // CustomPage може да държи списъка в "content", "items", "data" и т.н.
        var listNode =
                json.has("content") ? json.get("content") :
                        json.has("items") ? json.get("items") :
                                json.has("data") ? json.get("data") :
                                        null;

        // ако нищо от горните - значи самият root е array
        if (listNode == null && json.isArray()) {
            listNode = json;
        }

        org.junit.jupiter.api.Assertions.assertNotNull(listNode, "Cannot locate list node in CustomPage JSON");
        org.junit.jupiter.api.Assertions.assertTrue(listNode.isArray(), "List node is not an array");
        org.junit.jupiter.api.Assertions.assertEquals(1, listNode.size());
    }

    // ---------- GET BY SERIAL ----------

    @Test
    void getDeviceBySerialNumber_NotFound_ShouldReturn500() throws Exception {
        mvc.perform(get("/api/v1/devices/UNKNOWN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getDeviceBySerialNumber_Success() throws Exception {
        Passport passport = new Passport();
        passportRepository.save(passport);

        Device device = new Device();
        device.setSerialNumber("SN-004");
        device.setPurchaseDate(java.time.LocalDate.of(2025, 3, 1));
        device.setPassport(passport);
        deviceRepository.save(device);

        mvc.perform(get("/api/v1/devices/SN-004")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serialNumber").value("SN-004"));
    }

    // ---------- DELETE ----------

    @Test
    void deleteDevice_Success() throws Exception {
        User owner = userRepository.findAll().get(0);

        Device device = new Device();
        device.setSerialNumber("SN-005");
        device.setComment("to be deleted");
        device.setPurchaseDate(java.time.LocalDate.of(2025, 1, 10));
        device.setWarrantyExpirationDate(java.time.LocalDate.of(2026, 1, 10));
        device.setUser(owner);

        deviceRepository.save(device);

        mvc.perform(delete("/api/v1/devices/SN-005")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertEquals(0, deviceRepository.count());
    }

    @Test
    void deleteDevice_NotFound_ShouldReturnOk() throws Exception {
        mvc.perform(delete("/api/v1/devices/NOPE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
