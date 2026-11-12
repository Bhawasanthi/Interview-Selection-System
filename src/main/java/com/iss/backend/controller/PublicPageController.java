package com.iss.backend.controller;

import com.iss.backend.service.EmailService;
import com.iss.backend.service.PasswordResetService;
import com.iss.backend.web.RateLimiter;
import com.iss.backend.web.Validation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;

@Controller
public class PublicPageController {

    @Autowired private EmailService emailService;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private RateLimiter rateLimiter;

    /** Site address for links in emails. Taken from configuration, never from the request's Host header. */
    @Value("${app.base-url}") private String baseUrl;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    // The login form lives in a modal on the public pages
    @GetMapping("/login")
    public String login() {
        return "redirect:/?login";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String usernameOrEmail, HttpServletRequest request, RedirectAttributes ra) {
        if (!rateLimiter.allow("forgot", request.getRemoteAddr(), 5, Duration.ofMinutes(15))) {
            ra.addFlashAttribute("forgotValue", usernameOrEmail);
            ra.addFlashAttribute("forgotError", "Too many requests. Please wait 15 minutes and try again.");
            return "redirect:/?forgot";
        }
        PasswordResetService.Result result = passwordResetService.requestReset(usernameOrEmail, baseUrl);

        ra.addFlashAttribute("forgotValue", usernameOrEmail);
        // The same message whether or not the account exists, so the form cannot be used to find usernames or emails
        if (result.outcome() == PasswordResetService.Outcome.EMAIL_UNAVAILABLE) {
            ra.addFlashAttribute("forgotError",
                    "Password reset by email is not available right now. Please contact the school administration.");
        } else {
            ra.addFlashAttribute("forgotSent",
                    "If this matches an account, a password reset link has been sent to its email address "
                            + "(it expires in " + PasswordResetService.LINK_VALID_MINUTES + " minutes), or the request "
                            + "has been passed to the school administration. Please check your inbox and spam folder.");
        }
        return "redirect:/?forgot";
    }

    /** Page opened from the reset link in the email. */
    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String token, Model model) {
        PasswordResetService.LinkTarget target = passwordResetService.findLink(token);
        model.addAttribute("token", token);
        model.addAttribute("target", target);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                RedirectAttributes ra) {
        String error = passwordResetService.resetWithLink(token, newPassword, confirmPassword);
        if (error != null) {
            ra.addFlashAttribute("resetError", error);
            return "redirect:/reset-password?token=" + token;
        }
        ra.addFlashAttribute("resetDone", "Your password has been changed. Please log in with your new password.");
        return "redirect:/?login";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }

    @PostMapping("/contact")
    public String sendMessage(@RequestParam String fullName,
                              @RequestParam String email,
                              @RequestParam String subject,
                              @RequestParam String message,
                              HttpServletRequest request,
                              RedirectAttributes ra) {
        String error = Validation.first(
                Validation.text(fullName, "Full Name", 150),
                Validation.email(email, "Email address"),
                Validation.text(subject, "Subject", 150),
                Validation.text(message, "Message", 5000));
        if (error != null) {
            ra.addFlashAttribute("contactError", error);
            return "redirect:/contact";
        }
        if (!rateLimiter.allow("contact", request.getRemoteAddr(), 5, Duration.ofHours(1))) {
            ra.addFlashAttribute("contactError", "You have sent several messages already. Please wait an hour or call the school.");
            return "redirect:/contact";
        }
        try {
            emailService.sendContactMessage(fullName.trim(), email.trim(), subject.trim(), message.trim());
            ra.addFlashAttribute("contactSuccess", "Thank you, " + fullName + ". Your message has been sent.");
        } catch (Exception e) {
            ra.addFlashAttribute("contactError",
                    "Your message could not be sent right now. Please call the school on +94 47 223 0202 or email embpresidentcollege@gmail.com.");
        }
        return "redirect:/contact";
    }

    @GetMapping("/schools")
    public String schools() {
        return "schools";
    }
}
