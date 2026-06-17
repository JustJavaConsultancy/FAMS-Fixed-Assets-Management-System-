package com.example.fams.assets;

import com.example.fams.settings.AdminSettingsService;
import com.example.fams.lifecycle.AssetLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Year;
import java.util.List;
import java.util.NoSuchElementException;

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
}
