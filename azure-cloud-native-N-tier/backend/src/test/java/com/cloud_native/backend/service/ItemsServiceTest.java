package com.cloud_native.backend.service;

import com.cloud_native.backend.domain.Produto;
import com.cloud_native.backend.dto.ItemDto;
import com.cloud_native.backend.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemsServiceTest {

    @Mock
    private ProdutoRepository repository;

    @InjectMocks
    private ItemsService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void create_savesAndReturnsProduto() {
        ItemDto dto = new ItemDto();
        dto.setNome("X");
        dto.setPreco(5.0);

        Produto saved = new Produto(1L, "X", 5.0);
        when(repository.save(any())).thenReturn(saved);

        Produto result = service.create(dto);

        assertNotNull(result);
        assertEquals("X", result.getNome());
        verify(repository, times(1)).save(any());
    }

    @Test
    void findById_returnsWhenFound() {
        Produto p = new Produto(1L, "Y", 2.0);
        when(repository.findById(1L)).thenReturn(Optional.of(p));

        Produto res = service.findById(1L);
        assertEquals(1L, res.getId());
        assertEquals("Y", res.getNome());
    }
}
