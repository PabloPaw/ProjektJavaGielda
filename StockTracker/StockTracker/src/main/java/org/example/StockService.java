package org.example;

import javafx.application.Platform;
import javafx.collections.ObservableList;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StockService {
    private final Random random = new Random();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();


    private volatile double currentUsdRate = 4.0;


    public List<Stock> getStocks() {
        List<Stock> stocks = new ArrayList<>();


        double fetchedUsd = fetchNbpRate("usd");
        if (fetchedUsd > 0) currentUsdRate = fetchedUsd;


        stocks.add(new Stock("BITCOIN", fetchCryptoWithBackup()));


        stocks.add(new Stock("ZABKA", fetchStooqPrice("zab", 19.50)));
        stocks.add(new Stock("CCC", fetchStooqPrice("ccc", 180.0)));
        stocks.add(new Stock("PKO_BP", fetchStooqPrice("pko", 58.0)));
        stocks.add(new Stock("PEKAO", fetchStooqPrice("peo", 155.0)));
        stocks.add(new Stock("PKNORLEN", fetchStooqPrice("pkn", 65.0)));
        stocks.add(new Stock("KGHM", fetchStooqPrice("kgh", 115.0)));
        stocks.add(new Stock("ALLEGRO", fetchStooqPrice("ale", 32.0)));
        stocks.add(new Stock("CDPROJEKT", fetchStooqPrice("cdr", 150.0)));
        stocks.add(new Stock("DINO", fetchStooqPrice("dnp", 380.0)));
        stocks.add(new Stock("PZU", fetchStooqPrice("pzu", 49.0)));
        stocks.add(new Stock("LPP", fetchStooqPrice("lpp", 17000.0)));
        stocks.add(new Stock("PEPCO", fetchStooqPrice("pco", 23.0)));
        stocks.add(new Stock("SANTANDER", fetchStooqPrice("spl", 560.0)));
        stocks.add(new Stock("MBANK", fetchStooqPrice("mbk", 690.0)));
        stocks.add(new Stock("ALIOR", fetchStooqPrice("alr", 95.0)));
        stocks.add(new Stock("KRUK", fetchStooqPrice("kru", 460.0)));
        stocks.add(new Stock("KETY", fetchStooqPrice("kty", 800.0)));
        stocks.add(new Stock("BUDIMEX", fetchStooqPrice("bdx", 700.0)));
        stocks.add(new Stock("PGE", fetchStooqPrice("pge", 7.50)));
        stocks.add(new Stock("ORANGE", fetchStooqPrice("opl", 8.50)));

        return stocks;
    }

    public List<Stock> getCurrencies() {
        List<Stock> currencies = new ArrayList<>();

        currencies.add(new Stock("USD/PLN", currentUsdRate));
        currencies.add(new Stock("EUR/PLN", fetchNbpRate("eur")));
        currencies.add(new Stock("CHF/PLN", fetchNbpRate("chf")));
        currencies.add(new Stock("GBP/PLN", fetchNbpRate("gbp")));
        return currencies;
    }


    public void startMarketMonitor(ObservableList<Stock> mainStocks, ObservableList<Stock> currencyStocks) {

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();


        scheduler.scheduleAtFixedRate(() -> {
            try {
                double btcPln = fetchCryptoWithBackup();
                if (btcPln > 0) {
                    Platform.runLater(() -> updateStockInList(mainStocks, "BITCOIN", btcPln));
                }
            } catch (Exception e) { e.printStackTrace(); }
        }, 2, 5, TimeUnit.SECONDS);


    }

    private void updateStockInList(ObservableList<Stock> stocks, String symbol, double newPrice) {
        if (newPrice <= 0) return;
        for (Stock s : stocks) {
            if (s.getSymbol().equals(symbol)) {
                double oldPrice = s.getPrice();
                if (oldPrice > 0 && Math.abs(newPrice - oldPrice) > 0.0001) {
                    double change = ((newPrice - oldPrice) / oldPrice) * 100.0;
                    s.setChange(Math.round(change * 100.0) / 100.0);
                }
                s.setPrice(newPrice);
                break;
            }
        }
    }


    private double fetchCryptoWithBackup() {
        double priceUsd = fetchBinancePrice();
        if (priceUsd <= 0) priceUsd = fetchCoinCapPrice();
        if (priceUsd > 0) return priceUsd * currentUsdRate;
        return 380000.0;
    }

    private double fetchStooqPrice(String symbol, double fallback) {
        try {
            String url = "https://stooq.pl/q/l/?s=" + symbol + "&f=sd2t2ohlc&h&e=csv";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0").GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String[] lines = response.body().split("\n");
                if (lines.length > 1) {
                    String[] cols = lines[1].split(",");
                    if (cols.length >= 7) return Double.parseDouble(cols[6]);
                }
            }
        } catch (Exception e) { }
        return fallback;
    }

    private double fetchNbpRate(String curr) {
        try {
            String url = "http://api.nbp.pl/api/exchangerates/rates/a/" + curr + "/?format=json";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String json = response.body();
                String key = "\"mid\":";
                int start = json.indexOf(key);
                if (start != -1) {
                    start += key.length();
                    int end = json.indexOf("}", start);
                    if (end == -1) end = json.indexOf(",", start);
                    return Double.parseDouble(json.substring(start, end));
                }
            }
        } catch (Exception e) { }
        return -1;
    }

    private double fetchBinancePrice() {
        try {
            String url = "https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0").GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String json = response.body();
                String search = "\"price\":\"";
                int start = json.indexOf(search);
                if (start != -1) {
                    start += search.length();
                    int end = json.indexOf("\"", start);
                    return Double.parseDouble(json.substring(start, end));
                }
            }
        } catch (Exception e) { }
        return -1;
    }

    private double fetchCoinCapPrice() {
        try {
            String url = "https://api.coincap.io/v2/assets/bitcoin";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0").GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String json = response.body();
                String search = "\"priceUsd\":\"";
                int start = json.indexOf(search);
                if (start != -1) {
                    start += search.length();
                    int end = json.indexOf("\"", start);
                    return Double.parseDouble(json.substring(start, end));
                }
            }
        } catch (Exception e) { }
        return -1;
    }


    public void updateStockPrice(Stock stock) {
        if (stock.getSymbol().equals("BITCOIN")) return;
        double current = stock.getPrice();
        double chg = (random.nextDouble() - 0.5) * 0.01;
        double next = current + (current * chg);
        stock.setPrice(Math.round(next * 100.0) / 100.0);
        stock.setChange(Math.round(chg * 10000.0) / 100.0);
    }
}