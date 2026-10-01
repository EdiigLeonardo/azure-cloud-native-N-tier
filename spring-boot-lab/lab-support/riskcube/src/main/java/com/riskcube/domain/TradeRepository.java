package com.riskcube.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    /** Loads trades together with their book and desk in ONE query (no lazy loading afterwards). */
    @EntityGraph(attributePaths = {"book", "book.desk"})
    @Query("select t from Trade t")
    List<Trade> findAllWithBookAndDesk();

    List<Trade> findByCurrency(String currency);
}
