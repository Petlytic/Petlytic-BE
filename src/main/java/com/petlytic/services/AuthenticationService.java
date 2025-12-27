package com.petlytic.services;

import com.petlytic.dtos.requests.LoginUserDTO;
import com.petlytic.dtos.requests.RefreshTokenDTO;
import com.petlytic.dtos.requests.RegisterUserDTO;
import com.petlytic.dtos.requests.VerifyUserDTO;
import com.petlytic.dtos.responses.LoginResponse;
import com.petlytic.exceptions.EmailAlreadyExistsException;
import com.petlytic.exceptions.ResourceNotFoundException;
import com.petlytic.models.RefreshToken;
import com.petlytic.models.User;
import com.petlytic.models.VerificationToken;
import com.petlytic.models.enums.ResourceType;
import com.petlytic.models.enums.Role;
import com.petlytic.repositories.RefreshTokenRepository;
import com.petlytic.repositories.UserRepository;
import com.petlytic.repositories.VerificationTokenRepository;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final JwtService jwtService;

    @Transactional
    public User signup(RegisterUserDTO input) {
        if(userRepository.existsByEmail(input.getEmail())) {
            throw new EmailAlreadyExistsException("Email đã tồn tại: " + input.getEmail());
        }

        User user = User.builder()
                .username(input.getUsername())
                .email(input.getEmail())
                .password(passwordEncoder.encode(input.getPassword()))
                .role(Role.CUSTOMER)
                .active(false)
                .build();

        User savedUser = userRepository.save(user);
        String code = generateVerificationCode();
        VerificationToken token = VerificationToken.builder()
                .user(savedUser)
                .verificationCode(code)
                .verificationExpiration(LocalDateTime.now().plusMinutes(15))
                .build();
        verificationTokenRepository.save(token);

        sendVerificationEmail(user, code);

        return savedUser;
    }

    public LoginResponse refreshToken(RefreshTokenDTO input) {
        String incomingRefreshToken = input.getRefreshToken();

        String userEmail = jwtService.extractUsername(incomingRefreshToken);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.USER, "email",  userEmail));

        RefreshToken currentToken = refreshTokenRepository.findByToken(incomingRefreshToken)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        if (currentToken.isRevoked()) {
            revokeAllUserTokens(user);
            throw new RuntimeException("Refresh token was revoked. Please login again.");
        }

        if (currentToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refresh token expired");
        }

        currentToken.setRevoked(true);
        refreshTokenRepository.save(currentToken);

        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        saveUserRefreshToken(user, newRefreshToken);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtService.getExpirationTime())
                .build();
    }

    private void saveUserRefreshToken(User user, String jwtToken) {
        var token = RefreshToken.builder()
                .user(user)
                .token(jwtToken)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(token);
    }

    private void revokeAllUserTokens(User user) {
        var validUserTokens = refreshTokenRepository.findAllValidTokenByUser(user.getId());
        if (validUserTokens.isEmpty()) return;

        validUserTokens.forEach(token -> {
            token.setRevoked(true);
        });
        refreshTokenRepository.saveAll(validUserTokens);
    }

    public LoginResponse authenticate(LoginUserDTO input) {
        User user = userRepository.findByEmail(input.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.USER, "email",  input.getEmail()));

        if (!user.isEnabled()) {
            throw new RuntimeException("Account not verified. Please verify your account.");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        input.getEmail(),
                        input.getPassword()
                )
        );

        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        revokeAllUserTokens(user);
        saveUserRefreshToken(user, refreshToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getExpirationTime())
                .build();
    }

    @Transactional
    public void verifyUser(VerifyUserDTO input) {
        User user = userRepository.findByEmail(input.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.USER, "email",  input.getEmail()));

        VerificationToken token = verificationTokenRepository.findByUserAndVerificationCode(user, input.getVerificationCode())
                .orElseThrow(() -> new RuntimeException("Invalid verification code"));

        if (token.getVerificationExpiration().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Verification code has expired");
        }

        user.setActive(true);
        userRepository.save(user);

        verificationTokenRepository.delete(token);
    }

    @Transactional
    public void resendVerificationCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.USER, "email",  email));

        if (user.isEnabled()) {
            throw new RuntimeException("Account is already verified");
        }

        verificationTokenRepository.deleteAllByUser(user);

        String code = generateVerificationCode();
        VerificationToken newToken = VerificationToken.builder()
                .user(user)
                .verificationCode(code)
                .verificationExpiration(LocalDateTime.now().plusMinutes(15))
                .build();
        verificationTokenRepository.save(newToken);

        sendVerificationEmail(user, code);
    }

    private void sendVerificationEmail(User user, String verificationCode) {
        String subject = "Account Verification";

        String htmlMessage = "<html>"
                + "<body style=\"font-family: Arial, sans-serif;\">"
                + "<div style=\"background-color: #f5f5f5; padding: 20px;\">"
                + "<h2 style=\"color: #333;\">Welcome to our app!</h2>"
                + "<p style=\"font-size: 16px;\">Please enter the verification code below to continue:</p>"
                + "<div style=\"background-color: #fff; padding: 20px; border-radius: 5px; box-shadow: 0 0 10px rgba(0,0,0,0.1);\">"
                + "<h3 style=\"color: #333;\">Verification Code:</h3>"
                + "<p style=\"font-size: 18px; font-weight: bold; color: #007bff;\">" + verificationCode + "</p>"
                + "</div>"
                + "</div>"
                + "</body>"
                + "</html>";

        try {
            emailService.sendVerificationEmail(user.getEmail(), subject, htmlMessage);
        } catch (MessagingException e) {
            // Handle email sending exception
            e.printStackTrace();
        }
    }

    private String generateVerificationCode() {
        Random random = new Random();
        int code = random.nextInt(900000) + 100000;
        return String.valueOf(code);
    }
}