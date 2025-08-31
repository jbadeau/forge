package com.myorg.auth;

import com.myorg.shared.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", StringUtils.capitalize("auth-service"));
        return response;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        if (StringUtils.isEmpty(request.getUsername()) || StringUtils.isEmpty(request.getPassword())) {
            response.put("success", false);
            response.put("message", "Username and password are required");
            return response;
        }
        
        // Mock authentication logic
        if ("demo".equals(request.getUsername()) && "password".equals(request.getPassword())) {
            response.put("success", true);
            response.put("message", "Authentication successful");
            response.put("token", "mock-jwt-token-" + StringUtils.reverse(request.getUsername()));
        } else {
            response.put("success", false);
            response.put("message", "Invalid credentials");
        }
        
        return response;
    }

    public static class LoginRequest {
        private String username;
        private String password;
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}