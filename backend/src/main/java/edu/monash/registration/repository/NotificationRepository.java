package edu.monash.registration.repository;

import edu.monash.registration.domain.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** FR11 in-app notifications. Newest first; a student only ever sees their own rows. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByStudentIdAndReadAtIsNull(UUID studentId);

    @Query("""
            select n from Notification n
            join fetch n.course c
            where n.student.id = :studentId
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findForStudent(@Param("studentId") UUID studentId);

    @Query("""
            select n from Notification n
            join fetch n.course c
            where n.id = :id and n.student.id = :studentId
            """)
    Optional<Notification> findOwned(@Param("id") Long id, @Param("studentId") UUID studentId);
}
