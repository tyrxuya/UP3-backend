package bg.tuvarna.devicebackend.integration;

import bg.tuvarna.devicebackend.controllers.exceptions.ErrorResponse;
import bg.tuvarna.devicebackend.models.dtos.AuthResponseDTO;
import bg.tuvarna.devicebackend.models.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserAuthenticationIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;

    private static String token;

    private static final String EMAIL = "flow01_gosho@abv.bg";
    private static final String PASSWORD = "Az$um_GOSHO123";

    @BeforeEach
    void init() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test @Order(1)
    void register_Success_200() throws Exception {
        mvc.perform(post("/api/v1/users/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Georgi Ivanov",
                                  "email": "%s",
                                  "phone": "0884985849",
                                  "username": "flow01_user",
                                  "password": "%s"
                                }
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test @Order(2)
    void register_DuplicateEmail_400_WithMessage() throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/users/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Georgi Ivanov",
                                  "email": "%s",
                                  "phone": "0884985849",
                                  "username": "flow01_user2",
                                  "password": "%s"
                                }
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse err = mapper.readValue(res.getResponse().getContentAsString(), ErrorResponse.class);
        assertEquals("Email already taken", err.getError());
    }

    @Test @Order(3)
    void login_Success_200_ReturnsToken() throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponseDTO dto = mapper.readValue(res.getResponse().getContentAsString(), AuthResponseDTO.class);
        token = dto.getToken();
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test @Order(4)
    void login_WrongPassword_401() throws Exception {
        mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "WRONG_PASS"
                                }
                                """.formatted(EMAIL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Wrong credentials!"));
    }

    @Test @Order(5)
    void getUser_WithToken_200_RoleUser() throws Exception {
        mvc.perform(get("/api/v1/users/getUser")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value(UserRole.USER.toString()));
    }
}
