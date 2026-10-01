package com.cloud_native.backend.service;

import com.cloud_native.backend.domain.Produto;
import com.cloud_native.backend.dto.ItemDto;
import com.cloud_native.backend.exception.ResourceNotFoundException;
import com.cloud_native.backend.repository.ProdutoRepository;
import org.springframework.stereotype.Service;

@Service
public class ItemsService {

    private final ProdutoRepository repository;

    public ItemsService(ProdutoRepository repository) {
        this.repository = repository;
    }

    public Produto create(ItemDto dto) {
        Produto p = new Produto();
        p.setNome(dto.getNome());
        p.setPreco(dto.getPreco());
        return repository.save(p);
    }

    public Produto findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Item not found: " + id));
    }
}
