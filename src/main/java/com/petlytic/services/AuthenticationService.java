package com.petlytic.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.petlytic.dtos.requests.*;
import com.petlytic.dtos.responses.LoginResponse;
import com.petlytic.dtos.responses.UserResponseDTO;
import com.petlytic.exceptions.*;
import com.petlytic.mapper.UserMapper;
import com.petlytic.models.RefreshToken;
import com.petlytic.models.User;
import com.petlytic.models.VerificationToken;
import com.petlytic.models.enums.ResourceType;
import com.petlytic.models.enums.Role;
import com.petlytic.repositories.RefreshTokenRepository;
import com.petlytic.repositories.UserRepository;
import com.petlytic.repositories.VerificationTokenRepository;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.Collections;
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
    private final UserMapper userMapper;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    public void init() {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    @Transactional
    public LoginResponse loginWithGoogle(GoogleLoginDTO input) {
        try {
            GoogleIdToken idToken = verifier.verify(input.getIdToken());

            if (idToken == null) {
                throw new BadCredentialsException("Invalid Google ID Token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String avatarUrl = (String) payload.get("picture");

            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                user = User.builder()
                        .username(name)
                        .email(email)
                        .password(null)
                        .role(Role.CUSTOMER)
                        .avatarUrl(avatarUrl)
                        .active(true)
                        .build();
                user = userRepository.save(user);
            } else {
                boolean isChanged = false;

                if (!user.isEnabled()) {
                    user.setActive(true);
                    isChanged = true;
                }

                if (avatarUrl != null && !avatarUrl.equals(user.getAvatarUrl())) {
                    user.setAvatarUrl(avatarUrl);
                    isChanged = true;
                }

                if (isChanged) {
                    userRepository.save(user);
                }
            }

            String accessToken = jwtService.generateToken(user);
            String refreshToken = jwtService.generateRefreshToken(user);

            revokeAllUserTokens(user);
            saveUserRefreshToken(user, refreshToken);

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(jwtService.getExpirationTime())
                    .build();

        } catch (GeneralSecurityException | IOException e) {
            throw new BadCredentialsException("Google verification failed");
        } catch (Exception e) {
            throw new RuntimeException("Internal Error during Google Login", e);
        }
    }

    @Transactional
    public UserResponseDTO signup(RegisterUserDTO input) {
        if(userRepository.existsByEmail(input.getEmail())) {
            throw new EmailAlreadyExistsException("Email existed: " + input.getEmail());
        }

        User user = userMapper.toUser(input);
        user.setPassword(passwordEncoder.encode(input.getPassword()));
        user.setRole(Role.CUSTOMER);
        user.setActive(false);

        User savedUser = userRepository.save(user);
        String code = generateVerificationCode();
        VerificationToken token = VerificationToken.builder()
                .user(savedUser)
                .verificationCode(code)
                .verificationExpiration(LocalDateTime.now().plusMinutes(15))
                .build();
        verificationTokenRepository.save(token);

        sendVerificationEmail(user, code);

        return userMapper.toUserResponse(savedUser);
    }

    public LoginResponse refreshToken(RefreshTokenDTO input) {
        String incomingRefreshToken = input.getRefreshToken();
        String userEmail = jwtService.extractUsername(incomingRefreshToken);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.USER, "email",  userEmail));

        RefreshToken currentToken = refreshTokenRepository.findByToken(incomingRefreshToken)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.TOKEN, "token", incomingRefreshToken));

        if (currentToken.isRevoked()) {
            revokeAllUserTokens(user);
            throw new BadCredentialsException("Refresh token was revoked. Please login again.");
        }

        if (currentToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RefreshTokenExpiredException("Refresh token expired");
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

    public LoginResponse authenticate(LoginUserDTO input) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            input.getEmail(),
                            input.getPassword()
                    )
            );
        } catch (DisabledException e) {
            throw new AccountNotVerifiedException("Account not activated. Please check your email.");
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Email or password is not correct.");
        }

        User user = (User) authentication.getPrincipal();

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
                .orElseThrow(() -> new BadCredentialsException("Invalid verification code"));

        if (token.getVerificationExpiration().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Verification code has expired");
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
            throw new OperationNotPermittedException("Account is already verified");
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

    private void saveUserRefreshToken(User user, String jwtToken) {
        long expirationInMillis = jwtService.getRefreshTokenExpiration();

        var token = RefreshToken.builder()
                .user(user)
                .token(jwtToken)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusNanos(expirationInMillis * 1_000_000))
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
}