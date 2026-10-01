package com.cloud_native.backend.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "produtos")
@Data // Gera getters, setters, equals, hashcode e tostring
@NoArgsConstructor // Construtor sem argumentos (obrigatório para o JPA)
@AllArgsConstructor // Construtor com todos os argumentos
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String nome;
    private Double preco;
}
