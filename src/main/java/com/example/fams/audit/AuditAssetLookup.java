package com.example.fams.audit;

public record AuditAssetLookup(
        Long id,
        String assetCode,
        String barcodeValue,
        String name,
        String category,
        String expectedLocation,
        String status,
        String custodian,
        String imageUrl
) {
}
