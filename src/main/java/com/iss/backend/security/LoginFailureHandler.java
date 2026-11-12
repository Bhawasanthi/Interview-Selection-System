package com.iss.backend.security;

import com.iss.backend.service.ActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Records every failed login for Admin > Site Activity and sends the person back to the login box.
 * After too many wrong passwords the message says to wait (see ActivityService.isLockedOut).
 */
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    @Autowired private ActivityService activityService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String username = request.getParameter("username");
        String ip = request.getRemoteAddr();
        boolean locked = exception instanceof LockedException;

        activityService.recordLogin(locked ? ActivityService.LOCKED : ActivityService.FAILED,
                username == null ? null : username.trim(), null, ip, request.getHeader("User-Agent"));

        // Checked after recording, so the attempt that reaches the limit already shows the wait message
        boolean blocked = locked || activityService.isLockedOut(username, ip);
        response.sendRedirect(request.getContextPath() + (blocked ? "/?loginError=locked" : "/?loginError"));
    }
}
