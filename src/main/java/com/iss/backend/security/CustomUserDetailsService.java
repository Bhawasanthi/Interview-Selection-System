package com.iss.backend.security;

import com.iss.backend.entity.Student;
import com.iss.backend.entity.User;
import com.iss.backend.repository.StudentRepository;
import com.iss.backend.repository.UserRepository;
import com.iss.backend.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ActivityService activityService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // Too many wrong passwords recently: the account is reported as locked (checked before the password)
        boolean locked = activityService.isLockedOut(username, clientIp());

        // 1. Check staff (user) table first
        User staff = userRepository.findByUserName(username);
        if (staff != null) {
            String roleName = staff.getRole().getRoleName()
                    .toUpperCase().replace(" ", "_");
            return org.springframework.security.core.userdetails.User.withUsername(staff.getUserName())
                    .password(staff.getPassword())
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + roleName)))
                    .accountLocked(locked)
                    .build();
        }

        // 2. Otherwise check student table
        Student student = studentRepository.findByUsername(username);
        if (student != null) {
            return org.springframework.security.core.userdetails.User.withUsername(student.getUsername())
                    .password(student.getPassword())
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))
                    .accountLocked(locked)
                    .build();
        }

        throw new UsernameNotFoundException("No account found with username: " + username);
    }

    private static String clientIp() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest().getRemoteAddr() : null;
    }
}
