package edu.monash.registration.repository;

import edu.monash.registration.domain.Student;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByStudentId(String studentId);

    /**
     * FR9: serialises one student's register attempts so two overlapping requests cannot both pass
     * the clash check before either registration commits. Lock order stays course → student, which
     * no other write path inverts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.studentId = :studentId")
    Optional<Student> findWithLockByStudentId(@Param("studentId") String studentId);

    boolean existsByStudentId(String studentId);
}
