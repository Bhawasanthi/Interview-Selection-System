package com.iss.backend.controller;
import com.iss.backend.entity.Student;
import com.iss.backend.repository.StudentRepository;
import com.iss.backend.repository.UserRepository;
import com.iss.backend.service.AccountService;
import com.iss.backend.service.SettingsService;
import com.iss.backend.web.Validation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    @Autowired private StudentRepository studentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SettingsService settingsService;
    @Autowired private PasswordEncoder passwordEncoder;

    @PostMapping("/student-signup")
    public String signup(
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes) {

        if (!settingsService.registrationOpen()) {
            redirectAttributes.addFlashAttribute("signupError", "New student registration is currently closed.");
            return "redirect:/";
        }
        username = Validation.trim(username);
        String error = Validation.first(
                Validation.name(fullName, "Full Name", 150),
                Validation.email(email, "Email address"),
                Validation.username(username),
                Validation.password(password, AccountService.MIN_PASSWORD_LENGTH),
                password.equals(confirmPassword) ? null : "Passwords do not match.");
        if (error != null) {
            redirectAttributes.addFlashAttribute("signupError", error);
            return "redirect:/";
        }
        // Staff usernames are checked too: login looks up staff first, so a clash would lock the student out
        if (studentRepository.findByUsername(username) != null || userRepository.findByUserName(username) != null) {
            redirectAttributes.addFlashAttribute("signupError", "Username already taken.");
            return "redirect:/";
        }

        Student student = new Student();
        student.setFullName(fullName.trim());
        student.setEmail(email.trim());
        student.setUsername(username);
        student.setPassword(passwordEncoder.encode(password));
        studentRepository.save(student);

        redirectAttributes.addFlashAttribute("signupSuccess", "Account created! Please log in.");
        return "redirect:/";
    }
}
