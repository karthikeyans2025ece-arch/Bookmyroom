package com.example.bookmyroom.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookMyRoomOpenApi() {
        return new OpenAPI().info(new Info()
                .title("BookMyRoom API")
                .version("1.0.0")
                .description("Guest house room booking system - rooms, guests, bookings and occupancy reports"));
    }
}
