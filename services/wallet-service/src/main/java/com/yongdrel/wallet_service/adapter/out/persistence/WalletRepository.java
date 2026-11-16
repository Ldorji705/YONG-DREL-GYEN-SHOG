package com.yongdrel.wallet_service.adapter.out.persistence;

import com.yongdrel.wallet_service.domain.model.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Plain Repository (no delete methods): wallets are never deleted. */
public interface WalletRepository extends Repository<Wallet, java.util.UUID> {

    Wallet save(Wallet wallet);

    Optional<Wallet> findById(java.util.UUID id);

    Optional<Wallet> findByOwnerId(String ownerId);

    /** SELECT ... FOR UPDATE: concurrent operations on one wallet run one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.ownerId = :ownerId")
    Optional<Wallet> findByOwnerIdForUpdate(@Param("ownerId") String ownerId);

    /** Creates an empty wallet only if the user has none yet (atomic, safe under concurrency). */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO wallets (id, owner_id, currency, balance, version, created_at, updated_at)
            VALUES (:id, :ownerId, :currency, 0, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") byte[] id,
                       @Param("ownerId") String ownerId,
                       @Param("currency") String currency);
}
