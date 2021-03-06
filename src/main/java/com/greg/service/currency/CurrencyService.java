package com.greg.service.currency;

import com.greg.entity.user.Transaction;
import com.greg.entity.user.UserHolding;
import com.greg.exceptions.InvalidHoldingException;
import com.greg.service.user.UserService;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.lang3.time.DateUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * @author Greg Mitten (i7676925)
 * gregoryamitten@gmail.com
 */
@Service
public class CurrencyService {
    private final static String CURRENCY_BASE_URL = "https://min-api.cryptocompare.com/data/";
    private final static String HISTORY_FIRST_PART = CURRENCY_BASE_URL + "histoday?fsym=";
    private final static String PRICE_DAY = CURRENCY_BASE_URL + "pricehistorical?fsym=";
    private UserService userService;

    @Autowired
    public CurrencyService(@Lazy UserService userService) {
        this.userService = userService;
    }

    /**
     * Fetches the currency history for a CryptoCurrency/Fiat
     *
     * @param userHolding The {@link com.greg.entity.holding.Holding}'s history to fetch
     * @return A Map of Currency history with the key of Dates rounded to the nearest instance of 0:00am with their total value for that date as a boxed Double
     * @throws UnirestException
     * @throws IOException
     */
    public Map<Date, Double> getCurrencyHistory(UserHolding userHolding) throws UnirestException, IOException {

        if (userHolding.getAcronym().equals(userService.getCurrentUser().getSettings().getUserCurrency().getAcronym()))
            return populateSameCurrency(userHolding.getTotalQuantity(), userHolding.getDistanceInDaysToEarliestTransactionDate());


        JSONArray history = Unirest.get(HISTORY_FIRST_PART + userHolding.getAcronym() + "&tsym=" +
                userService.getCurrentUser().getSettings().getUserCurrency().getAcronym() +
                "&limit=" + userHolding.getDistanceInDaysToEarliestTransactionDate())
                .asJson()
                .getBody()
                .getObject()
                .getJSONArray("Data");

        Queue<Transaction> transactionQueue = new PriorityQueue<>();
        transactionQueue.addAll(userHolding.getTransactions());

        //Ensures first transaction is not a watched transaction
        transactionQueue = getNextTrackedTransaction(transactionQueue);

        //If there are no non-watched transactions skip over this item
        if (transactionQueue.size() < 1)
            return null;

        //Initialising local variables after we know that processing needs to take place
        Map<Date, Double> graphHoldingDataMap = new HashMap<>();

        Transaction currentTransaction = null;
        double cumulativeQuantity = 0;
        long nextDateUnix = 0;

        for (int i = 0; i < history.length(); i++) {

            JSONObject day = history.getJSONObject(i);
            double price = day.getDouble("close");
            Date currentItemUnixDate = DateUtils.round(new Date(day.getLong("time") * 1000), Calendar.DAY_OF_MONTH);

            if (currentItemUnixDate.getTime() > nextDateUnix) {
                currentTransaction = transactionQueue.poll();
                cumulativeQuantity += currentTransaction.getQuantity();
                nextDateUnix = (transactionQueue.size() > 0) ?
                        transactionQueue.peek().getDate().getTime() : new Date().getTime();


            }

            if (currentItemUnixDate.getTime() > currentTransaction.getDate().getTime())
                graphHoldingDataMap.put(
                        currentItemUnixDate,
                        (price * cumulativeQuantity)
                );
        }

        return graphHoldingDataMap;
    }

    /**
     * Formats the queue passing over "watch" {@link Transaction}s (where their quantity is 0)
     *
     * @param queue The queue to be searched
     * @return The {@link Transaction} Queue after passing any 0 quantity {@link Transaction}s
     */
    private Queue<Transaction> getNextTrackedTransaction(Queue<Transaction> queue) {
        while (queue.peek() != null && queue.peek().getQuantity() == 0)
            queue.poll();

        return queue;
    }

