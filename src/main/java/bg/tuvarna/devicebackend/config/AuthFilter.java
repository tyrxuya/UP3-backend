package bg.tuvarna.devicebackend.config;

import bg.tuvarna.devicebackend.controllers.exceptions.CustomException;
import bg.tuvarna.devicebackend.controllers.exceptions.ErrorCode;
import bg.tuvarna.devicebackend.controllers.exceptions.ErrorResponse;
import bg.tuvarna.devicebackend.models.dtos.AuthResponseDTO;
import bg.tuvarna.devicebackend.models.dtos.UserLoginDTO;
import bg.tuvarna.devicebackend.models.dtos.UserVO;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

@Configuration
@RequiredArgsConstructor
public class AuthFilter {
    private final AuthenticationManager manager;
    private final UserService userService;
    private final JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;

    @Bean(name = "CustomAuthFilter")
    public AuthenticationFilter authFilter() {
        AuthenticationConverter authenticationConverter = this::authConverter;
        AuthenticationFilter filter = new AuthenticationFilter(manager, authenticationConverter);
        filter.setRequestMatcher(AuthFilter::matches);
        filter.setSuccessHandler(this::successHandler);
        filter.setFailureHandler(this::failureHandler);
        return filter;
    }

    private void successHandler(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Authentication authentication) {
        if (authentication.isAuthenticated()) {
            User principal = (User) authentication.getPrincipal();
            String token = jwtService.generateToken(userService.getUserById(principal.getId()));
            UserVO userVO = new UserVO(principal);
            AuthResponseDTO responseDTO = new AuthResponseDTO(token, userVO);
            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
            httpServletResponse.setContentType("application/json");
            try {
                objectMapper.writeValue(httpServletResponse.getWriter(), responseDTO);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write auth response", e);
            }
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    private void failureHandler(HttpServletRequest request,
                                HttpServletResponse response,
                                AuthenticationException e) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");

        ErrorResponse body = new ErrorResponse(
                new CustomException("Wrong credentials!", ErrorCode.WrongCredentials)
        );

        objectMapper.writeValue(response.getWriter(), body);
    }

    private static boolean matches(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getMethod().equals("POST") && httpServletRequest.getRequestURI().equals("/api/v1/users/login");
    }

    private Authentication authConverter(HttpServletRequest request) {
        try {
            ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
            Gson gson = new Gson();
            UserLoginDTO userLoginDTO = gson.fromJson(requestWrapper.getReader(), UserLoginDTO.class);
            User user = userService.getUserByUsername(userLoginDTO.getUsername());
            return new UsernamePasswordAuthenticationToken(user.getId(), userLoginDTO.getPassword());
        } catch (IOException | CustomException e) {
            throw new AuthenticationServiceException("Wrong credentials!");
        }
    }
}