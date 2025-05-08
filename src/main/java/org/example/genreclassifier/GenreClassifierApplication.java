package org.example.genreclassifier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
//@SpringBootApplication(exclude = {org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration.class})

@SpringBootApplication
public class GenreClassifierApplication implements ApplicationListener<WebServerInitializedEvent> {

    private static int port;

    public static void main(String[] args) throws IOException {
//        TrainModel.Train();
        SpringApplication.run(GenreClassifierApplication.class, args);
        openBrowser("http://localhost:" + port);
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        port = event.getWebServer().getPort();
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
