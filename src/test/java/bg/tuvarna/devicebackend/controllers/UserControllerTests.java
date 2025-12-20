package bg.tuvarna.devicebackend.controllers;

import bg.tuvarna.devicebackend.controllers.exceptions.ErrorResponse;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.models.enums.UserRole;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "Az$um_GOSHO123";

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        User user = User.builder()
                .fullName("gosho")
                .email("gosho@abv.bg")
                .password(passwordEncoder.encode(PASSWORD))
                .role(UserRole.USER)
                .build();

        userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    // ---------- REGISTRATION ----------

    @Test
    void userRegistrationFailed_EmailAlreadyTaken() throws Exception {
        MvcResult result = mvc.perform(
                        post("/api/v1/users/registration")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "fullName": "Georgi",
                                          "email": "gosho@abv.bg",
                                          "password": "Az$um_GOSHO123",
                                          "phone": "0899123456"
                                        }
                                        """))
                .andReturn();

        assertEquals(400, result.getResponse().getStatus());

        ErrorResponse error = mapper.readValue(
                result.getResponse().getContentAsString(),
                ErrorResponse.class
        );

        assertEquals("Email already taken", error.getError());
    }

    @Test
    void userRegistrationSuccess() throws Exception {
        mvc.perform(post("/api/v1/users/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Ivan",
                                  "email": "ivan@abv.bg",
                                  "password": "Strong$Pass123",
                                  "phone": "0888123456"
                                }
                                """))
                .andExpect(status().isOk());

        assertEquals(2, userRepository.count());
    }

    // ---------- LOGIN ----------

    @Test
    void userLoginSuccess() throws Exception {
        mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "gosho@abv.bg",
                                  "password": "Az$um_GOSHO123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void userLoginFailed_WrongPassword() throws Exception {
        mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "gosho@abv.bg",
                                  "password": "WRONG_PASSWORD"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userLoginFailed_UserNotFound() throws Exception {
        mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "missing@abv.bg",
                                  "password": "SomePassword123"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ---------- PROTECTED ENDPOINT ----------

    @Test
    void accessProtectedEndpoint_WithoutToken_ShouldFail() throws Exception {
        mvc.perform(get("/api/v1/users/getUser"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessProtectedEndpoint_WithToken_ShouldSucceed() throws Exception {
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

        String token = mapper.readTree(
                loginResult.getResponse().getContentAsString()
        ).get("token").asText();

        assertNotNull(token);

        mvc.perform(get("/api/v1/users/getUser")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("gosho@abv.bg"))
                .andExpect(jsonPath("$.role").value(UserRole.USER.toString()));
    }
}
