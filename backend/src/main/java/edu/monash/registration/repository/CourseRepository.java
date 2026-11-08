package edu.monash.registration.repository;

import edu.monash.registration.domain.Course;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findBySemesterOrderByCodeAsc(String semester);

    @Query("""
            select c from Course c
            where c.semester = :semester
              and (lower(c.code) like lower(concat('%', :q, '%'))
                   or lower(c.name) like lower(concat('%', :q, '%')))
            order by c.code asc
            """)
    List<Course> search(@Param("semester") String semester, @Param("q") String q);

    /**
     * SELECT ... FOR UPDATE. Must be called inside an active transaction; this is the single
     * mechanism that protects the seat counter (FR6).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findWithLockById(@Param("id") UUID id);

    /**
     * SELECT ... FOR UPDATE scoped to the active semester. A course outside that semester is
     * invisible to the write path, so the semester check and the seat lock are atomic (FR3-B1).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id and c.semester = :semester")
    Optional<Course> findWithLockByIdAndSemester(@Param("id") UUID id,
            @Param("semester") String semester);

    @Query("select distinct c.semester from Course c order by c.semester desc")
    List<String> findDistinctSemesters();

    boolean existsBySemester(String semester);
}
