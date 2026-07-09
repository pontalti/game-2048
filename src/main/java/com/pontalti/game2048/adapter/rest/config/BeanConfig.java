package com.pontalti.game2048.adapter.rest.config;

import com.pontalti.game2048.adapter.ai.ExpectimaxAdvisor;
import com.pontalti.game2048.domain.port.out.MoveAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;

/**
 * Wires domain collaborators as Spring beans. Declaring them here (rather than
 * annotating the domain classes) keeps the domain free of Spring — the core has
 * no framework imports, which is the whole point of the hexagonal design.
 */
@Configuration
public class BeanConfig {

    /** The AI advisor, exposed behind its port so the service depends only on the abstraction. */
    @Bean
    public MoveAdvisor moveAdvisor() {
        return new ExpectimaxAdvisor();
    }

    /** A shared randomness source for creating new games. */
    @Bean
    public Random random() {
        return new Random();
    }
}