    /**
     * Returns the currency value at a specified date
     *
     * @param acronym         the currency to fetch
     * @param unixDate        the date to fetch the value from
     * @param currencyDesired the currency to retrieve the value in
     * @return The price value for the specified date
     * @throws Exception
     */
    public double getValueAtDate(String acronym, long unixDate, String currencyDesired) throws Exception {
        JSONObject response =
                (JSONObject) Unirest.get(PRICE_DAY + acronym + "&tsyms=" + currencyDesired
                        + "&ts=" + unixDate / 1000)
                        .asJson().getBody().getObject().get(acronym);


        double price = response.getDouble(userService.getCurrentUser().getSettings().getUserCurrency().getAcronym());

        if (price > 0)
            return price;

        throw new InvalidHoldingException("Could not retrieve price value for this date.");

    }

    /**
     * Fetches most recent currency price
     *
     * @param acronym         the currency to fetch
     * @param currencyDesired the currency to retrieve the value in
     * @return The price value for the specified date
     * @throws UnirestException
     */
    public double getCurrentPrice(String acronym, String currencyDesired) throws UnirestException {
        JSONObject response =
                Unirest.get(CURRENCY_BASE_URL + "price?fsym=" + acronym + "&tsyms=" + currencyDesired)
                        .asJson()
                        .getBody()
                        .getObject();

        return (response.length() == 1) ? response.getDouble(currencyDesired) : -1;
    }

    private Map<Date, Double> populateSameCurrency(double quantity, long distanceToFirstDate) throws UnirestException {
        Map<Date, Double> graphHoldingDataMap = new HashMap<>();

        //fixme work around to populate data where currency to be retrieved is the same as currency to be retrieved in
        JSONArray history = Unirest.get(HISTORY_FIRST_PART + "USD&tsym=BTC" +
                "&limit=" + distanceToFirstDate)
                .asJson().getBody().getObject().getJSONArray("Data");


        Date unixIteratorAsDate = new Date();
        for (int i = 0; i < history.length(); i++) {
            JSONObject day = history.getJSONObject(i);
            unixIteratorAsDate = DateUtils.round(new Date(day.getLong("time") * 1000), Calendar.DAY_OF_MONTH);

            graphHoldingDataMap.put(
                    unixIteratorAsDate,
                    quantity
            );
        }
        //n3 chart library will not display data on graph if all values are the same, as such this small inaccuracy is permitted to
        //display the whole series, a better solution should be found in the future possibly a new library.
        graphHoldingDataMap.put(
                unixIteratorAsDate,
                quantity * 1.0000000000001
        );

        return graphHoldingDataMap;
    }

//    public Map<String, Double> getBatchPricesForUser() throws UnirestException {
//        Map<String, Double> prices = new HashMap<>();
//        StringBuilder currentNamesToFetch = new StringBuilder();
//
//        List<UserHolding> holdingsToFetch = userService.getCurrentUser().getHoldings().stream().filter(
//                userHolding ->
//                (!userHolding.getHoldingType().equals(HoldingType.STOCK))
//        ).collect(Collectors.toList());
//
//        //Only 4 currencies' values can be retrieved at time
//        for (int i = 0; i < holdingsToFetch.size(); i++) {
//            if(i > 0 && i % 4 == 0) {
//                prices.putAll(getCurrentBatch(currentNamesToFetch.toString()));
//
//                //Clear items after they've been fetched
//                currentNamesToFetch.setLength(0);
//            }
//            currentNamesToFetch.append(holdingsToFetch.get(i).getAcronym()).append(",");
//        }
//
//        //Put the remainder
//        prices.putAll(getCurrentBatch(currentNamesToFetch.toString()));
//
//        return prices;
//    }
//
//    private Map<String, Double> getCurrentBatch(String acronyms) throws UnirestException {
//        Map<String, Double> currentBatchPrices = new HashMap<>();
//
//        JSONObject response = Unirest.get(PRICE_MULTI + acronyms + "&tsyms=USD")
//                .asJson()
//                .getBody()
//                .getObject();
//
//        String[] split = acronyms.split(",");
//
//        for (String acronym : split)
//            currentBatchPrices.put(acronym, response.getDouble(acronym));
//
//        return currentBatchPrices;
//    }
}