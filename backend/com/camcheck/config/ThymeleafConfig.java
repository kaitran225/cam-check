package com.camcheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.ViewResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * Thymeleaf configuration to ensure proper template resolution
 */
@Configuration
@Slf4j
public class ThymeleafConfig {

    @Bean
    public SpringResourceTemplateResolver primaryTemplateResolver() {
        SpringResourceTemplateResolver templateResolver = new SpringResourceTemplateResolver();
        templateResolver.setPrefix("classpath:/templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCacheable(false);
        templateResolver.setCheckExistence(true);
        templateResolver.setOrder(1);
        log.info("Configured primary template resolver with prefix: classpath:/templates/");
        return templateResolver;
    }

    @Bean
    public SpringResourceTemplateResolver secondaryTemplateResolver() {
        SpringResourceTemplateResolver templateResolver = new SpringResourceTemplateResolver();
        templateResolver.setPrefix("classpath:/static/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCacheable(false);
        templateResolver.setCheckExistence(true);
        templateResolver.setOrder(2);
        log.info("Configured secondary template resolver with prefix: classpath:/static/");
        return templateResolver;
    }
    
    @Bean
    public ClassLoaderTemplateResolver tertiaryTemplateResolver() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCacheable(false);
        templateResolver.setCheckExistence(true);
        templateResolver.setOrder(3);
        log.info("Configured tertiary template resolver with prefix: templates/");
        return templateResolver;
    }
    
    @Bean
    public ClassLoaderTemplateResolver quaternaryTemplateResolver() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("static/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCacheable(false);
        templateResolver.setCheckExistence(true);
        templateResolver.setOrder(4);
        log.info("Configured quaternary template resolver with prefix: static/");
        return templateResolver;
    }

    @Bean
    public SpringTemplateEngine templateEngine() {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.addTemplateResolver(primaryTemplateResolver());
        templateEngine.addTemplateResolver(secondaryTemplateResolver());
        templateEngine.addTemplateResolver(tertiaryTemplateResolver());
        templateEngine.addTemplateResolver(quaternaryTemplateResolver());
        log.info("Template engine configured with 4 resolvers");
        return templateEngine;
    }

    @Bean
    public ViewResolver viewResolver() {
        ThymeleafViewResolver resolver = new ThymeleafViewResolver();
        resolver.setTemplateEngine(templateEngine());
        resolver.setCharacterEncoding("UTF-8");
        log.info("View resolver configured");
        return resolver;
    }
} 