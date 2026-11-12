package com.iss.backend.repository;

import com.iss.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    List<Notification> findByApplicationApplicationId(Integer applicationId);

    List<Notification> findByApplicationApplicationIdOrderBySentDateDesc(Integer applicationId);

    List<Notification> findByApplicationStreamStreamIdOrderBySentDateDesc(Integer streamId);
}
