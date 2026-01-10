module org.example {
    // Te trzy są do interfejsu (JavaFX)
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    // TO JEST KLUCZOWE do pobierania danych z Internetu (NBP/Binance)
    requires java.net.http;

    // Pozwala JavaFX wchodzić do twojego kodu
    opens org.example to javafx.fxml;
    exports org.example;
}