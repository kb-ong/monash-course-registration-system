package edu.monash.registration.repository;

import edu.monash.registration.domain.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByStudentIdOrderByIdAsc(String studentId);

    long countByStudentIdAndCourseCode(String studentId, String courseCode);
}
