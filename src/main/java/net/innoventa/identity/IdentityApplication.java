package net.innoventa.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IdentityApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(IdentityApplication.class, arguments);
    }

}
