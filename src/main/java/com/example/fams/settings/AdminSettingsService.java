package com.example.fams.settings;

import com.example.fams.assets.AssetRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class AdminSettingsService {

    public static final String DEFAULT_ASSET_STATUS_KEY = "asset.default.status";

    private static final Set<String> DEPRECIATION_METHODS = Set.of("SL", "RB", "DDB");
    private static final Set<String> PARAMETER_KEYS = Set.of(
            "asset.code.prefix",
            DEFAULT_ASSET_STATUS_KEY,
            "asset.require.image",
            "asset.tag.type",
            "asset.disposal.requires.approval",
            "asset.lifecycle.approval.firstGroup",
            "asset.lifecycle.approval.finalGroup"
    );

    private final AssetCategoryRepository assetCategoryRepository;
    private final SystemParameterRepository systemParameterRepository;
    private final AssetRepository assetRepository;

    public AdminSettingsService(AssetCategoryRepository assetCategoryRepository,
                                SystemParameterRepository systemParameterRepository,
                                AssetRepository assetRepository) {
        this.assetCategoryRepository = assetCategoryRepository;
        this.systemParameterRepository = systemParameterRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional(readOnly = true)
    public List<AssetCategory> findAllCategories() {
        return assetCategoryRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<AssetCategory> findActiveCategories() {
        return assetCategoryRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public long countAssetsInCategory(String categoryName) {
        return assetRepository.countByCategoryIgnoreCase(categoryName);
    }

    @Transactional(readOnly = true)
    public List<SystemParameter> findSystemParameters() {
        return systemParameterRepository.findAllByOrderByKeyNameAsc();
    }

    @Transactional
    public void ensureDefaults() {
        if (assetCategoryRepository.count() == 0) {
            createDefaultCategory("IT Equipment", "IT", "Computers, network devices, printers, and peripherals.", 4, "SL");
            createDefaultCategory("Furniture", "FURN", "Desks, chairs, cabinets, fixtures, and fittings.", 8, "SL");
            createDefaultCategory("Office Equipment", "OFF", "General office equipment used across departments.", 5, "SL");
            createDefaultCategory("Vehicle", "VEH", "Cars, vans, motorcycles, and fleet assets.", 5, "RB");
            createDefaultCategory("Machinery", "MACH", "Production, technical, and operational machinery.", 10, "DDB");
        }
        createParameterIfMissing("asset.code.prefix", "Asset code prefix", "AST", "Prefix used when generating new asset numbers.", "text");
        createParameterIfMissing(DEFAULT_ASSET_STATUS_KEY, "Default asset status", "In Stock", "Initial status used when no status is supplied.", "select");
        createParameterIfMissing("asset.require.image", "Require asset image", "false", "Controls whether asset registration must include an image.", "boolean");
        createParameterIfMissing("asset.tag.type", "Default tag type", "QR_AND_BARCODE", "Controls the tag set generated during asset registration.", "select");
        createParameterIfMissing("asset.disposal.requires.approval", "Disposal requires approval", "true", "Requires administrator approval before asset disposal is finalized.", "boolean");
        createParameterIfMissing("asset.lifecycle.approval.firstGroup", "Lifecycle first approval group", "departmentHead", "First Flowable candidate group for asset lifecycle workflows.", "text");
        createParameterIfMissing("asset.lifecycle.approval.finalGroup", "Lifecycle final approval group", "admin", "Final Flowable candidate group for asset lifecycle workflows.", "text");
    }

    @Transactional
    public AssetCategory createCategory(AssetCategory category) {
        validateCategory(category, null);
        try {
            return assetCategoryRepository.save(category);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Category name or code already exists.");
        }
    }

    @Transactional
    public AssetCategory updateCategory(Long id, AssetCategory form) {
        AssetCategory category = assetCategoryRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Asset category not found."));
        validateCategory(form, id);
        category.setName(form.getName());
        category.setCode(form.getCode());
        category.setDescription(form.getDescription());
        category.setDefaultUsefulLifeYears(form.getDefaultUsefulLifeYears());
        category.setDefaultResidualRate(form.getDefaultResidualRate());
        category.setDefaultDepreciationMethod(form.getDefaultDepreciationMethod());
        category.setDefaultStatus(form.getDefaultStatus());
        category.setActive(form.isActive());
        try {
            return assetCategoryRepository.save(category);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Category name or code already exists.");
        }
    }

    @Transactional
    public void deleteCategory(Long id) {
        AssetCategory category = assetCategoryRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Asset category not found."));
        long usageCount = assetRepository.countByCategoryIgnoreCase(category.getName());
        if (usageCount > 0) {
            throw new IllegalStateException("Category cannot be deleted because " + usageCount + " asset(s) use it.");
        }
        assetCategoryRepository.delete(category);
    }

    @Transactional
    public void updateParameters(Map<String, String> values) {
        for (String key : PARAMETER_KEYS) {
            SystemParameter parameter = systemParameterRepository.findById(key)
                    .orElseThrow(() -> new NoSuchElementException("System parameter not found: " + key));
            String value = values.get(key);
            parameter.setValue(validateParameterValue(key, value));
            systemParameterRepository.save(parameter);
        }
    }

    public String getParameterValue(String key, String fallback) {
        return systemParameterRepository.findById(key)
                .map(SystemParameter::getValue)
                .orElse(fallback);
    }

    public boolean isKnownActiveCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return false;
        }
        return assetCategoryRepository.findByNameIgnoreCase(categoryName)
                .map(AssetCategory::isActive)
                .orElse(false);
    }

    private void createDefaultCategory(String name, String code, String description, int usefulLife, String method) {
        AssetCategory category = new AssetCategory();
        category.setName(name);
        category.setCode(code);
        category.setDescription(description);
        category.setDefaultUsefulLifeYears(usefulLife);
        category.setDefaultResidualRate(BigDecimal.ZERO);
        category.setDefaultDepreciationMethod(method);
        category.setDefaultStatus("In Stock");
        category.setActive(true);
        assetCategoryRepository.save(category);
    }

    private void createParameterIfMissing(String key, String label, String value, String description, String type) {
        if (systemParameterRepository.existsById(key)) {
            return;
        }
        SystemParameter parameter = new SystemParameter();
        parameter.setKeyName(key);
        parameter.setLabel(label);
        parameter.setValue(value);
        parameter.setDescription(description);
        parameter.setType(type);
        systemParameterRepository.save(parameter);
    }

    private void validateCategory(AssetCategory category, Long existingId) {
        category.normalize();
        if (category.getName() == null || category.getName().isBlank()) {
            throw new IllegalArgumentException("Category name is required.");
        }
        if (category.getCode() == null || category.getCode().isBlank()) {
            throw new IllegalArgumentException("Category code is required.");
        }
        if (category.getDefaultUsefulLifeYears() == null || category.getDefaultUsefulLifeYears() < 1) {
            throw new IllegalArgumentException("Useful life must be at least 1 year.");
        }
        BigDecimal residualRate = category.getDefaultResidualRate();
        if (residualRate == null || residualRate.compareTo(BigDecimal.ZERO) < 0 || residualRate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Residual rate must be between 0 and 100.");
        }
        if (!DEPRECIATION_METHODS.contains(category.getDefaultDepreciationMethod())) {
            throw new IllegalArgumentException("Depreciation method must be SL, RB, or DDB.");
        }
        if (category.getDefaultStatus() == null || category.getDefaultStatus().isBlank()) {
            throw new IllegalArgumentException("Default status is required.");
        }
        assetCategoryRepository.findByNameIgnoreCase(category.getName())
                .filter(existing -> !existing.getId().equals(existingId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("A category with this name already exists.");
                });
        assetCategoryRepository.findAllByOrderByNameAsc().stream()
                .filter(existing -> existing.getCode().equalsIgnoreCase(category.getCode()))
                .filter(existing -> !existing.getId().equals(existingId))
                .findFirst()
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("A category with this code already exists.");
                });
    }

    private String validateParameterValue(String key, String value) {
        String clean = value == null ? "" : value.trim();
        return switch (key) {
            case "asset.code.prefix" -> {
                if (!clean.matches("[A-Za-z0-9-]{2,12}")) {
                    throw new IllegalArgumentException("Asset code prefix must be 2-12 letters, numbers, or hyphens.");
                }
                yield clean.toUpperCase();
            }
            case DEFAULT_ASSET_STATUS_KEY -> {
                if (!Set.of("In Stock", "Assigned", "Maintenance").contains(clean)) {
                    throw new IllegalArgumentException("Default asset status is invalid.");
                }
                yield clean;
            }
            case "asset.require.image", "asset.disposal.requires.approval" -> Boolean.parseBoolean(clean) ? "true" : "false";
            case "asset.lifecycle.approval.firstGroup", "asset.lifecycle.approval.finalGroup" -> {
                if (!clean.matches("[A-Za-z0-9_-]{2,60}")) {
                    throw new IllegalArgumentException("Approval group must be 2-60 letters, numbers, underscores or hyphens.");
                }
                yield clean;
            }
            case "asset.tag.type" -> {
                if (!Set.of("QR_AND_BARCODE", "QR_ONLY", "BARCODE_ONLY").contains(clean)) {
                    throw new IllegalArgumentException("Default tag type is invalid.");
                }
                yield clean;
            }
            default -> throw new IllegalArgumentException("Unsupported system parameter: " + key);
        };
    }
}
