package com.iss.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "student")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer studentId;

    private String email;
    private String fullName;
    private String nameWithInitials;
    private String district;
    private String nic;
    private String indexNumber;
    private String permanentAddress;
    private String phoneNumber;
    private String whatsappNumber;
    private String gender;
    private LocalDate dateOfBirth;
    private String guardianName;
    private String guardianPhone;
    private Integer ageYears;
    private Integer ageMonths;
    private Integer ageDays;
    private String learningMedium;
    private String olSchoolAddress;
    private String olSchoolAdmissionNumber;
    private LocalDate dateEnteredSchool;
    private String gradeEntered;


    @Column(columnDefinition = "TEXT")
    private String extraCurricularActivities;

    private String username;
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Integer getStudentId() { return studentId; }
    public void setStudentId(Integer studentId) { this.studentId = studentId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getNameWithInitials() { return nameWithInitials; }
    public void setNameWithInitials(String nameWithInitials) { this.nameWithInitials = nameWithInitials; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getNic() { return nic; }
    public void setNic(String nic) { this.nic = nic; }

    public String getIndexNumber() { return indexNumber; }
    public void setIndexNumber(String indexNumber) { this.indexNumber = indexNumber; }

    public String getPermanentAddress() { return permanentAddress; }
    public void setPermanentAddress(String permanentAddress) { this.permanentAddress = permanentAddress; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getWhatsappNumber() { return whatsappNumber; }
    public void setWhatsappNumber(String whatsappNumber) { this.whatsappNumber = whatsappNumber; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getGuardianName() { return guardianName; }
    public void setGuardianName(String guardianName) { this.guardianName = guardianName; }

    public String getGuardianPhone() { return guardianPhone; }
    public void setGuardianPhone(String guardianPhone) { this.guardianPhone = guardianPhone; }

    public Integer getAgeYears() { return ageYears; }
    public void setAgeYears(Integer ageYears) { this.ageYears = ageYears; }

    public Integer getAgeMonths() { return ageMonths; }
    public void setAgeMonths(Integer ageMonths) { this.ageMonths = ageMonths; }

    public Integer getAgeDays() { return ageDays; }
    public void setAgeDays(Integer ageDays) { this.ageDays = ageDays; }

    public String getLearningMedium() { return learningMedium; }
    public void setLearningMedium(String learningMedium) { this.learningMedium = learningMedium; }

    public String getOlSchoolAddress() { return olSchoolAddress; }
    public void setOlSchoolAddress(String olSchoolAddress) { this.olSchoolAddress = olSchoolAddress; }

    public String getOlSchoolAdmissionNumber() { return olSchoolAdmissionNumber; }
    public void setOlSchoolAdmissionNumber(String olSchoolAdmissionNumber) { this.olSchoolAdmissionNumber = olSchoolAdmissionNumber; }

    public LocalDate getDateEnteredSchool() { return dateEnteredSchool; }
    public void setDateEnteredSchool(LocalDate dateEnteredSchool) { this.dateEnteredSchool = dateEnteredSchool; }

    public String getGradeEntered() { return gradeEntered; }
    public void setGradeEntered(String gradeEntered) { this.gradeEntered = gradeEntered; }

    public String getExtraCurricularActivities() { return extraCurricularActivities; }
    public void setExtraCurricularActivities(String extraCurricularActivities) { this.extraCurricularActivities = extraCurricularActivities; }
}