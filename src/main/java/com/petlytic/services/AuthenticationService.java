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
import com.petlytic.models.enums.ErrorCode;
import com.petlytic.models.enums.ResourceType;
import com.petlytic.models.enums.Role;
import com.petlytic.repositories.RefreshTokenRepository;
import com.petlytic.repositories.UserRepository;
import com.petlytic.repositories.VerificationTokenRepository;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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

    @Value("${user.default-avatar}")
    private String defaultAvatarUrl;

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
                throw new AppException(ErrorCode.GOOGLE_LOGIN_FAILED);
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
            log.error("Google Login Error: ", e);
            throw new AppException(ErrorCode.GOOGLE_LOGIN_FAILED);
        }
    }

    @Transactional
    public UserResponseDTO signup(RegisterUserDTO input) {
        if(userRepository.existsByEmail(input.getEmail())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        User user = userMapper.toUser(input);
        user.setPassword(passwordEncoder.encode(input.getPassword()));
        user.setRole(Role.CUSTOMER);
        user.setActive(false);
        if (user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty()) {
            user.setAvatarUrl(defaultAvatarUrl);
        }

        User savedUser = userRepository.save(user);

        String code = generateVerificationCode();

        VerificationToken token = VerificationToken.builder()
                .user(savedUser)
                .verificationCode(code)
                .verificationExpiration(LocalDateTime.now().plusMinutes(15))
                .build();
        verificationTokenRepository.save(token);

        try {
            emailService.sendVerificationEmail(user.getEmail(), code);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}", user.getEmail(), e);

            throw new AppException(ErrorCode.MAIL_VERIFICATION_FAILED);
        }

        return userMapper.toUserResponse(savedUser);
    }

    public LoginResponse refreshToken(RefreshTokenDTO input) {
        String incomingRefreshToken = input.getRefreshToken();
        String userEmail = jwtService.extractUsername(incomingRefreshToken);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, ResourceType.USER));

        RefreshToken currentToken = refreshTokenRepository.findByToken(incomingRefreshToken)
                .orElseThrow(() -> new AppException(ErrorCode.TOKEN_INVALID));

        if (currentToken.isRevoked()) {
            revokeAllUserTokens(user);
            throw new AppException(ErrorCode.TOKEN_REVOKED);
        }

        if (currentToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
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
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            input.getEmail(),
                            input.getPassword()
                    )
            );

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

        } catch (DisabledException e) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_VERIFIED);

        } catch (BadCredentialsException e) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Transactional
    public void verifyUser(VerifyUserDTO input) {
        User user = userRepository.findByEmail(input.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, ResourceType.USER));

        VerificationToken token = verificationTokenRepository.findByUserAndVerificationCode(user, input.getVerificationCode())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_KEY, "Verification Code"));

        if (token.getVerificationExpiration().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.CODE_EXPIRED);
        }

        user.setActive(true);
        userRepository.save(user);
        verificationTokenRepository.delete(token);
    }

    @Transactional
    public void resendVerificationCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, ResourceType.USER));

        if (user.isEnabled()) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_VERIFIED);
        }

        verificationTokenRepository.deleteAllByUser(user);

        String code = generateVerificationCode();
        VerificationToken newToken = VerificationToken.builder()
                .user(user)
                .verificationCode(code)
                .verificationExpiration(LocalDateTime.now().plusMinutes(15))
                .build();
        verificationTokenRepository.save(newToken);

        emailService.sendVerificationEmail(user.getEmail(), code);
    }

    public void logout(String refreshToken) {
        var storedToken = refreshTokenRepository.findByToken(refreshToken)
                .orElse(null);

        if (storedToken != null) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
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