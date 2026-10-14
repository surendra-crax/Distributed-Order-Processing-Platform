package com.platform.orderprocessing.inventory.repository;

import com.platform.orderprocessing.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for inventory management and atomic stock operations.
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findBySku(String sku);

    boolean existsBySku(String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.sku = :sku")
    Optional<Inventory> findBySkuWithLock(@Param("sku") String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.sku IN :skus ORDER BY i.sku ASC")
    List<Inventory> findAllBySkuInWithLockOrdered(@Param("skus") Collection<String> skus);

    /**
     * Executes a conditional atomic stock reservation.
     * Prevents overselling at the database engine level without requiring long row locks.
     *
     * @param sku product SKU
     * @param quantity quantity to reserve
     * @return 1 if successfully reserved, 0 if insufficient available stock
     */
    @Modifying
    @Query(value = "UPDATE inventory SET available_quantity = available_quantity - :quantity, "
            + "reserved_quantity = reserved_quantity + :quantity, "
            + "updated_at = CURRENT_TIMESTAMP "
            + "WHERE sku = :sku AND available_quantity >= :quantity", nativeQuery = true)
    int decrementAvailableStock(@Param("sku") String sku, @Param("quantity") int quantity);

    /**
     * Releases previously reserved stock (e.g. on order failure or cancellation).
     */
    @Modifying
    @Query(value = "UPDATE inventory SET available_quantity = available_quantity + :quantity, "
            + "reserved_quantity = reserved_quantity - :quantity, "
            + "updated_at = CURRENT_TIMESTAMP "
            + "WHERE sku = :sku AND reserved_quantity >= :quantity", nativeQuery = true)
    int releaseReservedStock(@Param("sku") String sku, @Param("quantity") int quantity);

    /**
     * Adds fresh stock to available inventory.
     */
    @Modifying
    @Query(value = "UPDATE inventory SET available_quantity = available_quantity + :quantity, "
            + "updated_at = CURRENT_TIMESTAMP "
            + "WHERE sku = :sku", nativeQuery = true)
    int addStockQuantity(@Param("sku") String sku, @Param("quantity") int quantity);
}
