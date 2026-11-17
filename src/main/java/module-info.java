module org.example.desktopver1 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires com.google.gson;
    requires atlantafx.base;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;

    opens org.example.desktopver1 to javafx.fxml;
    opens org.example.desktopver1.controller to javafx.fxml;
    opens org.example.desktopver1.model to javafx.base, com.google.gson;
    opens org.example.desktopver1.network to com.google.gson;

    exports org.example.desktopver1;
    exports org.example.desktopver1.controller;
    exports org.example.desktopver1.model;
    exports org.example.desktopver1.view;
    exports org.example.desktopver1.service;
    exports org.example.desktopver1.database;
    exports org.example.desktopver1.network;
}