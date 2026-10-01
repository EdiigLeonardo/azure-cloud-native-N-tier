package com.cloud_native.backend.controller;

import com.cloud_native.backend.dto.ItemDto;
import com.cloud_native.backend.domain.Produto;
import com.cloud_native.backend.service.ItemsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemsControllerUnitTest {

    private MockMvc mockMvc;

    @Mock
    private ItemsService service;

    @InjectMocks
    private ItemsController controller;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new com.cloud_native.backend.advice.ApiExceptionHandler())
            .build();
    }

    @Test
    void postValid_returns201() throws Exception {
        ItemDto dto = new ItemDto();
        dto.setNome("A");
        dto.setPreco(1.0);

        when(service.create(any())).thenReturn(new Produto(1L, "A", 1.0));

        String json = "{\"nome\":\"A\",\"preco\":1.0}";

        mockMvc.perform(post("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void postInvalid_returns400() throws Exception {
        String json = "{\"nome\":\"\",\"preco\":-1}";

        mockMvc.perform(post("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
    }
}
