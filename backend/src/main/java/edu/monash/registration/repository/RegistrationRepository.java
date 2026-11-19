package edu.monash.registration.repository;

import edu.monash.registration.domain.Registration;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

    boolean existsByStudentIdAndCourseId(UUID studentId, UUID courseId);

    long countByCourseId(UUID courseId);

    /** JPQL aggregate over the join so the credit total never loads every registration (FR6). */
    @Query("""
            select sum(c.creditHours) from Registration r
            join r.course c
            where r.student.id = :studentId and c.semester = :semester
            """)
    Long sumCredits(@Param("studentId") UUID studentId, @Param("semester") String semester);

    @Query("""
            select r from Registration r
            join fetch r.course c
            where r.student.id = :studentId and c.semester = :semester
            order by r.registeredAt asc, c.code asc
            """)
    List<Registration> findAllForStudentInSemester(@Param("studentId") UUID studentId,
            @Param("semester") String semester);

    /**
     * FR9: the student's active-semester registrations with their weekly slots fetched in the same
     * query, so clash detection never lazily loads a schedule per course. {@code distinct} collapses
     * the one-row-per-slot duplication a collection fetch join would otherwise produce.
     */
    @Query("""
            select distinct r from Registration r
            join fetch r.course c
            left join fetch c.schedule
            where r.student.id = :studentId and c.semester = :semester
            """)
    List<Registration> findAllForStudentInSemesterWithSchedule(
            @Param("studentId") UUID studentId, @Param("semester") String semester);

    @Query("""
            select r from Registration r
            join fetch r.student s
            join fetch r.course c
            where r.id = :id
            """)
    Optional<Registration> findByIdWithStudentAndCourse(@Param("id") UUID id);

    /**
     * SELECT ... FOR UPDATE on the registration row so concurrent drops of the same row are
     * serialised: the winner deletes, the loser observes no row and gets a clean 404 (FR4-B8).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Registration r where r.id = :id")
    Optional<Registration> findWithLockById(@Param("id") UUID id);
}
