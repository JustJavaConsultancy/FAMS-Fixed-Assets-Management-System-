package com.example.fams.assets;

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

    public AssetService(AssetRepository assetRepository,
                        CloudinaryUploadService cloudinaryUploadService,
                        AssetTagGenerationService assetTagGenerationService) {
        this.assetRepository = assetRepository;
        this.cloudinaryUploadService = cloudinaryUploadService;
        this.assetTagGenerationService = assetTagGenerationService;
    }

    @Transactional(readOnly = true)
    public List<Asset> findAll() {
        return assetRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Asset findById(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Asset not found: " + id));
    }

    @Transactional
    public Asset create(Asset asset, MultipartFile image) {
        asset.setAssetCode(nextAssetCode());
        AssetTags tags = assetTagGenerationService.generate(asset.getAssetCode());
        asset.setBarcodeValue(tags.barcodeValue());
        asset.setQrCodeImageDataUri(tags.qrCodeImageDataUri());
        asset.setBarcodeImageDataUri(tags.barcodeImageDataUri());
        cloudinaryUploadService.upload(image).ifPresent(upload -> {
            asset.setImageUrl(upload.secureUrl());
            asset.setImagePublicId(upload.publicId());
        });
        return assetRepository.save(asset);
    }

    private String nextAssetCode() {
        long next = assetRepository.count() + 1;
        return "AST-" + Year.now().getValue() + "-" + String.format("%05d", next);
    }
}
