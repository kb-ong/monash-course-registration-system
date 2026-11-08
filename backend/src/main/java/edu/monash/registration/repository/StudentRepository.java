package edu.monash.registration.repository;

import edu.monash.registration.domain.Student;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByStudentId(String studentId);

    boolean existsByStudentId(String studentId);
}
