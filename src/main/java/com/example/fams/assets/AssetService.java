package com.example.fams.assets;

import com.example.fams.settings.AdminSettingsService;
import com.example.fams.lifecycle.AssetLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Year;
import java.util.List;
import java.util.NoSuchElementException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import com.example.fams.assets.dto.BulkAssignRequestDto;
import com.example.fams.assets.dto.BulkTransferRequestDto;
import com.example.fams.assets.dto.BulkRetireRequestDto;
import com.example.fams.assets.dto.BulkOperationResultDto;
import com.example.fams.assets.dto.BulkOperationItemResultDto;

@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final CloudinaryUploadService cloudinaryUploadService;
    private final AssetTagGenerationService assetTagGenerationService;
    private final AdminSettingsService adminSettingsService;
    private final AssetLifecycleService assetLifecycleService;

    public AssetService(AssetRepository assetRepository,
                        CloudinaryUploadService cloudinaryUploadService,
                        AssetTagGenerationService assetTagGenerationService,
                        AdminSettingsService adminSettingsService,
                        AssetLifecycleService assetLifecycleService) {
        this.assetRepository = assetRepository;
        this.cloudinaryUploadService = cloudinaryUploadService;
        this.assetTagGenerationService = assetTagGenerationService;
        this.adminSettingsService = adminSettingsService;
        this.assetLifecycleService = assetLifecycleService;
    }

    /**
     * Bulk create assets from a CSV file. The CSV must contain a header row. Supported headers (case-insensitive):
     * name, category, description, serialNumber, manufacturer, model, purchaseDate (yyyy-MM-dd), purchaseCost,
     * vendor, warrantyExpiry (yyyy-MM-dd), department, branch, custodian, status
     *
     * This method is defensive: it attempts to create as many assets as possible and returns a summary of successes
     * and per-line errors.
     */
    public BulkUploadResult createFromCsv(org.springframework.web.multipart.MultipartFile csvFile) {
        List<String> errors = new ArrayList<>();
        int success = 0;

        if (csvFile == null || csvFile.isEmpty()) {
            errors.add("No CSV file provided.");
            return new BulkUploadResult(success, errors);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvFile.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = null;
            // Read until a non-empty header line
            while ((headerLine = reader.readLine()) != null) {
                if (!headerLine.trim().isEmpty()) break;
            }
            if (headerLine == null) {
                errors.add("CSV file is empty.");
                return new BulkUploadResult(success, errors);
            }

            List<String> headers = parseCsvLine(headerLine).stream().map(String::trim).map(String::toLowerCase).collect(Collectors.toList());

            String line;
            int lineNo = 1; // header
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.trim().isEmpty()) continue;
                List<String> cols = parseCsvLine(line);
                try {
                    Map<String, String> row = new java.util.HashMap<>();
                    for (int i = 0; i < headers.size() && i < cols.size(); i++) {
                        row.put(headers.get(i), cols.get(i).trim());
                    }

                    // Required fields
                    String name = row.getOrDefault("name", "");
                    String category = row.getOrDefault("category", "");
                    String department = row.getOrDefault("department", "");
                    String branch = row.getOrDefault("branch", "");
                    String custodian = row.getOrDefault("custodian", "");

                    if (name.isBlank() || category.isBlank() || department.isBlank() || branch.isBlank() || custodian.isBlank()) {
                        errors.add("Line " + lineNo + ": missing required field (name, category, department, branch, custodian are required).");
                        continue;
                    }

                    // validate category
                    if (!adminSettingsService.isKnownActiveCategory(category)) {
                        errors.add("Line " + lineNo + ": unknown or inactive category '" + category + "'.");
                        continue;
                    }

                    Asset asset = new Asset();
                    asset.setName(name);
                    asset.setCategory(category);
                    asset.setDescription(row.getOrDefault("description", null));
                    asset.setSerialNumber(row.getOrDefault("serialnumber", null));
                    asset.setManufacturer(row.getOrDefault("manufacturer", null));
                    asset.setModel(row.getOrDefault("model", null));

                    String purchaseDateStr = row.getOrDefault("purchasedate", "");
                    if (!purchaseDateStr.isBlank()) {
                        try {
                            asset.setPurchaseDate(java.time.LocalDate.parse(purchaseDateStr));
                        } catch (Exception e) {
                            errors.add("Line " + lineNo + ": invalid purchaseDate '" + purchaseDateStr + "'. Use yyyy-MM-dd.");
                            continue;
                        }
                    }

                    String purchaseCostStr = row.getOrDefault("purchasecost", "");
                    if (!purchaseCostStr.isBlank()) {
                        try {
                            // Allow commas
                            String cleaned = purchaseCostStr.replaceAll(",", "");
                            asset.setPurchaseCost(new BigDecimal(cleaned));
                        } catch (Exception e) {
                            errors.add("Line " + lineNo + ": invalid purchaseCost '" + purchaseCostStr + "'.");
                            continue;
                        }
                    }

                    asset.setVendor(row.getOrDefault("vendor", null));

                    String warrantyExpiryStr = row.getOrDefault("warrantyexpiry", "");
                    if (!warrantyExpiryStr.isBlank()) {
                        try {
                            asset.setWarrantyExpiry(java.time.LocalDate.parse(warrantyExpiryStr));
                        } catch (Exception e) {
                            errors.add("Line " + lineNo + ": invalid warrantyExpiry '" + warrantyExpiryStr + "'. Use yyyy-MM-dd.");
                            continue;
                        }
                    }

                    asset.setDepartment(department);
                    asset.setBranch(branch);
                    asset.setCustodian(custodian);

                    String status = row.getOrDefault("status", null);
                    if (status != null && !status.isBlank()) asset.setStatus(status);

                    // Apply defaults and tags similar to single-create path
                    applyGlobalDefaults(asset);
                    asset.setAssetCode(nextAssetCode());
                    AssetTags tags = assetTagGenerationService.generate(asset.getAssetCode());
                    applyTagDefaults(asset, tags);

                    // Save
                    Asset saved = assetRepository.save(asset);
                    assetLifecycleService.recordRegistration(saved, "Bulk Upload");
                    success++;
                } catch (Exception e) {
                    errors.add("Line " + lineNo + ": unexpected error - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            errors.add("Failed to read CSV file: " + e.getMessage());
        }

        return new BulkUploadResult(success, errors);
    }

    // Very small CSV parser that handles commas and double-quote quoting
    private List<String> parseCsvLine(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // escaped quote
                    cur.append('"');
                    i++; // skip next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                cols.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cols.add(cur.toString());
        return cols;
    }

    @Transactional(readOnly = true)
    public List<Asset> findAll() {
        return assetRepository.findByStatusNotIgnoreCaseOrderByCreatedAtDesc("Disposed");
    }

    @Transactional(readOnly = true)
    public Asset findById(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Asset not found: " + id));
    }

    @Transactional
    public Asset create(Asset asset, MultipartFile image) {
        adminSettingsService.ensureDefaults();
        validateAssetCategory(asset.getCategory());
        validateImageRequirement(image);
        applyGlobalDefaults(asset);
        asset.setAssetCode(nextAssetCode());
        AssetTags tags = assetTagGenerationService.generate(asset.getAssetCode());
        applyTagDefaults(asset, tags);
        cloudinaryUploadService.upload(image).ifPresent(upload -> {
            asset.setImageUrl(upload.secureUrl());
            asset.setImagePublicId(upload.publicId());
        });
        Asset savedAsset = assetRepository.save(asset);
        assetLifecycleService.recordRegistration(savedAsset, "Asset Manager");
        return savedAsset;
    }

    @Transactional
    public BulkOperationResultDto bulkAssign(BulkAssignRequestDto request) {
        BulkOperationResultDto result = new BulkOperationResultDto();
        result.setTotalRequested(request.getAssetIds() == null ? 0 : request.getAssetIds().size());
        if (request.getAssetIds() == null || request.getAssetIds().isEmpty()) return result;

        List<Asset> assets;
        if (request.isSelectAllMatching()) {
            assets = findAll();
        } else {
            assets = assetRepository.findAllById(request.getAssetIds());
        }
        List<Asset> processed = new ArrayList<>();
        for (Long id : request.getAssetIds()) {
            Asset asset = assets.stream().filter(a -> a.getId().equals(id)).findFirst().orElse(null);
            if (asset == null) {
                result.addResult(new BulkOperationItemResultDto(id, null, false, "Asset not found"));
                continue;
            }
            if ("Disposed".equalsIgnoreCase(asset.getStatus())) {
                result.addResult(new BulkOperationItemResultDto(id, asset.getAssetCode(), false, "Asset is already disposed and was skipped."));
                continue;
            }
            try {
                if (request.getAssignedTo() != null && !request.getAssignedTo().isBlank()) {
                    asset.setCustodian(request.getAssignedTo());
                }
                if (request.getDepartment() != null && !request.getDepartment().isBlank()) {
                    asset.setDepartment(request.getDepartment());
                }
                asset.setStatus("Assigned");
                assetRepository.save(asset);
                processed.add(asset);
                result.addResult(new BulkOperationItemResultDto(id, asset.getAssetCode(), true, "Assigned"));
            } catch (Exception e) {
                result.addResult(new BulkOperationItemResultDto(id, asset.getAssetCode(), false, "Failed: " + e.getMessage()));
            }
        }
        // Audit
        if (!processed.isEmpty()) {
            assetLifecycleService.recordBulkTransfer(processed, "System", defaultText(request.getNotes(), "Bulk assignment"));
        }
        return result;
    }

    @Transactional
    public BulkOperationResultDto bulkTransfer(BulkTransferRequestDto request) {
        BulkOperationResultDto result = new BulkOperationResultDto();
        result.setTotalRequested(request.getAssetIds() == null ? 0 : request.getAssetIds().size());
        if (request.getAssetIds() == null || request.getAssetIds().isEmpty()) return result;
        List<Asset> assets;
        if (request.isSelectAllMatching()) {
            assets = findAll();
        } else {
            assets = assetRepository.findAllById(request.getAssetIds());
        }
        List<Asset> processed = new ArrayList<>();
        for (Long id : request.getAssetIds()) {
            Asset asset = assets.stream().filter(a -> a.getId().equals(id)).findFirst().orElse(null);
            if (asset == null) {
                result.addResult(new BulkOperationItemResultDto(id, null, false, "Asset not found"));
                continue;
            }
            if ("Disposed".equalsIgnoreCase(asset.getStatus())) {
                result.addResult(new BulkOperationItemResultDto(id, asset.getAssetCode(), false, "Asset is disposed and was skipped."));
                continue;
            }
            try {
                if (request.getTransferToDepartment() != null && !request.getTransferToDepartment().isBlank()) {
                    asset.setDepartment(request.getTransferToDepartment());
                }
                if (request.getTransferToLocation() != null && !request.getTransferToLocation().isBlank()) {
                    asset.setBranch(request.getTransferToLocation());
                }
                asset.setStatus("Assigned");
                assetRepository.save(asset);
                processed.add(asset);
                result.addResult(new BulkOperationItemResultDto(id, asset.getAssetCode(), true, "Transferred"));
            } catch (Exception e) {
                result.addResult(new BulkOperationItemResultDto(id, asset.getAssetCode(), false, "Failed: " + e.getMessage()));
            }
        }
        if (!processed.isEmpty()) {
            assetLifecycleService.recordBulkTransfer(processed, "System", defaultText(request.getNotes(), "Bulk transfer"));
        }
        return result;
    }

    @Transactional
    public BulkOperationResultDto bulkRetire(BulkRetireRequestDto request) {
        BulkOperationResultDto result = new BulkOperationResultDto();
        result.setTotalRequested(request.getAssetIds() == null ? 0 : request.getAssetIds().size());
        if (request.getAssetIds() == null || request.getAssetIds().isEmpty()) return result;
        List<Asset> assets;
        if (request.isSelectAllMatching()) {
            assets = findAll();
        } else {
            assets = assetRepository.findAllById(request.getAssetIds());
        }
        List<Asset> processed = new ArrayList<>();
        for (Long id : request.getAssetIds()) {
            Asset asset = assets.stream().filter(a -> a.getId().equals(id)).findFirst().orElse(null);
            if (asset == null) {
                result.addResult(new BulkOperationItemResultDto(id, null, false, "Asset not found"));
                continue;
            }
            if ("Disposed".equalsIgnoreCase(asset.getStatus())) {
                result.addResult(new BulkOperationItemResultDto(id, asset.getAssetCode(), false, "Asset already disposed and was skipped."));
                continue;
            }
            try {
                asset.setStatus("Disposed");
                assetRepository.save(asset);
                processed.add(asset);
                result.addResult(new BulkOperationItemResultDto(id, asset.getAssetCode(), true, "Retired"));
            } catch (Exception e) {
                result.addResult(new BulkOperationItemResultDto(id, asset.getAssetCode(), false, "Failed: " + e.getMessage()));
            }
        }
        if (!processed.isEmpty()) {
            assetLifecycleService.recordBulkDisposal(processed, "System", defaultText(request.getNotes(), "Bulk retirement"));
        }
        return result;
    }

    private void validateAssetCategory(String category) {
        if (!adminSettingsService.isKnownActiveCategory(category)) {
            throw new IllegalArgumentException("Select an active asset category configured by an administrator.");
        }
    }

    private void validateImageRequirement(MultipartFile image) {
        boolean requiresImage = Boolean.parseBoolean(adminSettingsService.getParameterValue("asset.require.image", "false"));
        if (requiresImage && (image == null || image.isEmpty())) {
            throw new IllegalArgumentException("Asset image is required by the current system settings.");
        }
    }

    private void applyGlobalDefaults(Asset asset) {
        if (asset.getStatus() == null || asset.getStatus().isBlank()) {
            asset.setStatus(adminSettingsService.getParameterValue(AdminSettingsService.DEFAULT_ASSET_STATUS_KEY, "In Stock"));
        }
    }

    private void applyTagDefaults(Asset asset, AssetTags tags) {
        String tagType = adminSettingsService.getParameterValue("asset.tag.type", "QR_AND_BARCODE");
        if ("QR_ONLY".equals(tagType)) {
            asset.setQrCodeImageDataUri(tags.qrCodeImageDataUri());
            return;
        }
        asset.setBarcodeValue(tags.barcodeValue());
        if ("BARCODE_ONLY".equals(tagType)) {
            asset.setBarcodeImageDataUri(tags.barcodeImageDataUri());
            return;
        }
        asset.setQrCodeImageDataUri(tags.qrCodeImageDataUri());
        asset.setBarcodeImageDataUri(tags.barcodeImageDataUri());
    }

    private String nextAssetCode() {
        long next = assetRepository.count() + 1;
        String prefix = adminSettingsService.getParameterValue("asset.code.prefix", "AST");
        return prefix + "-" + Year.now().getValue() + "-" + String.format("%05d", next);
    }

    private String defaultText(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value;
    }
}
