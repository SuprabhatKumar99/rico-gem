package com.ricozknow.notification;

public interface EmailService {
    void send(String to, String subject, String text);
}
