package com.eb.cdcrag.settings;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/organisation-settings")
public class OrganisationSettingsController {

    private final OrganisationSettingsService service;
    public OrganisationSettingsController(OrganisationSettingsService service) { this.service = service; }

    @GetMapping
    public OrganisationSettings get() {
        return service.get();
    }

    @PutMapping
    public OrganisationSettings update(@RequestBody OrganisationSettings settings) {
        service.update(settings);
        return service.get();
    }

    /** Octets du logo, séparés de GET / pour ne pas charger l'image à chaque
     * lecture des paramètres -- voir OrganisationSettings. */
    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() {
        OrganisationSettingsRepository.LogoData logo = service.getLogo();
        if (logo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.contentType())).body(logo.bytes());
    }

    @PostMapping("/logo")
    public OrganisationSettings uploadLogo(@RequestParam("file") MultipartFile file) {
        try {
            service.updateLogo(file);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return service.get();
    }

    @DeleteMapping("/logo")
    public OrganisationSettings clearLogo() {
        service.clearLogo();
        return service.get();
    }
}
