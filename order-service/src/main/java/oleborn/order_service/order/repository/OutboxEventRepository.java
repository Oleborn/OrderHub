package oleborn.order_service.order.repository;

import oleborn.order_service.order.dictionary.OutboxStatus;
import oleborn.order_service.order.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

//    //несколько poller'ов могут забрать одни и те же события (дубликаты в Kafka) - решение FOR UPDATE SKIP LOCKED
//    @Modifying
//    @Query(value = """
//    UPDATE outbox_event
//    SET status = 'PROCESSING', processed_at = now()
//    WHERE id IN (
//        SELECT id FROM outbox_event
//        WHERE status = 'NEW'
//        ORDER BY created_at
//        LIMIT :limit
//        FOR UPDATE SKIP LOCKED
//    )
//    RETURNING *
//    """, nativeQuery = true)
//    List<OutboxEvent> claimNewEvents(@Param("limit") int limit);

    @Modifying
    @Query("""
            UPDATE OutboxEvent o 
            SET o.status = :status, o.processedAt = CURRENT_TIMESTAMP 
            WHERE o.aggregateId = :eventId 
            AND o.status = 'NEW'
            """)
    int updateStatus
            (@Param("eventId") Long eventId,
             @Param("status") OutboxStatus status
            );

    long countByStatus(OutboxStatus status);

    long count();
}