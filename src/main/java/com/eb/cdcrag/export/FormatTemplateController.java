package com.eb.cdcrag.export;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/format-templates")
public class FormatTemplateController {

    private final FormatTemplateService service;
    public FormatTemplateController(FormatTemplateService service) { this.service = service; }

    @GetMapping
    public List<FormatTemplateService.FormatTemplateView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public FormatTemplateService.FormatTemplateView get(@PathVariable long id) {
        try {
            return service.get(id);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FormatTemplateService.FormatTemplateView upload(@RequestParam String name,
                                                           @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier est vide");
        }
        try {
            return service.upload(name, file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier illisible : " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/{id}/set-default")
    public FormatTemplateService.FormatTemplateView setDefault(@PathVariable long id) {
        try {
            service.setDefault(id);
            return service.get(id);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        try {
            service.delete(id);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }
}
