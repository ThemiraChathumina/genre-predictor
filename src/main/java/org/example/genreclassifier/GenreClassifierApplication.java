package org.example.genreclassifier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;

@SpringBootApplication
public class GenreClassifierApplication {

    public static void main(String[] args) throws IOException {
        // Force Spring Boot to listen on port 5000
        SpringApplication app = new SpringApplication(GenreClassifierApplication.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", "5000"));
        app.run(args);

        // Open browser to localhost:5000
        openBrowser("http://localhost:5000");
    }

    public static void openBrowser(String url) {
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                Desktop.getDesktop().browse(new URI(url));
            } else if (System.getProperty("os.name").toLowerCase().contains("win")) {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
