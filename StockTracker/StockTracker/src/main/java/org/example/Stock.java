package org.example;

import javafx.beans.property.*;

public class Stock {
    private final StringProperty symbol = new SimpleStringProperty();
    private final DoubleProperty price = new SimpleDoubleProperty();
    private final DoubleProperty change = new SimpleDoubleProperty();


    private double alertMin = 0.0;
    private double alertMax = 0.0;

    public Stock(String symbol, double price) {
        this.symbol.set(symbol);
        this.price.set(price);
        this.change.set(0.0);
    }

    public String getSymbol() { return symbol.get(); }
    public StringProperty symbolProperty() { return symbol; }

    public double getPrice() { return price.get(); }
    public DoubleProperty priceProperty() { return price; }
    public void setPrice(double price) { this.price.set(price); }

    public double getChange() { return change.get(); }
    public DoubleProperty changeProperty() { return change; }
    public void setChange(double change) { this.change.set(change); }


    public double getAlertMin() { return alertMin; }
    public void setAlertMin(double alertMin) { this.alertMin = alertMin; }

    public double getAlertMax() { return alertMax; }
    public void setAlertMax(double alertMax) { this.alertMax = alertMax; }

    @Override
    public String toString() {
        return getSymbol();
    }
}