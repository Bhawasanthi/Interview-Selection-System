package com.iss.backend.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Automatic emails (Spring Boot Mail). Every letter is built once here and used
 * both for the email and for the copy shown in the Student Portal / previews.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Public school address that receives messages from the website's Contact Us form. */
    public static final String SCHOOL_EMAIL = "embpresidentcollege@gmail.com";

    @Autowired private SettingsService settingsService;
    @Autowired private MailConfigService mailConfig;

    /** True when an email account is saved and sending is switched on (Admin > System Settings > Email). */
    public boolean isConfigured() {
        return mailConfig.current().ready();
    }

    public String senderAddress() {
        return isConfigured() ? mailConfig.current().username() : null;
    }



    public String interviewLetter(String schoolName, String schoolAddress, String studentName,
                                  String stream, String date, String time, String venue) {
        String year = settingsService.letterExamYear();
        return "The Principal,\n" +
                schoolName + "\n" +
                schoolAddress + "\n\n" +

                "Date: " + LocalDate.now().format(DMY) + "\n\n" +

                "To: Mother / Father / Guardian\n\n" +

                "Interview Selection for G.C.E. Advanced Level " + year + "\n\n" +

                "Dear Parent / Guardian,\n\n" +

                "I am pleased to inform you that " + studentName + ", who applied for admission to " + schoolName +
                " for the G.C.E. Advanced Level " + year + ", has been selected to participate in the interview for the " +
                stream + " stream.\n\n" +

                "Accordingly, the interview will be held as follows:\n\n" +

                "Date: " + date + "\n" +
                "Time: " + time + "\n" +
                "Venue: " + venue + "\n\n" +

                "You are kindly requested to bring the student to the school on the above date and time " +
                "to participate in the interview process.\n\n" +

                "Documents to be brought\n\n" +
                "1. Original Birth Certificate and a certified photocopy.\n" +
                "2. Original G.C.E. Ordinary Level (O/L) Result Certificate and a photocopy.\n" +
                "3. School Leaving Certificate / Character Certificate, if applicable.\n" +
                "4. Certificates of extracurricular activities and other qualifications, if applicable.\n" +
                "5. Any other documents relevant to the admission process.\n\n" +

                "Important Instructions\n\n" +
                "1. The student should arrive at least 15 minutes before the scheduled interview time.\n" +
                "2. The student should be dressed in appropriate school attire.\n" +
                "3. The student must attend the interview with a parent / guardian.\n" +
                "4. Please bring all required original documents for verification.\n\n" +

                "Your cooperation in attending the interview on the specified date and time is greatly appreciated.\n\n" +

                "Principal\n" +
                schoolName + "\n" +
                schoolAddress;
    }




    public String admissionLetter(String schoolName, String schoolAddress, String stream,
                                  String date, String time, String venue) {
        String year = settingsService.letterExamYear();
        return "The Principal,\n" +
                schoolName + "\n" +
                schoolAddress + "\n\n" +

                "Date: " + LocalDate.now().format(DMY) + "\n\n" +

                "Admission of students for the G.C.E. Advanced Level " + year + ".\n" +
                schoolName + "\n\n" +

                "I am pleased to inform you that you have been selected for the " + stream +
                " stream of the G.C.E. Advanced Level of " + schoolName + " / A.L. " + year + ". " +
                "The recruitment process will be held on " + date + " at " + time + " at " + venue + ", " +
                "so I would like to inform you to come with your mother/father/guardian on that day.\n\n" +

                "1. Original and certified photocopy of birth certificate\n" +
                "2. Rs. 15/- 03 9\"x4\" size envelopes with stamps affixed\n" +
                "* External applicants, student performance index, copy thereof, character certificate and copy\n\n" +

                "Approved Fees :-\n" +
                "* School Development Society Fees - 1100.00\n" +
                "* Exam Fee 150 x 5 - 750.00\n" +
                "* Facilities and service charges 60 x 2 - 120.00\n\n" +

                "Principal\n" +
                schoolName + "\n" +
                schoolAddress;
    }



    public String notQualifiedLetter(String schoolName, String schoolAddress, String studentName, String stream) {
        String year = settingsService.letterExamYear();
        return "The Principal,\n" +
                schoolName + "\n" +
                schoolAddress + "\n\n" +

                "Date: " + LocalDate.now().format(DMY) + "\n\n" +

                "To: Mother / Father / Guardian\n\n" +

                "Interview Selection Result for G.C.E. Advanced Level " + year + "\n\n" +

                "Dear Parent / Guardian,\n\n" +

                "Thank you for the application submitted on behalf of " + studentName + " for admission to the " +
                stream + " stream of " + schoolName + " for the G.C.E. Advanced Level " + year + ".\n\n" +

                "All applications were carefully reviewed against the minimum G.C.E. Ordinary Level results " +
                "required for the " + stream + " stream. We regret to inform you that " + studentName +
                " has not been selected for the interview, as the O/L results submitted do not meet the " +
                "selection conditions for this stream.\n\n" +

                "This decision applies only to the " + stream + " stream at this school. If you believe the " +
                "results in the application were entered incorrectly, please contact the school office with " +
                "the original O/L Result Certificate.\n\n" +

                "We thank you for your interest in " + schoolName + " and wish " + studentName +
                " every success in future studies.\n\n" +

                "Principal\n" +
                schoolName + "\n" +
                schoolAddress;
    }

    /** Optional letter for interviewed students who were not selected. */
    public String regretLetter(String studentName, String stream, String schoolName, String schoolAddress) {
        return "The Principal,\n" +
                schoolName + "\n" +
                schoolAddress + "\n\n" +
                "Date: " + LocalDate.now().format(DMY) + "\n\n" +
                "Dear " + studentName + ",\n\n" +
                "Thank you for applying for the " + stream + " stream at " + schoolName +
                " for the G.C.E. Advanced Level " + settingsService.letterExamYear() + ".\n\n" +
                "We regret to inform you that you have not been selected on this occasion, " +
                "as the number of applicants exceeded the available seats.\n\n" +
                "We wish you the very best in your future studies.\n\n" +
                "Principal\n" +
                schoolName + "\n" +
                schoolAddress;
    }




    public void sendInterviewInvitation(String toEmail, String studentName, String stream,
                                        String schoolName, String schoolAddress,
                                        String date, String time, String venue) {
        sendLetter(toEmail, schoolName,
                "Interview Selection for G.C.E. Advanced Level " + settingsService.letterExamYear() + " - " + stream + " Stream",
                interviewLetter(schoolName, schoolAddress, studentName, stream, date, time, venue));
    }

    public void sendAdmissionLetter(String toEmail, String stream, String schoolName, String schoolAddress,
                                    String date, String time, String venue) {
        sendLetter(toEmail, schoolName,
                "Admission of students for the G.C.E. Advanced Level " + settingsService.letterExamYear() + " - " + stream + " Stream",
                admissionLetter(schoolName, schoolAddress, stream, date, time, venue));
    }

    public void sendNotQualifiedLetter(String toEmail, String studentName, String stream,
                                       String schoolName, String schoolAddress) {
        sendLetter(toEmail, schoolName,
                "Interview Selection Result - G.C.E. Advanced Level " + settingsService.letterExamYear() + " - " + stream + " Stream",
                notQualifiedLetter(schoolName, schoolAddress, studentName, stream));
    }

    public void sendRegretLetter(String toEmail, String studentName, String stream, String schoolName, String schoolAddress) {
        sendLetter(toEmail, schoolName,
                "Application Result - G.C.E. Advanced Level " + settingsService.letterExamYear(),
                regretLetter(studentName, stream, schoolName, schoolAddress));
    }

    /** Password reset link after a "Forgot password" request. */
    public void sendPasswordResetLink(String toEmail, String name, String username, String link, long validMinutes) {
        String body = "Dear " + (name == null || name.isBlank() ? username : name) + ",\n\n" +
                "We received a request to reset the password for your Interview Selection System account.\n\n" +
                "Username: " + username + "\n\n" +
                "Click the link below to choose a new password:\n" +
                link + "\n\n" +
                "This link can be used once and expires in " + validMinutes + " minutes.\n\n" +
                "If you did not request this, you can ignore this email - your password will not change.\n\n" +
                "Interview Selection System";
        sendLetter(toEmail, "Interview Selection System", "Reset your password - Interview Selection System", body);
    }

    /** New temporary password after a "Forgot password" request. */
    public void sendPasswordReset(String toEmail, String name, String username, String tempPassword) {
        String body = "Dear " + (name == null || name.isBlank() ? username : name) + ",\n\n" +
                "We received a request to reset the password for your Interview Selection System account.\n\n" +
                "Username: " + username + "\n" +
                "Temporary password: " + tempPassword + "\n\n" +
                "Please log in with this temporary password. You will be asked to choose a new password straight away.\n\n" +
                "If you did not request this, please contact the school administration immediately.\n\n" +
                "Interview Selection System";
        sendLetter(toEmail, "Interview Selection System", "Password Reset - Interview Selection System", body);
    }

    /** Sends a short message so the administrator can check the email settings. */
    public void sendTestEmail(String to) {
        sendLetter(to, "Interview Selection System", "Test Email - Interview Selection System",
                "This is a test email from the Interview Selection System.\n\n"
                        + "If you received it, interview invitations and admission letters can be emailed to students.");
    }

    public void sendContactMessage(String fromName, String fromEmail, String subject, String body) {
        requireConfigured();
        try {
            JavaMailSenderImpl mailSender = mailConfig.sender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(new InternetAddress(mailSender.getUsername(), "Interview Selection System", "UTF-8"));
            helper.setTo(SCHOOL_EMAIL);
            helper.setReplyTo(fromEmail);
            helper.setSubject("[Website] " + subject);
            helper.setText("From: " + fromName + " <" + fromEmail + ">\n\n" + body);
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("Could not send email: " + e.getMessage(), e);
        }
    }

    /**
     * Sends a letter as a formatted HTML email with a plain-text alternative.
     * The sender name is the school name, e.g. "President College <issforschool@gmail.com>".
     */
    private void sendLetter(String to, String senderName, String subject, String letter) {
        requireConfigured();
        try {
            JavaMailSenderImpl mailSender = mailConfig.sender();
            String fromName = mailConfig.current().senderName().isBlank() ? senderName : mailConfig.current().senderName();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(mailSender.getUsername(), fromName, "UTF-8"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(letter, toHtml(senderName, letter));
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.warn("Email to {} failed: {}", to, e.toString());
            throw new IllegalStateException("Could not send email: " + e.getMessage(), e);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Email is not configured");
        }
    }

    /** Formal letter layout in the school colours; headings in the letter are shown in bold. */
    private static String toHtml(String schoolName, String letter) {
        List<String> headings = List.of("Documents to be brought", "Important Instructions", "Approved Fees :-",
                "Dear Parent / Guardian,");
        StringBuilder body = new StringBuilder();
        for (String line : letter.split("\n", -1)) {
            String safe = HtmlUtils.htmlEscape(line);
            // A line that is just a link (e.g. the password reset link) becomes a button
            if (line.startsWith("http://") || line.startsWith("https://")) {
                body.append("<a href=\"").append(safe).append("\" style=\"display:inline-block;margin:6px 0 10px;")
                        .append("padding:11px 22px;background:#7b1e2e;color:#ffffff;text-decoration:none;border-radius:6px;")
                        .append("font-family:Arial,sans-serif;font-weight:bold;\">Reset Password</a><br>")
                        .append("<span style=\"font-size:12px;color:#777777;word-break:break-all;\">").append(safe).append("</span><br>");
                continue;
            }
            boolean bold = headings.contains(line)
                    || line.startsWith("Interview Selection for G.C.E.")
                    || line.startsWith("Interview Selection Result for G.C.E.")
                    || line.startsWith("Admission of students for the G.C.E.");
            body.append(bold ? "<strong>" + safe + "</strong>" : safe).append("<br>");
        }
        return """
                <!DOCTYPE html>
                <html><body style="margin:0;padding:24px;background:#f4f1ea;font-family:Georgia,'Times New Roman',serif;">
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:680px;margin:0 auto;background:#ffffff;border:1px solid #e6dfd2;">
                  <tr><td style="background:#7b1e2e;border-bottom:5px solid #d4a21c;padding:20px 28px;color:#ffffff;">
                    <div style="font-size:20px;font-weight:bold;">{{SCHOOL}}</div>
                    <div style="font-size:12px;letter-spacing:1px;text-transform:uppercase;color:#f4d67e;margin-top:4px;">Interview Selection System</div>
                  </td></tr>
                  <tr><td style="padding:28px 32px;font-size:15px;line-height:1.7;color:#222222;">{{BODY}}</td></tr>
                  <tr><td style="padding:14px 32px;background:#faf6ee;border-top:1px solid #e6dfd2;font-family:Arial,sans-serif;font-size:12px;color:#777777;">
                    This is an automated message from the Interview Selection System. You can also view this letter by logging in to the Student Portal.
                  </td></tr>
                </table>
                </body></html>
                """
                // replace() rather than formatted(): the CSS contains "%" characters
                .replace("{{SCHOOL}}", HtmlUtils.htmlEscape(schoolName))
                .replace("{{BODY}}", body);
    }
}
