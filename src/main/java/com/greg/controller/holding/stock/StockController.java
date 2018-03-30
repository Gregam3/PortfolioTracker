package com.greg.controller.holding.stock;

import com.greg.entity.holding.stock.Stock;
import com.greg.service.stock.StockService;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author Greg Mitten (i7676925)
 * gregoryamitten@gmail.com
 */
@RestController
@RequestMapping("/stock/")
public class StockController {

    private static final Logger LOG = Logger.getLogger(StockController.class);

    private final StockService stockService;

    @Autowired
    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("list")
    public ResponseEntity<List<Stock>> list() {
        return new ResponseEntity<>(stockService.list(), HttpStatus.OK);
    }

    @GetMapping("check")
    public ResponseEntity<String> getStockById() throws UnirestException {
        return new ResponseEntity<>(stockService.clearStocksWithoutData(), HttpStatus.OK);
    }

    public ResponseEntity<Double> getPortfolioStockChangeOverMonth(@PathVariable("email") String email) {
        return new ResponseEntity<>(stockService.getPortfolioStockChangeOverMonth(email), HttpStatus.OK);
    }
}