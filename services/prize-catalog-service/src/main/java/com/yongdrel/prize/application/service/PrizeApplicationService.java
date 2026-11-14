package com.yongdrel.prize.application.service;

import com.yongdrel.prize.adapter.in.web.dto.PrizeRequest;
import com.yongdrel.prize.adapter.out.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class PrizeApplicationService {

    private final PrizeRepository repository;

    public PrizeApplicationService(PrizeRepository repository) {
        this.repository = repository;
    }

    public PrizeEntity create(PrizeRequest request, String userId) {
        PrizeEntity prize = new PrizeEntity();
        prize.createdBy = userId;
        apply(prize, request);
        return repository.save(prize);
    }

    public List<PrizeEntity> findAll() {
        return repository.findAll();
    }

    public PrizeEntity findById(UUID id) {
        return repository.findById(id).orElseThrow(() ->
            new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Prize not found"));
    }

    public PrizeEntity update(UUID id, PrizeRequest request, String userId) {
        PrizeEntity prize = findById(id);
        requireOwner(prize, userId);
        apply(prize, request);
        return repository.save(prize);
    }

    public void delete(UUID id, String userId) {
        PrizeEntity prize = findById(id);
        requireOwner(prize, userId);
        repository.delete(prize);
    }

    private void requireOwner(PrizeEntity prize, String userId) {
        if (!prize.createdBy.equals(userId)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Not prize owner");
        }
    }

    private void apply(PrizeEntity prize, PrizeRequest request) {
        prize.name = request.name().trim();
        prize.description = request.description();
        prize.value = request.value();
        prize.quantity = request.quantity();
        prize.status = request.status();
    }
}
