package com.ricozknow.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** SMTP-backed email delivery. Credentials are supplied only through Spring Mail configuration. */
@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {
    private final JavaMailSender mailSender;

    @Value("${ricozknow.email.from:no-reply@ricozknow.com}")
    private String from;

    @Override
    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
