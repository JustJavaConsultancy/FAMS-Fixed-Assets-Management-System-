package com.example.fams.assets;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetCheckoutRepository extends JpaRepository<AssetCheckout, Long> {

    /**
     * Find all checkouts for a specific asset
     */
    List<AssetCheckout> findByAssetIdOrderByCheckoutDateDesc(Long assetId);

    /**
     * Find currently checked-out assets
     */
    List<AssetCheckout> findByStatusOrderByCheckoutDateDesc(String status);

    /**
     * Find all checkouts by a specific person
     */
    List<AssetCheckout> findByCheckedOutByOrderByCheckoutDateDesc(String checkedOutBy);

    /**
     * Find checkouts that are overdue (due return date is in the past and status is "Checked Out")
     */
    List<AssetCheckout> findByStatusAndDueReturnDateBefore(String status, java.time.LocalDate date);

    /**
     * Find checkouts by status, ordered by when they were requested (newest first).
     * Used for the asset-manager pending-approval queue.
     */
    List<AssetCheckout> findByStatusOrderByRequestedAtDesc(String status);

    /**
     * Find a specific employee's checkouts by status, ordered by request time (newest first).
     * Used for an employee's "My Checkout Requests" view.
     */
    List<AssetCheckout> findByStatusAndRequestedByOrderByRequestedAtDesc(String status, String requestedBy);

    /**
     * Find all of a specific employee's checkout requests (any status), ordered by request
     * time (newest first). Used for the employee's checkout request history view.
     */
    List<AssetCheckout> findByRequestedByOrderByRequestedAtDesc(String requestedBy);

    /**
     * Find an asset's checkouts in a given status, ordered by checkout date (newest first).
     * Used to detect whether an asset currently has an active ("Checked Out") checkout so the
     * employee can be offered a "Check In / Return" action.
     */
    List<AssetCheckout> findByAssetIdAndStatusOrderByCheckoutDateDesc(Long assetId, String status);

    /**
     * Find checkouts in a given status, ordered by when they were last updated (newest first).
     * Used for the asset-manager pending-return approval queue.
     */
    List<AssetCheckout> findByStatusOrderByUpdatedAtDesc(String status);
}

