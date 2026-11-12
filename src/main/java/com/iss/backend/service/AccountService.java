package com.iss.backend.service;

import com.iss.backend.entity.Student;
import com.iss.backend.entity.User;
import com.iss.backend.repository.StudentRepository;
import com.iss.backend.repository.UserRepository;
import com.iss.backend.web.Validation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Profile updates and password changes shared by every workspace. */
@Service
public class AccountService {

    public static final int MIN_PASSWORD_LENGTH = 6;

    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PasswordResetService passwordResetService;

    /** @return an error message, or null when the name was saved. */
    public String updateStaffName(User user, String name) {
        String error = Validation.name(name, "Name", 100);
        if (error != null) return error;
        user.setName(name.trim());
        userRepository.save(user);
        return null;
    }

    /** @return an error message, or null when the password was changed. */
    public String changeStaffPassword(User user, String current, String next, String confirm) {
        String error = validate(user.getPassword(), current, next, confirm);
        if (error != null) return error;
        user.setPassword(passwordEncoder.encode(next));
        userRepository.save(user);
        passwordResetService.clearStaff(user.getUserId());
        return null;
    }

    /** @return an error message, or null when the password was changed. */
    public String changeStudentPassword(Student student, String current, String next, String confirm) {
        String error = validate(student.getPassword(), current, next, confirm);
        if (error != null) return error;
        student.setPassword(passwordEncoder.encode(next));
        studentRepository.save(student);
        passwordResetService.clearStudent(student.getStudentId());
        return null;
    }

    private String validate(String encoded, String current, String next, String confirm) {
        if (!passwordEncoder.matches(current, encoded)) {
            return "Your current password is incorrect.";
        }
        if (next == null || next.length() < MIN_PASSWORD_LENGTH) {
            return "The new password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        }
        if (!next.equals(confirm)) {
            return "The new passwords do not match.";
        }
        return null;
    }
}
