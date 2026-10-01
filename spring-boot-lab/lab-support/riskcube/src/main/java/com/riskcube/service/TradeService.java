package com.riskcube.service;

import com.riskcube.domain.Book;
import com.riskcube.domain.BookRepository;
import com.riskcube.domain.Trade;
import com.riskcube.domain.TradeRepository;
import com.riskcube.web.TradeRequest;
import com.riskcube.web.TradeView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeService {

    private final TradeRepository tradeRepository;
    private final BookRepository bookRepository;

    public TradeService(TradeRepository tradeRepository, BookRepository bookRepository) {
        this.tradeRepository = tradeRepository;
        this.bookRepository = bookRepository;
    }

    @Transactional(readOnly = true)
    public TradeView get(Long id) {
        Trade trade = tradeRepository.findById(id).orElseThrow();
        return TradeView.from(trade);
    }

    @Transactional
    public TradeView create(TradeRequest request) {
        Book book = bookRepository.findByName(request.bookName())
                .orElseThrow(() -> new IllegalArgumentException("unknown book " + request.bookName()));
        Trade trade = new Trade(request.externalId(), book, request.counterparty(), request.currency(),
                request.notional(), request.pnl(), request.tradeDate());
        return TradeView.from(tradeRepository.save(trade));
    }

    @Transactional
    public void delete(Long id) {
        tradeRepository.deleteById(id);
    }
}
