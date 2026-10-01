package com.riskcube.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeskRepository extends JpaRepository<Desk, Long> {

    Optional<Desk> findByName(String name);
}
