package com.eb.cdcrag.generation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService service;
    public PlanController(PlanService service) { this.service = service; }

    @GetMapping
    public List<PlanRepository.PlanSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public PlanRepository.PlanView get(@PathVariable long id) {
        try {
            return service.get(id);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanRepository.PlanView create(@RequestBody PlanService.PlanRequest req) {
        try {
            return service.create(req);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public PlanRepository.PlanView update(@PathVariable long id, @RequestBody PlanService.PlanRequest req) {
        try {
            return service.update(id, req);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanRepository.PlanView duplicate(@PathVariable long id, @RequestBody(required = false) DuplicateRequest req) {
        try {
            return service.duplicate(id, req == null ? null : req.name());
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    public record DuplicateRequest(String name) {}
}
