package oleborn.order_service.order.repository;

import oleborn.order_service.order.domain.entity.ProcessedCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedCommandRepository extends JpaRepository<ProcessedCommand, UUID> {

}
