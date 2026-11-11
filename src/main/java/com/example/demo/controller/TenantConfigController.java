package com.example.demo.controller;

import com.example.demo.dto.TenantConfigDto;
import com.example.demo.entity.TenantConfig;
import com.example.demo.repository.TenantConfigRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class TenantConfigController {

    private static final Set<String> ALLOWED_STEPS = Set.of("SERVICE", "STAFF", "STATION", "DETAILS");
    private static final List<String> REQUIRED_CORE_STEPS = List.of("SERVICE", "STAFF", "DETAILS");
    private static final List<String> DEFAULT_STEP_ORDER = List.of("SERVICE", "STAFF", "STATION", "DETAILS");
    private static final Set<String> ALLOWED_LAYOUTS = Set.of("classic", "compact", "split", "luxe");

    private final TenantConfigRepository tenantConfigRepository;

    public TenantConfigController(TenantConfigRepository tenantConfigRepository) {
        this.tenantConfigRepository = tenantConfigRepository;
    }

    @GetMapping("/api/public/tenant-config")
    public ResponseEntity<TenantConfigDto> getPublicTenantConfig(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestParam(value = "tenant", required = false) String tenantParam,
            @RequestParam(value = "domain", required = false) String domainParam,
            @RequestParam(value = "strict", defaultValue = "false") boolean strict,
            HttpServletRequest request
    ) {
        Optional<TenantConfig> resolved = resolveTenant(tenantHeader, tenantParam, domainParam, strict, request);
        if (resolved.isEmpty() && strict) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Requested salon tenant was not found");
        }
        TenantConfig config = resolved.orElseGet(this::buildFallbackDefaultConfig);
        return ResponseEntity.ok(toDto(config, true));
    }

    @GetMapping("/api/admin/tenant-config")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    public ResponseEntity<List<TenantConfigDto>> listAllTenantConfigs() {
        List<TenantConfigDto> configs = tenantConfigRepository.findAll().stream()
                .map(cfg -> toDto(cfg, false))
                .toList();
        return ResponseEntity.ok(configs);
    }

    @PutMapping("/api/admin/tenant-config/{tenantKey}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    public ResponseEntity<TenantConfigDto> upsertTenantConfig(
            @PathVariable String tenantKey,
            @RequestBody TenantConfigDto payload
    ) {
        String normalizedKey = normalizeKey(tenantKey);
        if (normalizedKey.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantKey must not be blank");
        }

        TenantConfig entity = tenantConfigRepository.findByTenantKeyIgnoreCase(normalizedKey)
                .orElseGet(() -> {
                    TenantConfig created = new TenantConfig();
                    created.setTenantKey(normalizedKey);
                    return created;
                });

        applyValidatedUpdates(entity, payload);
        TenantConfig saved = tenantConfigRepository.save(entity);
        return ResponseEntity.ok(toDto(saved, false));
    }

    private Optional<TenantConfig> resolveTenant(
            String tenantHeader,
            String tenantParam,
            String domainParam,
            boolean strict,
            HttpServletRequest request
    ) {
        String explicitKey = firstNonBlank(tenantHeader, tenantParam);
        if (explicitKey != null) {
            Optional<TenantConfig> byKey = tenantConfigRepository.findByTenantKeyIgnoreCase(explicitKey.trim());
            if (byKey.isPresent() || strict) {
                return byKey;
            }
        }

        String host = (domainParam != null && !domainParam.isBlank())
                ? cleanHostString(domainParam)
                : extractHost(request);
        if (host != null && !host.isBlank()) {
            Optional<TenantConfig> byDomain = tenantConfigRepository.findByCustomDomainIgnoreCase(host);
            if (byDomain.isPresent()) {
                return byDomain;
            }
            String subdomain = extractSubdomain(host);
            if (subdomain != null) {
                Optional<TenantConfig> bySubdomain = tenantConfigRepository.findByTenantKeyIgnoreCase(subdomain);
                if (bySubdomain.isPresent() || strict) {
                    return bySubdomain;
                }
            }
        }

        return tenantConfigRepository.findFirstByDefaultTenantTrue()
                .or(() -> tenantConfigRepository.findByTenantKeyIgnoreCase("default"));
    }

    private void applyValidatedUpdates(TenantConfig entity, TenantConfigDto payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuration payload is required");
        }

        if (payload.getSalonName() != null && !payload.getSalonName().isBlank()) {
            entity.setSalonName(payload.getSalonName().trim());
        }
        if (payload.getCustomDomain() != null) {
            String domain = payload.getCustomDomain().trim().toLowerCase(Locale.ROOT);
            entity.setCustomDomain(domain.isEmpty() ? null : domain);
        }
        if (payload.getLayoutVariant() != null && !payload.getLayoutVariant().isBlank()) {
            String variant = payload.getLayoutVariant().trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_LAYOUTS.contains(variant)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported layoutVariant: " + variant);
            }
            entity.setLayoutVariant(variant);
        }

        if (payload.getTheme() != null) {
            TenantConfigDto.ThemeConfig theme = payload.getTheme();
            if (theme.getPrimaryColor() != null && !theme.getPrimaryColor().isBlank()) {
                String color = theme.getPrimaryColor().trim();
                if (!color.matches("^#[0-9a-fA-F]{3,8}$")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid hex primaryColor: " + color);
                }
                entity.setPrimaryColor(color);
            }
            if (theme.getSecondaryColor() != null && !theme.getSecondaryColor().isBlank()) {
                String color = theme.getSecondaryColor().trim();
                if (!color.matches("^#[0-9a-fA-F]{3,8}$")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid hex secondaryColor: " + color);
                }
                entity.setSecondaryColor(color);
            }
            if (theme.getFontFamily() != null && !theme.getFontFamily().isBlank()) {
                entity.setFontFamily(sanitizeFontFamily(theme.getFontFamily()));
            }
            if (theme.getLogoUrl() != null) {
                entity.setLogoUrl(sanitizeAssetUrl(theme.getLogoUrl()));
            }
            if (theme.getBannerImageUrl() != null) {
                entity.setBannerImageUrl(sanitizeAssetUrl(theme.getBannerImageUrl()));
            }
        }

        if (payload.getWizard() != null) {
            TenantConfigDto.WizardLayoutConfig wizard = payload.getWizard();
            entity.setEnableStationSelection(wizard.isEnableStationSelection());
            entity.setShowAppointmentNotes(wizard.isShowAppointmentNotes());
            // Customer-facing UI must never expose staff qualification badges
            entity.setShowStaffQualificationTags(false);

            if (wizard.getStepOrder() != null && !wizard.getStepOrder().isEmpty()) {
                List<String> validatedSteps = validateStepOrder(wizard.getStepOrder());
                entity.setStepOrder(String.join(",", validatedSteps));
            }
            if (wizard.getAvailableStations() != null) {
                List<String> cleanedStations = cleanStringList(wizard.getAvailableStations());
                entity.setAvailableStations(String.join(",", cleanedStations));
            }
            if (wizard.getEnabledCategories() != null) {
                List<String> cleanedCategories = cleanStringList(wizard.getEnabledCategories());
                entity.setEnabledCategories(String.join(",", cleanedCategories));
            }
        }
    }

    private List<String> validateStepOrder(List<String> rawSteps) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String step : rawSteps) {
            if (step == null || step.isBlank()) {
                continue;
            }
            String upper = step.trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_STEPS.contains(upper)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown wizard step: " + step);
            }
            normalized.add(upper);
        }
        for (String required : REQUIRED_CORE_STEPS) {
            if (!normalized.contains(required)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "stepOrder must include required step: " + required
                );
            }
        }
        List<String> ordered = new ArrayList<>(normalized);
        if (ordered.indexOf("DETAILS") != ordered.size() - 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "DETAILS must remain the final confirmation step"
            );
        }
        return ordered;
    }

    private TenantConfigDto toDto(TenantConfig entity, boolean enforceCustomerPrivacy) {
        List<String> rawOrder = splitCsv(entity.getStepOrder());
        if (rawOrder.isEmpty()) {
            rawOrder = new ArrayList<>(DEFAULT_STEP_ORDER);
        }
        List<String> effectiveSteps = new ArrayList<>();
        for (String step : rawOrder) {
            String upper = step.toUpperCase(Locale.ROOT);
            if ("STATION".equals(upper) && !entity.isEnableStationSelection()) {
                continue;
            }
            if (ALLOWED_STEPS.contains(upper) && !effectiveSteps.contains(upper)) {
                effectiveSteps.add(upper);
            }
        }

        boolean showTags = !enforceCustomerPrivacy && entity.isShowStaffQualificationTags();

        TenantConfigDto.ThemeConfig theme = new TenantConfigDto.ThemeConfig(
                entity.getPrimaryColor(),
                entity.getSecondaryColor(),
                entity.getFontFamily(),
                entity.getLogoUrl(),
                entity.getBannerImageUrl()
        );
        TenantConfigDto.WizardLayoutConfig wizard = new TenantConfigDto.WizardLayoutConfig(
                effectiveSteps,
                entity.isEnableStationSelection(),
                splitCsv(entity.getAvailableStations()),
                splitCsv(entity.getEnabledCategories()),
                entity.isShowAppointmentNotes(),
                showTags
        );

        return new TenantConfigDto(
                entity.getTenantKey(),
                entity.getCustomDomain(),
                entity.getSalonName(),
                entity.getLayoutVariant(),
                theme,
                wizard
        );
    }

    private TenantConfig buildFallbackDefaultConfig() {
        TenantConfig cfg = new TenantConfig();
        cfg.setTenantKey("default");
        cfg.setSalonName("Jenny's Nail Studio");
        cfg.setPrimaryColor("#e83e8c");
        cfg.setSecondaryColor("#343a40");
        cfg.setFontFamily("'Poppins', sans-serif");
        cfg.setStepOrder("SERVICE,STAFF,STATION,DETAILS");
        cfg.setEnableStationSelection(false);
        cfg.setShowAppointmentNotes(true);
        cfg.setShowStaffQualificationTags(false);
        cfg.setLayoutVariant("classic");
        cfg.setDefaultTenant(true);
        return cfg;
    }

    private String extractHost(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String host = (forwardedHost != null && !forwardedHost.isBlank())
                ? forwardedHost.split(",")[0].trim()
                : request.getHeader("Host");
        return cleanHostString(host);
    }

    private String cleanHostString(String host) {
        if (host == null) {
            return null;
        }
        String cleaned = host.trim();
        int colonIdx = cleaned.indexOf(':');
        if (colonIdx > -1) {
            cleaned = cleaned.substring(0, colonIdx);
        }
        return cleaned.trim().toLowerCase(Locale.ROOT);
    }

    private String extractSubdomain(String host) {
        if (host == null || host.equals("localhost") || host.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) {
            return null;
        }
        String[] parts = host.split("\\.");
        if (parts.length >= 3 || (parts.length == 2 && "localhost".equals(parts[1]))) {
            String candidate = parts[0];
            if (!"www".equals(candidate) && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private String sanitizeFontFamily(String font) {
        String cleaned = font.replaceAll("[;<>{}]", "").trim();
        return cleaned.isEmpty() ? "'Poppins', sans-serif" : cleaned;
    }

    private String sanitizeAssetUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("/")) {
            return trimmed;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset URL must use http(s) or a relative '/' path");
    }

    private List<String> cleanStringList(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-_]", "");
    }
}
