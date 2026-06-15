package com.example.fams.maintenance;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MaintenanceMessagingConfig {

    public static final String EXCHANGE = "fams.maintenance.exchange";
    public static final String DUE_QUEUE = "fams.maintenance.due.queue";
    public static final String DUE_ROUTING_KEY = "maintenance.due";

    @Bean
    Queue maintenanceDueQueue() {
        return new Queue(DUE_QUEUE, true);
    }

    @Bean
    TopicExchange maintenanceExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Binding maintenanceDueBinding(Queue maintenanceDueQueue, TopicExchange maintenanceExchange) {
        return BindingBuilder.bind(maintenanceDueQueue).to(maintenanceExchange).with(DUE_ROUTING_KEY);
    }
}
