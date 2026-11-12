package com.iss.backend.repository;

import com.iss.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Integer> {

    User findByUserName(String userName);

    List<User> findByRoleRoleNameIgnoreCase(String roleName);

    List<User> findByRoleRoleNameIgnoreCaseAndStreamStreamId(String roleName, Integer streamId);

    boolean existsByStreamStreamId(Integer streamId);
}
