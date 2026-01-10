package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StockApp extends Application {

    private final StockService stockService = new StockService();
    private final ObservableList<Stock> stockData = FXCollections.observableArrayList();
    private final ObservableList<PortfolioItem> myPortfolioData = FXCollections.observableArrayList();
    private final Map<String, XYChart.Series<String, Number>> stockSeriesMap = new HashMap<>();

    private LineChart<String, Number> lineChart;
    private Stock selectedStock;

    // UI Elements
    private Label cashLabel;
    private Label totalValueLabel;
    private Label ownedLabel;
    private Label activeAlertLabel;

    // Nowe elementy do wyboru trybu kupna
    private RadioButton rbQuantity; // Tryb "Ilość sztuk"
    private RadioButton rbValue;    // Tryb "Za kwotę"
    private TextField amountField;  // Pole wpisywania

    private TableView<Stock> marketTable;
    private TableView<PortfolioItem> portfolioTable;

    private double cash = 50000.00; // Dałem więcej kasy na start do testów
    private boolean isDarkMode = true;
    private Scene scene;

    @Override
    public void start(Stage primaryStage) {
        stockData.addAll(stockService.getInitialStocks());
        if (!stockData.isEmpty()) selectedStock = stockData.get(0);

        stockService.startMarketMonitor(stockData);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab marketTab = new Tab("Rynek i Handel");
        marketTab.setContent(createMarketView());
        Tab portfolioTab = new Tab("Mój Portfel");
        portfolioTab.setContent(createPortfolioView());
        tabPane.getTabs().addAll(marketTab, portfolioTab);

        BorderPane root = new BorderPane();
        root.setCenter(tabPane);
        root.setBottom(createStatusBar());

        startMarketSimulation();

        scene = new Scene(root, 1150, 750);
        applyTheme();

        primaryStage.setTitle("WIG20 Pro Trader - Fractional Trading");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        applyTheme();
        if (marketTable != null) marketTable.refresh();
        if (portfolioTable != null) portfolioTable.refresh();
    }

    private void applyTheme() {
        scene.getStylesheets().clear();
        if (isDarkMode) {
            var cssResource = getClass().getResource("/styles.css");
            if (cssResource != null) scene.getStylesheets().add(cssResource.toExternalForm());
        }
    }

    private BorderPane createMarketView() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(10));
        pane.setLeft(createStockTable());
        pane.setCenter(createChartSection());
        pane.setRight(createRightPanel());
        return pane;
    }

    private VBox createStockTable() {
        marketTable = new TableView<>();
        marketTable.setItems(stockData);
        marketTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Stock, String> symCol = new TableColumn<>("Symbol");
        symCol.setCellValueFactory(new PropertyValueFactory<>("symbol"));

        TableColumn<Stock, Double> priceCol = new TableColumn<>("Kurs");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));

        TableColumn<Stock, Double> chgCol = new TableColumn<>("Zmiana");
        chgCol.setCellValueFactory(new PropertyValueFactory<>("change"));

        symCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) {
                    if (item.equals("WIG20")) setStyle("-fx-font-weight: bold; -fx-text-fill: #FFD700;");
                    else if (item.equals("BITCOIN")) setStyle("-fx-font-weight: bold; -fx-text-fill: #F7931A;");
                    else {
                        setStyle("");
                        setTextFill(isDarkMode ? Color.WHITE : Color.BLACK);
                    }
                }
            }
        });

        priceCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(String.format("%.2f", item));
                setTextFill(isDarkMode ? Color.WHITE : Color.BLACK);
            }
        });

        chgCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(String.format("%.2f%%", item));
                if (item > 0) setTextFill(Color.web("#00AA00"));
                else if (item < 0) setTextFill(Color.RED);
                else setTextFill(isDarkMode ? Color.WHITE : Color.BLACK);
            }
        });

        marketTable.getColumns().addAll(symCol, priceCol, chgCol);
        marketTable.getSelectionModel().selectedItemProperty().addListener((o, old, newVal) -> {
            if (newVal != null) {
                selectedStock = newVal;
                refreshChart();
                updateOwnedLabel();
                updateAlertLabel();
            }
        });

        VBox box = new VBox(10, new Label("Notowania"), marketTable);
        box.setPrefWidth(300);
        return box;
    }

    private VBox createChartSection() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setTickLabelsVisible(true);
        xAxis.setLabel("Czas");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setAutoRanging(true);
        yAxis.setForceZeroInRange(false);
        lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Wykres (Live)");
        lineChart.setAnimated(false);
        lineChart.setCreateSymbols(false);
        lineChart.setLegendVisible(false);
        VBox box = new VBox(lineChart);
        box.setPadding(new Insets(0, 10, 0, 10));
        VBox.setVgrow(lineChart, Priority.ALWAYS);
        return box;
    }

    // --- ZMODYFIKOWANY PANEL HANDLU (UŁAMKI I KWOTY) ---
    private VBox createRightPanel() {
        Label tradeTitle = new Label("Panel Handlu");
        tradeTitle.setStyle("-fx-font-weight: bold;");

        ownedLabel = new Label("Posiadasz: 0.0000 szt."); // Więcej miejsc po przecinku
        ownedLabel.setTextFill(Color.GRAY);

        // Wybór trybu
        ToggleGroup group = new ToggleGroup();
        rbQuantity = new RadioButton("Ilość (szt.)");
        rbQuantity.setToggleGroup(group);
        rbQuantity.setSelected(true); // Domyślnie
        rbQuantity.setTextFill(Color.LIGHTGRAY);

        rbValue = new RadioButton("Kwota (PLN)");
        rbValue.setToggleGroup(group);
        rbValue.setTextFill(Color.LIGHTGRAY);

        // Zmiana podpowiedzi w polu tekstowym zależnie od wyboru
        amountField = new TextField();
        amountField.setPromptText("Wpisz ilość...");

        group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (rbQuantity.isSelected()) amountField.setPromptText("Np. 0.5 lub 10");
            else amountField.setPromptText("Np. 2000 PLN");
        });

        HBox radioBox = new HBox(10, rbQuantity, rbValue);
        radioBox.setAlignment(Pos.CENTER_LEFT);

        Button buyBtn = new Button("KUP");
        buyBtn.setStyle("-fx-background-color: #2ea043; -fx-text-fill: white;");
        buyBtn.setOnAction(e -> handleTransaction(true));

        Button sellBtn = new Button("SPRZEDAJ");
        sellBtn.setStyle("-fx-background-color: #d73a49; -fx-text-fill: white;");
        sellBtn.setOnAction(e -> handleTransaction(false));

        HBox tradeBtns = new HBox(5, buyBtn, sellBtn);
        tradeBtns.setAlignment(Pos.CENTER);

        VBox tradeBox = new VBox(10, tradeTitle, ownedLabel, new Separator(), radioBox, amountField, tradeBtns);
        tradeBox.setStyle("-fx-border-color: #555; -fx-padding: 10; -fx-border-radius: 5;");

        // Alerty
        Label alertTitle = new Label("Alerty Cenowe");
        alertTitle.setStyle("-fx-font-weight: bold;");
        activeAlertLabel = new Label("Brak ustawień");
        activeAlertLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #aaa;");
        TextField minPriceField = new TextField();
        minPriceField.setPromptText("Alarm spadku");
        TextField maxPriceField = new TextField();
        maxPriceField.setPromptText("Alarm wzrostu");
        Button setAlertBtn = new Button("Zapisz Alerty");
        setAlertBtn.setMaxWidth(Double.MAX_VALUE);
        setAlertBtn.setOnAction(e -> handleSetAlerts(minPriceField, maxPriceField));
        VBox alertBox = new VBox(10, alertTitle, activeAlertLabel, new Label("Min:"), minPriceField, new Label("Max:"), maxPriceField, setAlertBtn);
        alertBox.setStyle("-fx-border-color: #555; -fx-padding: 10; -fx-border-radius: 5;");

        VBox mainRight = new VBox(20, tradeBox, alertBox);
        mainRight.setPadding(new Insets(0, 5, 0, 5));
        mainRight.setPrefWidth(240); // Trochę szerszy panel
        return mainRight;
    }

    private VBox createPortfolioView() {
        portfolioTable = new TableView<>();
        portfolioTable.setItems(myPortfolioData);
        portfolioTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<PortfolioItem, String> symCol = new TableColumn<>("Symbol");
        symCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStock().getSymbol()));

        // Ilość jako Double (4 miejsca po przecinku dla krypto)
        TableColumn<PortfolioItem, Double> qtyCol = new TableColumn<>("Ilość");
        qtyCol.setCellValueFactory(cell -> cell.getValue().quantityProperty().asObject());
        qtyCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    setText(String.format("%.4f", item)); // 4 miejsca po przecinku
                    setTextFill(isDarkMode ? Color.WHITE : Color.BLACK);
                }
            }
        });

        TableColumn<PortfolioItem, Double> valCol = new TableColumn<>("Wartość");
        valCol.setCellValueFactory(cell -> new SimpleDoubleProperty(
                cell.getValue().getQuantity() * cell.getValue().getStock().getPrice()
        ).asObject());

        valCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    setText(String.format("%.2f PLN", item));
                    setTextFill(isDarkMode ? Color.WHITE : Color.BLACK);
                }
            }
        });

        portfolioTable.getColumns().addAll(symCol, qtyCol, valCol);
        VBox box = new VBox(10, new Label("Twój Portfel"), portfolioTable);
        box.setPadding(new Insets(20));
        return box;
    }

    private HBox createStatusBar() {
        cashLabel = new Label();
        totalValueLabel = new Label();
        updateFinanceLabels();
        Button themeBtn = new Button("Motyw ☀/☾");
        themeBtn.setOnAction(e -> toggleTheme());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(20, cashLabel, totalValueLabel, spacer, themeBtn);
        box.setPadding(new Insets(10));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color: #333333;");
        return box;
    }

    // --- LOGIKA TRANSAKCJI (OBSŁUGA UŁAMKÓW I KWOT) ---
    private void handleTransaction(boolean isBuying) {
        if (selectedStock == null) return;
        try {
            // Zastępujemy przecinki kropkami, żeby double zadziałał (np. 0,5 -> 0.5)
            String input = amountField.getText().replace(",", ".");
            double value = Double.parseDouble(input);
            if (value <= 0) return;

            double quantityToTrade = 0;

            if (rbQuantity.isSelected()) {
                // Tryb: Kupuję KONKRETNĄ ILOŚĆ (np. 0.5 sztuki)
                quantityToTrade = value;
            } else {
                // Tryb: Kupuję ZA KWOTĘ (np. za 2000 zł)
                // Ilość = Kwota / Cena
                quantityToTrade = value / selectedStock.getPrice();
            }

            if (isBuying) buyStock(selectedStock, quantityToTrade);
            else sellStock(selectedStock, quantityToTrade);

            amountField.clear();
            updateOwnedLabel();
        } catch (NumberFormatException ex) {
            showAlert("Błąd", "Wpisz poprawną liczbę!");
        }
    }

    private void buyStock(Stock stock, double quantity) {
        double cost = stock.getPrice() * quantity;
        if (cash >= cost) {
            cash -= cost;
            PortfolioItem existing = findPortfolioItem(stock.getSymbol());
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + quantity);
            } else {
                myPortfolioData.add(new PortfolioItem(stock, quantity));
            }
            updateFinanceLabels();
            showAlert("Sukces", String.format("Kupiłeś %.4f szt. %s", quantity, stock.getSymbol()));
        } else {
            showAlert("Brak środków", "Nie masz wystarczająco gotówki!");
        }
    }

    private void sellStock(Stock stock, double quantity) {
        PortfolioItem item = findPortfolioItem(stock.getSymbol());
        // Sprawdzamy z małym marginesem błędu dla double (epsilon)
        if (item != null && item.getQuantity() >= (quantity - 0.0001)) {
            double value = stock.getPrice() * quantity;
            cash += value;
            double newQty = item.getQuantity() - quantity;

            // Jeśli zostało bardzo mało (np. 0.000001), usuwamy pozycję
            if (newQty < 0.0001) myPortfolioData.remove(item);
            else item.setQuantity(newQty);

            updateFinanceLabels();
            showAlert("Sukces", String.format("Sprzedałeś %.4f szt. %s", quantity, stock.getSymbol()));
        } else {
            showAlert("Błąd", "Nie masz tyle akcji!");
        }
    }

    // --- RESZTA LOGIKI ---
    private void handleSetAlerts(TextField minField, TextField maxField) {
        try {
            double min = minField.getText().isEmpty() ? 0 : Double.parseDouble(minField.getText());
            double max = maxField.getText().isEmpty() ? 0 : Double.parseDouble(maxField.getText());
            selectedStock.setAlertMin(min);
            selectedStock.setAlertMax(max);
            updateAlertLabel();
            minField.clear(); maxField.clear();
        } catch (Exception e) { }
    }

    private void updateAlertLabel() {
        if (selectedStock == null) return;
        String msg = "";
        if (selectedStock.getAlertMin() > 0) msg += "Spadek < " + selectedStock.getAlertMin() + "\n";
        if (selectedStock.getAlertMax() > 0) msg += "Wzrost > " + selectedStock.getAlertMax();
        activeAlertLabel.setText(msg.isEmpty() ? "Brak ustawień" : msg);
    }

    private void checkAlerts(Stock s) {
        if (s.getAlertMin() > 0 && s.getPrice() < s.getAlertMin()) {
            String msg = "ALERT! " + s.getSymbol() + " < " + s.getAlertMin();
            Platform.runLater(() -> new Alert(Alert.AlertType.WARNING, msg).show());
            s.setAlertMin(0); Platform.runLater(this::updateAlertLabel);
        }
        if (s.getAlertMax() > 0 && s.getPrice() > s.getAlertMax()) {
            String msg = "SUKCES! " + s.getSymbol() + " > " + s.getAlertMax();
            Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg).show());
            s.setAlertMax(0); Platform.runLater(this::updateAlertLabel);
        }
    }

    private PortfolioItem findPortfolioItem(String symbol) {
        return myPortfolioData.stream().filter(p -> p.getStock().getSymbol().equals(symbol)).findFirst().orElse(null);
    }

    private void updateOwnedLabel() {
        if (selectedStock != null) {
            PortfolioItem item = findPortfolioItem(selectedStock.getSymbol());
            double qty = (item != null) ? item.getQuantity() : 0;
            // Formatujemy do 4 miejsc po przecinku
            ownedLabel.setText(String.format("Posiadasz: %.4f szt.", qty));
        }
    }

    private void updateFinanceLabels() {
        double stockVal = myPortfolioData.stream().mapToDouble(p -> p.getQuantity() * p.getStock().getPrice()).sum();
        cashLabel.setText(String.format("Gotówka: %.2f PLN", cash));
        cashLabel.setStyle("-fx-text-fill: #00ffaa; -fx-font-weight: bold;");
        totalValueLabel.setText(String.format("Wartość Akcji: %.2f PLN", stockVal));
        totalValueLabel.setStyle("-fx-text-fill: #eeeeee;");
    }

    private void startMarketSimulation() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            Platform.runLater(() -> {
                for (Stock s : stockData) {
                    stockService.updateStockPrice(s);
                    updateChart(s);
                    checkAlerts(s);
                }
                if (!myPortfolioData.isEmpty()) {
                    // Wymuszamy odświeżenie tabeli, żeby wartości portfela zmieniały się live
                    portfolioTable.refresh();
                    updateFinanceLabels();
                }
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void updateChart(Stock s) {
        if (!stockSeriesMap.containsKey(s.getSymbol())) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(s.getSymbol());
            stockSeriesMap.put(s.getSymbol(), series);
        }
        var series = stockSeriesMap.get(s.getSymbol());
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        series.getData().add(new XYChart.Data<>(time, s.getPrice()));
        if (series.getData().size() > 30) series.getData().remove(0);

        if (selectedStock != null && s.getSymbol().equals(selectedStock.getSymbol())) {
            if (!lineChart.getData().contains(series)) {
                lineChart.getData().clear();
                lineChart.getData().add(series);
            }
        }
    }

    private void refreshChart() {
        lineChart.getData().clear();
        if (selectedStock != null && stockSeriesMap.containsKey(selectedStock.getSymbol())) {
            lineChart.getData().add(stockSeriesMap.get(selectedStock.getSymbol()));
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }

    // --- ZAKTUALIZOWANA KLASA PORTFOLIO (DOUBLE ZAMIAST INT) ---
    public static class PortfolioItem {
        private final Stock stock;
        // Zmiana z SimpleIntegerProperty na SimpleDoubleProperty
        private final SimpleDoubleProperty quantity;

        public PortfolioItem(Stock stock, double quantity) {
            this.stock = stock;
            this.quantity = new SimpleDoubleProperty(quantity);
        }
        public Stock getStock() { return stock; }

        public double getQuantity() { return quantity.get(); }
        public void setQuantity(double q) { this.quantity.set(q); }
        public SimpleDoubleProperty quantityProperty() { return quantity; }
    }
}