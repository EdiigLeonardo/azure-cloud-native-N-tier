package com.cloud_native.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_native.backend.domain.Produto;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {
}

