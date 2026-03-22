package com.francmatyas.uhk_thesis_automatic_kyc_api.amqp;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {

    public static final String X_JOBS = "x.jobs";
    public static final String X_RESULTS = "x.results";
    public static final String X_CONTROL = "x.control";
    public static final String X_DLX = "x.dlx";

    @Bean
    TopicExchange jobs() {
        return new TopicExchange(X_JOBS, true, false);
    }

    @Bean
    TopicExchange results() {
        return new TopicExchange(X_RESULTS, true, false);
    }

    @Bean
    TopicExchange control() {
        return new TopicExchange(X_CONTROL, true, false);
    }

    @Bean
    TopicExchange dlx() {
        return new TopicExchange(X_DLX, true, false);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter conv) {
        var tpl = new RabbitTemplate(cf);
        tpl.setMessageConverter(conv);
        return tpl;
    }

    @Bean
    public Declarables resultBindings(@Value("${api.instanceId:api-1}") String apiId) {
        Queue q = QueueBuilder
                .durable("q.api.results." + apiId)
                .withArgument("x-dead-letter-exchange", X_DLX)
                .build();
        Binding b = BindingBuilder.bind(q).to(results()).with("results.api." + apiId + ".#");
        return new Declarables(q, b);
    }
}
