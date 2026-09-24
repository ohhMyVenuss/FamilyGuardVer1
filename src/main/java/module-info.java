module org.example.desktopver1 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;

    opens org.example.desktopver1 to javafx.fxml;
    opens org.example.desktopver1.controller to javafx.fxml;
    opens org.example.desktopver1.model to javafx.base;

    exports org.example.desktopver1;
    exports org.example.desktopver1.controller;
    exports org.example.desktopver1.model;
    exports org.example.desktopver1.view;
    exports org.example.desktopver1.service;
    exports org.example.desktopver1.database;
}