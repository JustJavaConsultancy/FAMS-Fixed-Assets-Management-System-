package com.example.fams;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class FamsApplication {

    public static final String APP_TIME_ZONE = "Africa/Lagos";

    public static void main(String[] args) {
        // Pin the JVM default timezone so that LocalDate.now()/LocalDateTime.now()
        // and Jackson's (de)serialization of date/time fields are consistent with the
        // business location. Without this, a UTC-hosted server makes "today" drift by a
        // day, which corrupts depreciation period resolution, run dates, and effective-from
        // defaults.
        TimeZone.setDefault(TimeZone.getTimeZone(APP_TIME_ZONE));

        SpringApplication.run(FamsApplication.class, args);
    }

}
