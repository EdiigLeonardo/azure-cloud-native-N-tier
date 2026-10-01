package com.riskcube.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false, unique = true)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Book book;

    private String counterparty;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal notional;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal pnl;

    private LocalDate tradeDate;

    protected Trade() {
    }

    public Trade(String externalId, Book book, String counterparty, String currency,
                 BigDecimal notional, BigDecimal pnl, LocalDate tradeDate) {
        this.externalId = externalId;
        this.book = book;
        this.counterparty = counterparty;
        this.currency = currency;
        this.notional = notional;
        this.pnl = pnl;
        this.tradeDate = tradeDate;
    }

    public Long getId() { return id; }
    public String getExternalId() { return externalId; }
    public Book getBook() { return book; }
    public String getCounterparty() { return counterparty; }
    public String getCurrency() { return currency; }
    public BigDecimal getNotional() { return notional; }
    public BigDecimal getPnl() { return pnl; }
    public LocalDate getTradeDate() { return tradeDate; }
}
