package com.iss.backend.service;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Email server settings, managed from Admin > System Settings > Email.
 * Values saved there take effect immediately (no restart). Until they are saved,
 * the spring.mail.* values from application.properties / mail.properties are used.
 */
@Service
public class MailConfigService {

    public static final String HOST = "mail.host";
    public static final String PORT = "mail.port";
    public static final String SECURITY = "mail.security";          // STARTTLS, SSL or NONE
    public static final String USERNAME = "mail.username";
    public static final String PASSWORD = "mail.password";
    public static final String SENDER_NAME = "mail.sender_name";
    public static final String ENABLED = "mail.enabled";

    @Autowired private SettingsService settings;

    @Value("${spring.mail.host:smtp.gmail.com}") private String fileHost;
    @Value("${spring.mail.port:587}") private String filePort;
    @Value("${spring.mail.username:}") private String fileUsername;
    @Value("${spring.mail.password:}") private String filePassword;

    /** The settings currently in use. */
    public record MailConfig(String host, int port, String security, String username, String password,
                             String senderName, boolean enabled) {

        public boolean hasCredentials() {
            return notPlaceholder(username) && notPlaceholder(password);
        }

        public boolean ready() {
            return enabled && hasCredentials() && host != null && !host.isBlank();
        }

        private static boolean notPlaceholder(String v) {
            return v != null && !v.isBlank() && !v.startsWith("YOUR_");
        }
    }

    public MailConfig current() {
        String username = settings.get(USERNAME, fileUsername);
        String password = settings.get(PASSWORD, filePassword);
        int port;
        try {
            port = Integer.parseInt(settings.get(PORT, filePort).trim());
        } catch (NumberFormatException e) {
            port = 587;
        }
        return new MailConfig(
                settings.get(HOST, fileHost).trim(),
                port,
                settings.get(SECURITY, port == 465 ? "SSL" : "STARTTLS"),
                username == null ? "" : username.trim(),
                password == null ? "" : password,
                settings.get(SENDER_NAME, ""),
                !"false".equals(settings.get(ENABLED, "true")));
    }

    /** Saves the settings; a blank password keeps the one already saved. */
    public void save(String host, int port, String security, String username, String password,
                     String senderName, boolean enabled) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(HOST, host);
        values.put(PORT, String.valueOf(port));
        values.put(SECURITY, switch (security == null ? "" : security.toUpperCase()) {
            case "SSL" -> "SSL";
            case "NONE" -> "NONE";      // plain connection, for a local/internal mail relay only
            default -> "STARTTLS";
        });
        values.put(USERNAME, username);
        values.put(SENDER_NAME, senderName == null ? "" : senderName);
        values.put(ENABLED, String.valueOf(enabled));
        if (password != null && !password.isBlank()) {
            // Gmail shows App Passwords in groups of four ("abcd efgh ..."); the spaces are not part of it
            values.put(PASSWORD, password.replace(" ", ""));
        }
        settings.saveAll(values);
    }

    /** A mail sender built from the current settings. */
    public JavaMailSenderImpl sender() {
        MailConfig c = current();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(c.host());
        sender.setPort(c.port());
        sender.setUsername(c.username());
        sender.setPassword(c.password());
        sender.setDefaultEncoding("UTF-8");

        Properties p = sender.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", "true");
        if ("SSL".equals(c.security())) {
            p.put("mail.smtp.ssl.enable", "true");
        } else if (!"NONE".equals(c.security())) {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        }
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "10000");
        p.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    /** Logs in to the mail server without sending anything. */
    public void testConnection() throws MessagingException {
        sender().testConnection();
    }

    /** A plain-language explanation of a mail error, for the administrator. */
    public static String explain(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AuthenticationFailedException
                    || (t.getMessage() != null && t.getMessage().contains("535"))) {
                return "Gmail rejected the login. Use an App Password (not the normal Gmail password) "
                        + "and make sure 2-Step Verification is turned on for the account.";
            }
            if (t instanceof UnknownHostException) {
                return "The mail server could not be found. Check the SMTP host and the internet connection.";
            }
            if (t instanceof ConnectException || t instanceof SocketTimeoutException) {
                return "Could not connect to the mail server. Check the SMTP host, port and security setting, "
                        + "and that this computer is connected to the internet.";
            }
        }
        return "The mail server returned an error: " + (e.getMessage() == null ? e.toString() : e.getMessage());
    }
}
