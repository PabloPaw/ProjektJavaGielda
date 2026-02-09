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
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StockApp extends Application {

    private final StockService stockService = new StockService();
    private final ObservableList<Stock> stockData = FXCollections.observableArrayList();
    private final ObservableList<Stock> currencyData = FXCollections.observableArrayList();
    private final ObservableList<PortfolioItem> myPortfolioData = FXCollections.observableArrayList();

    private final Map<String, XYChart.Series<String, Number>> stockSeriesMap = new HashMap<>();

    private LineChart<String, Number> liveChart;
    private Stock selectedStock;

    // UI Elements
    private Label cashLabel;
    private Label totalValueLabel;
    private Label ownedLabel;
    private Label activeAlertLabel;

    private RadioButton rbQuantity;
    private RadioButton rbValue;
    private TextField amountField;

    private TableView<Stock> marketTable;
    private TableView<Stock> currencyTable;
    private TableView<PortfolioItem> portfolioTable;


    private double cash = 10000.00;

    private boolean isDarkMode = true;
    private Scene scene;

    @Override
    public void start(Stage primaryStage) {

        showStartupDialog();


        stockData.addAll(stockService.getStocks());
        currencyData.addAll(stockService.getCurrencies());

        if (!stockData.isEmpty()) {
            selectedStock = stockData.get(0);
        }

        stockService.startMarketMonitor(stockData, currencyData);


        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab marketTab = new Tab("Rynek Live");
        marketTab.setContent(createMarketView());

        Tab portfolioTab = new Tab("Mój Portfel");
        portfolioTab.setContent(createPortfolioView());


        tabPane.getTabs().addAll(marketTab, portfolioTab);

        BorderPane root = new BorderPane();
        root.setCenter(tabPane);
        root.setBottom(createStatusBar());

        startMarketSimulation();

        scene = new Scene(root, 1200, 800);
        applyTheme();

        primaryStage.setTitle("WIG20 Pro Trader");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // --- OKIENKO STARTOWE ---
    private void showStartupDialog() {
        TextInputDialog dialog = new TextInputDialog("50000");
        dialog.setTitle("Konfiguracja Portfela");
        dialog.setHeaderText("Witaj w symulatorze giełdowym!");
        dialog.setContentText("Podaj kwotę startową (PLN):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(amount -> {
            try {
                double value = Double.parseDouble(amount);
                if (value > 0) this.cash = value;
            } catch (NumberFormatException e) { }
        });
    }

    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        applyTheme();
        if (marketTable != null) marketTable.refresh();
        if (currencyTable != null) currencyTable.refresh();
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

        VBox leftPanel = new VBox(10);
        leftPanel.setPrefWidth(350);

        Label stocksLabel = new Label("Notowania Live");
        stocksLabel.setStyle("-fx-font-weight: bold;");
        marketTable = createStockTableView(stockData, true);
        VBox.setVgrow(marketTable, Priority.ALWAYS);

        Label currencyLabel = new Label("Kursy średnie NBP");
        currencyLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #aaa;");
        currencyTable = createStockTableView(currencyData, false);
        currencyTable.setPrefHeight(130);

        leftPanel.getChildren().addAll(stocksLabel, marketTable, new Separator(), currencyLabel, currencyTable);

        pane.setLeft(leftPanel);
        pane.setCenter(createLiveChartSection());
        pane.setRight(createRightPanel());
        return pane;
    }

    private TableView<Stock> createStockTableView(ObservableList<Stock> data, boolean isLiveTable) {
        TableView<Stock> table = new TableView<>();
        table.setItems(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Stock, String> symCol = new TableColumn<>("Symbol");
        symCol.setCellValueFactory(new PropertyValueFactory<>("symbol"));
        symCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) {
                    if (item.equals("BITCOIN")) setStyle("-fx-font-weight: bold; -fx-text-fill: #F7931A;");
                    else { setStyle(""); setTextFill(isDarkMode ? Color.WHITE : Color.BLACK); }
                }
            }
        });

        TableColumn<Stock, Double> priceCol = new TableColumn<>("Kurs");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        priceCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    setText(String.format("%.2f", item));
                    setTextFill(isDarkMode ? Color.WHITE : Color.BLACK);
                } else setText(null);
            }
        });

        table.getColumns().add(symCol);
        table.getColumns().add(priceCol);

        if (isLiveTable) {
            TableColumn<Stock, Double> chgCol = new TableColumn<>("Zmiana");
            chgCol.setCellValueFactory(new PropertyValueFactory<>("change"));
            chgCol.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(Double item, boolean empty) {
                    super.updateItem(item, empty);
                    if (!empty && item != null) {
                        setText(String.format("%.2f%%", item));
                        if (item > 0) setTextFill(Color.web("#00AA00"));
                        else if (item < 0) setTextFill(Color.RED);
                        else setTextFill(isDarkMode ? Color.WHITE : Color.BLACK);
                    } else setText(null);
                }
            });
            table.getColumns().add(chgCol);

            table.getSelectionModel().selectedItemProperty().addListener((o, old, newVal) -> {
                if (newVal != null) {
                    selectedStock = newVal;
                    refreshLiveChart();
                    updateOwnedLabel();
                    updateAlertLabel();
                }
            });
        } else {
            table.setSelectionModel(null);
        }

        return table;
    }

    private VBox createLiveChartSection() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setTickLabelsVisible(true);
        xAxis.setLabel("Czas (Live)");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setAutoRanging(true);
        yAxis.setForceZeroInRange(false);
        liveChart = new LineChart<>(xAxis, yAxis);
        liveChart.setTitle("Wykres");
        liveChart.setAnimated(false);
        liveChart.setCreateSymbols(false);
        liveChart.setLegendVisible(false);
        VBox box = new VBox(liveChart);
        box.setPadding(new Insets(0, 10, 0, 10));
        VBox.setVgrow(liveChart, Priority.ALWAYS);
        return box;
    }

    private VBox createRightPanel() {
        Label tradeTitle = new Label("Panel Handlu");
        tradeTitle.setStyle("-fx-font-weight: bold;");
        ownedLabel = new Label("Posiadasz: 0.0000 szt.");
        ownedLabel.setTextFill(Color.GRAY);

        ToggleGroup group = new ToggleGroup();
        rbQuantity = new RadioButton("Ilość (szt.)");
        rbQuantity.setToggleGroup(group);
        rbQuantity.setSelected(true);
        rbQuantity.setTextFill(Color.LIGHTGRAY);
        rbValue = new RadioButton("Kwota (PLN)");
        rbValue.setToggleGroup(group);
        rbValue.setTextFill(Color.LIGHTGRAY);

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
        mainRight.setPrefWidth(240);
        return mainRight;
    }

    private VBox createPortfolioView() {
        portfolioTable = new TableView<>();
        portfolioTable.setItems(myPortfolioData);
        portfolioTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<PortfolioItem, String> symCol = new TableColumn<>("Symbol");
        symCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStock().getSymbol()));
        TableColumn<PortfolioItem, Double> qtyCol = new TableColumn<>("Ilość");
        qtyCol.setCellValueFactory(cell -> cell.getValue().quantityProperty().asObject());
        qtyCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    setText(String.format("%.4f", item));
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

    // --- LOGIKA ---
    private void handleTransaction(boolean isBuying) {
        if (selectedStock == null) return;
        try {
            String input = amountField.getText().replace(",", ".");
            double value = Double.parseDouble(input);
            if (value <= 0) return;
            double quantityToTrade = 0;
            if (rbQuantity.isSelected()) quantityToTrade = value;
            else quantityToTrade = value / selectedStock.getPrice();

            if (isBuying) buyStock(selectedStock, quantityToTrade);
            else sellStock(selectedStock, quantityToTrade);
            amountField.clear();
            updateOwnedLabel();
        } catch (NumberFormatException ex) { showAlert("Błąd", "Wpisz poprawną liczbę!"); }
    }

    private void buyStock(Stock stock, double quantity) {
        double cost = stock.getPrice() * quantity;
        if (cash >= cost) {
            cash -= cost;
            PortfolioItem existing = findPortfolioItem(stock.getSymbol());
            if (existing != null) existing.setQuantity(existing.getQuantity() + quantity);
            else myPortfolioData.add(new PortfolioItem(stock, quantity));
            updateFinanceLabels();
            showAlert("Sukces", String.format("Kupiłeś %.4f szt. %s", quantity, stock.getSymbol()));
        } else showAlert("Brak środków", "Nie masz wystarczająco gotówki!");
    }

    private void sellStock(Stock stock, double quantity) {
        PortfolioItem item = findPortfolioItem(stock.getSymbol());
        if (item != null && item.getQuantity() >= (quantity - 0.0001)) {
            cash += stock.getPrice() * quantity;
            double newQty = item.getQuantity() - quantity;
            if (newQty < 0.0001) myPortfolioData.remove(item);
            else item.setQuantity(newQty);
            updateFinanceLabels();
            showAlert("Sukces", String.format("Sprzedałeś %.4f szt. %s", quantity, stock.getSymbol()));
        } else showAlert("Błąd", "Nie masz tyle akcji!");
    }

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
                    updateLiveChart(s);
                    checkAlerts(s);
                }
                if (!myPortfolioData.isEmpty()) {
                    portfolioTable.refresh();
                    updateFinanceLabels();
                }
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void updateLiveChart(Stock s) {
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
            if (!liveChart.getData().contains(series)) {
                liveChart.getData().clear();
                liveChart.getData().add(series);
            }
        }
    }

    private void refreshLiveChart() {
        liveChart.getData().clear();
        if (selectedStock != null && stockSeriesMap.containsKey(selectedStock.getSymbol())) {
            liveChart.getData().add(stockSeriesMap.get(selectedStock.getSymbol()));
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

    public static class PortfolioItem {
        private final Stock stock;
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