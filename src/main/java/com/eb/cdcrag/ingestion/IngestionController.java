package com.eb.cdcrag.ingestion;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestionService;
    private final DocumentTextExtractor extractor;

    public IngestionController(IngestionService ingestionService, DocumentTextExtractor extractor) {
        this.ingestionService = ingestionService;
        this.extractor = extractor;
    }

    @PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    public IngestionService.IngestResult ingestText(
            @RequestParam String filename,
            @RequestParam(required = false) String cdcType,
            @RequestBody String content) {
        return ingestionService.ingest(filename, cdcType, content);
    }

    @PostMapping(path = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestionService.IngestResult ingestFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String cdcType) throws IOException {
        String text = extractor.extract(file.getOriginalFilename(), file.getBytes());
        return ingestionService.ingest(file.getOriginalFilename(), cdcType, text);
    }
}
