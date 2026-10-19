package com.eb.cdcrag.review;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewRepository repository;
    private final ReviewWorker worker;

    public ReviewController(ReviewRepository repository, ReviewWorker worker) {
        this.repository = repository;
        this.worker = worker;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> start(@RequestParam long cdcId) {
        long id = repository.start(cdcId);
        worker.run(id, cdcId);
        return Map.of("reviewId", id, "cdcId", cdcId, "status", "RUNNING");
    }

    @GetMapping("/{id}")
    public ReviewRepository.ReviewRecord get(@PathVariable long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review " + id + " introuvable"));
    }
}
