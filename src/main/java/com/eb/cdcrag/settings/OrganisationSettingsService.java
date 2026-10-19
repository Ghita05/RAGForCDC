package com.eb.cdcrag.settings;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;

/** get() ne renvoie jamais vide (OrganisationSettings.empty()) : consommé par
 * ExportService, un export ne doit jamais échouer faute de configuration. */
@Service
public class OrganisationSettingsService {

    private static final Set<String> ALLOWED_LOGO_TYPES = Set.of("image/png", "image/jpeg");
    private static final long MAX_LOGO_BYTES = 2 * 1024 * 1024;

    private final OrganisationSettingsRepository repository;

    public OrganisationSettingsService(OrganisationSettingsRepository repository) {
        this.repository = repository;
    }

    public OrganisationSettings get() {
        return repository.find().orElseGet(OrganisationSettings::empty);
    }

    public void update(OrganisationSettings settings) {
        repository.save(settings);
    }

    public OrganisationSettingsRepository.LogoData getLogo() {
        return repository.findLogo().orElse(null);
    }

    public void updateLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier reçu.");
        }
        String contentType = file.getContentType();
        if (!ALLOWED_LOGO_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Format de logo non supporté (PNG ou JPG attendu) : " + contentType);
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new IllegalArgumentException("Logo trop volumineux (2 Mo maximum).");
        }
        try {
            repository.saveLogo(file.getBytes(), contentType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void clearLogo() {
        repository.clearLogo();
    }
}
