package com.eb.cdcrag.ingestion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lecture seule sur le corpus ingéré ; l'import passe par IngestionController. */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRepository repository;
    public DocumentController(DocumentRepository repository) { this.repository = repository; }

    @GetMapping
    public List<DocumentRepository.DocumentSummary> list() {
        return repository.findAll();
    }
}
