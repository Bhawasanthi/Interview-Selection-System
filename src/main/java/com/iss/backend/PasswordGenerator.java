
package com.iss.backend;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGenerator {

    public static void main(String[] args) {

        BCryptPasswordEncoder passwordEncoder =
                new BCryptPasswordEncoder();

        String password = "Test@123";

        String hash = passwordEncoder.encode(password);

        System.out.println("Password: " + password);
        System.out.println("BCrypt Hash: " + hash);
    }
}
