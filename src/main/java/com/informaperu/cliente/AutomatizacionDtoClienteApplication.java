package com.informaperu.cliente;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

@SpringBootApplication
@EnableScheduling
public class AutomatizacionDtoClienteApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(AutomatizacionDtoClienteApplication.class);
    
    public static void main(String[] args) {
        logger.info("╔═══════════════════════════════════════════════════════════════════╗");
        logger.info("║             INICIANDO APLICACIÓN DE SINCRONIZACIÓN                ║");
        logger.info("╚═══════════════════════════════════════════════════════════════════╝");
        
        ConfigurableApplicationContext context = SpringApplication.run(AutomatizacionDtoClienteApplication.class, args);
        
        logger.info("╔═══════════════════════════════════════════════════════════════════╗");
        logger.info("║                   APLICACIÓN INICIADA CON ÉXITO                   ║");
        logger.info("╠═══════════════════════════════════════════════════════════════════╣");
        logger.info("║ - Acceder a los endpoints REST: /api/cliente                      ║");
        logger.info("║ - Iniciar sincronización: POST /api/cliente/batch                 ║");
        logger.info("║ - Sincronización automática: cada 3 minutos (solo para pruebas)   ║");
        logger.info("╚═══════════════════════════════════════════════════════════════════╝");
    }
    
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager(entityManagerFactory);
        logger.info("✅ Transaction Manager configurado correctamente");
        return transactionManager;
    }
}