package com.riskcube.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Desk desk;

    @OneToMany(mappedBy = "book")
    private List<Trade> trades = new ArrayList<>();

    protected Book() {
    }

    public Book(String name, Desk desk) {
        this.name = name;
        this.desk = desk;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Desk getDesk() { return desk; }
    public List<Trade> getTrades() { return trades; }
}
