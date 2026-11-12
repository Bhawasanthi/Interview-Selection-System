package com.iss.backend.repository;

import com.iss.backend.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Integer> {
    Student findByUsername(String username);

    java.util.List<Student> findByEmailIgnoreCase(String email);

    Student findByNic(String nic);

}