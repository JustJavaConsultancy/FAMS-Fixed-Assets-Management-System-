package com.example.fams.assets;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AssetCheckoutService {

    private final AssetCheckoutRepository checkoutRepository;
    private final AssetRepository assetRepository;

    public AssetCheckoutService(AssetCheckoutRepository checkoutRepository,
                                AssetRepository assetRepository) {
        this.checkoutRepository = checkoutRepository;
        this.assetRepository = assetRepository;
    }

    /**
     * Employee requests a checkout. The request sits in "Pending Approval" and the asset is
     * NOT yet checked out — its status is left unchanged until a manager approves.
     */
    @Transactional
    public AssetCheckout requestCheckout(Long assetId, String requestedBy, LocalDate checkoutDate,
                                         LocalDate dueReturnDate, String purpose, String conditionBeforeCheckout) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new NoSuchElementException("Asset not found with id: " + assetId));

        if (!asset.getStatus().equals("In Stock") && !asset.getStatus().equals("Available")
                && !asset.getStatus().equals("Returned") && !asset.getStatus().equals("Assigned")) {
            throw new IllegalArgumentException("Asset is not available for checkout. Current status: " + asset.getStatus());
        }

        if (dueReturnDate.isBefore(checkoutDate)) {
            throw new IllegalArgumentException("Due return date cannot be before checkout date");
        }

        AssetCheckout checkout = new AssetCheckout();
        checkout.setAsset(asset);
        checkout.setRequestedBy(requestedBy);
        checkout.setRequestedAt(LocalDateTime.now());
        checkout.setCheckedOutBy(requestedBy);
        checkout.setCheckoutDate(checkoutDate);
        checkout.setDueReturnDate(dueReturnDate);
        checkout.setPurpose(purpose);
        checkout.setConditionBeforeCheckout(conditionBeforeCheckout);
        checkout.setStatus("Pending Approval");

        return checkoutRepository.save(checkout);
    }

    /**
     * Asset manager approves a pending checkout request. This is the point at which the asset
     * is actually checked out.
     */
    @Transactional
    public AssetCheckout approveCheckout(Long checkoutId, String approvedBy) {
        AssetCheckout checkout = checkoutRepository.findById(checkoutId)
                .orElseThrow(() -> new NoSuchElementException("Checkout request not found with id: " + checkoutId));

        if (!checkout.getStatus().equals("Pending Approval")) {
            throw new IllegalArgumentException("Only pending checkout requests can be approved");
        }

        checkout.setApprovedBy(approvedBy);
        checkout.setApprovedAt(LocalDateTime.now());
        checkout.setStatus("Checked Out");

        AssetCheckout saved = checkoutRepository.save(checkout);

        Asset asset = checkout.getAsset();
        asset.setStatus("Checked Out");
        assetRepository.save(asset);

        return saved;
    }

    /**
     * Asset manager rejects a pending checkout request. The asset is left unchanged.
     */
    @Transactional
    public AssetCheckout rejectCheckout(Long checkoutId, String approvedBy, String reason) {
        AssetCheckout checkout = checkoutRepository.findById(checkoutId)
                .orElseThrow(() -> new NoSuchElementException("Checkout request not found with id: " + checkoutId));

        if (!checkout.getStatus().equals("Pending Approval")) {
            throw new IllegalArgumentException("Only pending checkout requests can be rejected");
        }

        checkout.setApprovedBy(approvedBy);
        checkout.setApprovedAt(LocalDateTime.now());
        checkout.setRejectionReason(reason);
        checkout.setStatus("Rejected");

        return checkoutRepository.save(checkout);
    }

    /**
     * Pending checkout requests awaiting asset-manager approval.
     */
    public List<AssetCheckout> getPendingApprovals() {
        return checkoutRepository.findByStatusOrderByRequestedAtDesc("Pending Approval");
    }

    /**
     * Pending return requests (employee-submitted) awaiting asset-manager approval.
     */
    public List<AssetCheckout> getPendingReturns() {
        return checkoutRepository.findByStatusOrderByUpdatedAtDesc("Pending Return");
    }

    /**
     * Asset manager rejects a pending employee return request. The checkout returns to the
     * "Checked Out" state and the asset remains checked out with the employee.
     */
    @Transactional
    public AssetCheckout rejectReturn(Long checkoutId, String rejectedBy, String reason) {
        AssetCheckout checkout = checkoutRepository.findById(checkoutId)
                .orElseThrow(() -> new NoSuchElementException("Checkout record not found with id: " + checkoutId));

        if (!checkout.getStatus().equals("Pending Return")) {
            throw new IllegalArgumentException("Only pending return requests can be rejected");
        }

        checkout.setApprovedBy(rejectedBy);
        checkout.setApprovedAt(LocalDateTime.now());
        checkout.setRejectionReason(reason);
        checkout.setStatus("Checked Out");

        return checkoutRepository.save(checkout);
    }

    /**
     * @deprecated Use {@link #requestCheckout(Long, String, LocalDate, LocalDate, String, String)}
     * followed by {@link #approveCheckout(Long, String)}. Direct creation bypasses the
     * employee-request / manager-approval workflow.
     */
    @Deprecated
    @Transactional
    public AssetCheckout checkout(Long assetId, String checkedOutBy, LocalDate checkoutDate,
                                  LocalDate dueReturnDate, String purpose, String conditionBeforeCheckout) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new NoSuchElementException("Asset not found with id: " + assetId));

        if (!asset.getStatus().equals("In Stock") && !asset.getStatus().equals("Available") && !asset.getStatus().equals("Returned")) {
            throw new IllegalArgumentException("Asset is not available for checkout. Current status: " + asset.getStatus());
        }

        if (dueReturnDate.isBefore(checkoutDate)) {
            throw new IllegalArgumentException("Due return date cannot be before checkout date");
        }

        AssetCheckout checkout = new AssetCheckout();
        checkout.setAsset(asset);
        checkout.setCheckedOutBy(checkedOutBy);
        checkout.setCheckoutDate(checkoutDate);
        checkout.setDueReturnDate(dueReturnDate);
        checkout.setPurpose(purpose);
        checkout.setConditionBeforeCheckout(conditionBeforeCheckout);
        checkout.setStatus("Checked Out");

        AssetCheckout saved = checkoutRepository.save(checkout);

        // Update asset status
        asset.setStatus("Checked Out");
        assetRepository.save(asset);

        return saved;
    }

    /**
     * Employee submits a return request for an asset they have checked out. The request sits in
     * "Pending Return" and the asset is NOT yet returned — its status is left unchanged until a
     * manager approves the return.
     */
    @Transactional
    public AssetCheckout requestReturn(Long checkoutId, String conditionAfterReturn, String returnNotes) {
        AssetCheckout checkout = checkoutRepository.findById(checkoutId)
                .orElseThrow(() -> new NoSuchElementException("Checkout record not found with id: " + checkoutId));

        if (!checkout.getStatus().equals("Checked Out")) {
            throw new IllegalArgumentException("Only a checked-out asset can have a return request submitted");
        }

        checkout.setConditionAfterReturn(conditionAfterReturn);
        checkout.setReturnNotes(returnNotes);
        checkout.setStatus("Pending Return");

        return checkoutRepository.save(checkout);
    }

    /**
     * Asset manager approves a pending employee return request. This is the point at which the
     * asset is actually returned (status "Returned"), and it then awaits the manager's final
     * verification before becoming available again.
     */
    /**
     * Complete a return — handles both the asset-manager direct return (status "Checked Out")
     * and the approval of an employee's pending return request (status "Pending Return"). In
     * both cases the asset moves to "Returned" and then awaits the manager's final verification.
     */
    @Transactional
    public AssetCheckout returnCheckout(Long checkoutId, String conditionAfterReturn, String returnNotes) {
        AssetCheckout checkout = checkoutRepository.findById(checkoutId)
                .orElseThrow(() -> new NoSuchElementException("Checkout record not found with id: " + checkoutId));

        if (!checkout.getStatus().equals("Checked Out") && !checkout.getStatus().equals("Pending Return")) {
            throw new IllegalArgumentException("Only a checked-out asset or a pending return request can be returned");
        }

        checkout.setReturnDate(LocalDate.now());
        if (conditionAfterReturn != null && !conditionAfterReturn.isBlank()) {
            checkout.setConditionAfterReturn(conditionAfterReturn);
        }
        if (returnNotes != null && !returnNotes.isBlank()) {
            checkout.setReturnNotes(returnNotes);
        }
        checkout.setStatus("Returned");

        AssetCheckout saved = checkoutRepository.save(checkout);

        // Update asset status to Returned
        Asset asset = checkout.getAsset();
        asset.setStatus("Returned");
        assetRepository.save(asset);

        return saved;
    }

    /**
     * Verify a returned asset checkout
     */
    @Transactional
    public AssetCheckout verifyReturn(Long checkoutId, String verifiedBy, String notes) {
        AssetCheckout checkout = checkoutRepository.findById(checkoutId)
                .orElseThrow(() -> new NoSuchElementException("Checkout record not found with id: " + checkoutId));

        if (!checkout.getStatus().equals("Returned")) {
            throw new IllegalArgumentException("Only returned assets can be verified");
        }

        checkout.setVerifiedBy(verifiedBy);
        checkout.setVerifiedAt(LocalDateTime.now());
        checkout.setStatus("Verified");

        if (notes != null && !notes.isBlank()) {
            checkout.setReturnNotes(notes);
        }

        AssetCheckout saved = checkoutRepository.save(checkout);

        // Update asset status to Available/In Stock
        Asset asset = checkout.getAsset();
        asset.setStatus("Available");
        assetRepository.save(asset);

        return saved;
    }

    /**
     * Get all checkouts for an asset
     */
    public List<AssetCheckout> getCheckoutsForAsset(Long assetId) {
        return checkoutRepository.findByAssetIdOrderByCheckoutDateDesc(assetId);
    }

    /**
     * Get all currently checked-out assets
     */
    public List<AssetCheckout> getActiveCheckouts() {
        return checkoutRepository.findByStatusOrderByCheckoutDateDesc("Checked Out");
    }

    /**
     * Get all checkouts by a specific person
     */
    public List<AssetCheckout> getCheckoutsByPerson(String person) {
        return checkoutRepository.findByCheckedOutByOrderByCheckoutDateDesc(person);
    }

    /**
     * Get overdue checkouts
     */
    public List<AssetCheckout> getOverdueCheckouts() {
        return checkoutRepository.findByStatusAndDueReturnDateBefore("Checked Out", LocalDate.now());
    }

    /**
     * Get a specific checkout record
     */
    public AssetCheckout getCheckout(Long checkoutId) {
        return checkoutRepository.findById(checkoutId)
                .orElseThrow(() -> new NoSuchElementException("Checkout record not found with id: " + checkoutId));
    }

    /**
     * Get all checkouts
     */
    public List<AssetCheckout> getAllCheckouts() {
        return checkoutRepository.findAll();
    }
}

