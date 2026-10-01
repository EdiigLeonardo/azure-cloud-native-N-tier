package com.cloud_native.backend.controller;

import com.cloud_native.backend.domain.Produto;
import com.cloud_native.backend.dto.ItemDto;
import com.cloud_native.backend.repository.ProdutoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/items")
public class ItemsController {

    private final com.cloud_native.backend.service.ItemsService service;

    public ItemsController(com.cloud_native.backend.service.ItemsService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> createItem(@RequestBody @jakarta.validation.Valid ItemDto dto) {
        Produto saved = service.create(dto);
        URI location = URI.create("/api/items/" + saved.getId());
        return ResponseEntity.created(location).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Produto> getItem(@PathVariable Long id) {
        Produto p = service.findById(id);
        return ResponseEntity.ok(p);
    }
}
