package edu.monash.registration.repository;

import edu.monash.registration.domain.WaitlistEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * FR11 queue persistence. Queue order is the entry identity id (see {@link WaitlistEntry}); callers
 * that mutate the queue do so while holding the course row lock, so a plain read is consistent.
 */
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    boolean existsByStudentIdAndCourseId(UUID studentId, UUID courseId);

    Optional<WaitlistEntry> findByStudentIdAndCourseId(UUID studentId, UUID courseId);

    long countByCourseId(UUID courseId);

    long countByCourseIdAndIdLessThan(UUID courseId, Long id);

    @Query("""
            select w from WaitlistEntry w
            join fetch w.course c
            where w.student.id = :studentId and c.semester = :semester
            order by w.id asc
            """)
    List<WaitlistEntry> findForStudentInSemester(@Param("studentId") UUID studentId,
            @Param("semester") String semester);

    /**
     * Queue snapshot in join order (lowest id first). Promotion walks this list; entries that are no
     * longer eligible are skipped, so one ineligible student never blocks a later one.
     */
    @Query("""
            select w from WaitlistEntry w
            join fetch w.student s
            join fetch w.course c
            where c.id = :courseId
            order by w.id asc
            """)
    List<WaitlistEntry> findQueueForCourse(@Param("courseId") UUID courseId);

    /** A waitlist id is opaque and never disclosed for another student: not-found and not-owner are
     * both reported as WAITLIST_NOT_FOUND. */
    @Query("""
            select w from WaitlistEntry w
            join fetch w.course c
            join fetch w.student s
            where w.id = :id and s.studentId = :studentId
            """)
    Optional<WaitlistEntry> findOwned(@Param("id") Long id, @Param("studentId") String studentId);
}
