package com.example.myllm.controller;

import com.example.myllm.model.dto.AuthRequest;
import com.example.myllm.model.dto.LoginResponse;
import com.example.myllm.model.dto.RegisterRequest;
import com.example.myllm.model.entity.User;
import com.example.myllm.repository.UserRepository;
import com.example.myllm.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名不能为空"));
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码至少需要6个字符"));
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "邮箱已被注册"));
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setStatus(1);
        user.setRole("user");
        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername());
        LoginResponse resp = new LoginResponse(
                token, user.getId(), user.getUsername(), user.getNickname(), user.getRole());

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request,
                                   @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
                                   @RequestHeader(value = "X-Real-IP", required = false) String realIp,
                                   jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名不能为空"));
        }

        User user = userRepository.findByUsername(request.getUsername()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            return ResponseEntity.status(403).body(Map.of("error", "账户已被禁用"));
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }

        // Update login info
        user.setLastLoginAt(LocalDateTime.now());
        user.setLoginCount((user.getLoginCount() != null ? user.getLoginCount() : 0) + 1);
        String ip = forwardedFor != null ? forwardedFor : (realIp != null ? realIp : httpRequest.getRemoteAddr());
        user.setLastLoginIp(ip);
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername());
        LoginResponse resp = new LoginResponse(
                token, user.getId(), user.getUsername(), user.getNickname(), user.getRole());

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        User user = (User) authentication.getPrincipal();
        LoginResponse resp = new LoginResponse(
                null, user.getId(), user.getUsername(), user.getNickname(), user.getRole());
        return ResponseEntity.ok(resp);
    }
}
