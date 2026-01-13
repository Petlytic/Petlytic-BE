package com.petlytic.services;

import com.petlytic.exceptions.AppException;
import com.petlytic.models.User;
import com.petlytic.models.enums.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {
    @Autowired
    private JavaMailSender mailSender;

    public void sendHtmlEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Send email failed to {}", to, e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    @Async
    public void sendVerificationEmail(String to, String code) {
        String subject = "Account Verification";
        String html = buildVerificationTemplate(code);

        sendHtmlEmail(to, subject, html);
    }

    private String buildVerificationTemplate(String code) {
        return """
            <html>
              <body style="font-family: Arial, sans-serif;">
                <div style="background:#f5f5f5;padding:20px">
                  <h2>Welcome to Petlytic 🐾</h2>
                  <p>Please enter the verification code below:</p>
                  <div style="background:#fff;padding:20px;border-radius:6px">
                    <h3>Your verification code</h3>
                    <p style="font-size:20px;font-weight:bold;color:#007bff;">
                      %s
                    </p>
                  </div>
                </div>
              </body>
            </html>
        """.formatted(code);
    }
}
