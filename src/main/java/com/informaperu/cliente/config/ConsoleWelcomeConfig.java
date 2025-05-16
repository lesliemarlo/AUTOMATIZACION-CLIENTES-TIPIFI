package com.informaperu.cliente.config;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración para mostrar un banner de bienvenida en la consola
 * cuando inicia la aplicación
 */
@Configuration
public class ConsoleWelcomeConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsoleWelcomeConfig.class);
    
    @Bean
    public CommandLineRunner welcomeMessage() {
        return args -> {
            logger.info("\n"
                + "╔═══════════════════════════════════════════════════════════════════╗\n"
                + "║                                                                   ║\n"
                + "║             SISTEMA DE SINCRONIZACIÓN DE CLIENTES                 ║\n"
                + "║                         INFORMA PERÚ                              ║\n"
                + "║                                                                   ║\n"
                + "╠═══════════════════════════════════════════════════════════════════╣\n"
                + "║                                                                   ║\n"
                + "║  Endpoint:                                                        ║\n"
                + "║  - GET  /api/cliente - Consultar datos                           ║\n"
                + "║  - POST /api/cliente - Guardar datos                             ║\n"
                + "║  - POST /api/cliente/batch - Iniciar proceso por lotes           ║\n"
                + "║                                                                   ║\n"
                + "║  Para el modo prueba:                                            ║\n"
                + "║  - El scheduler se ejecutará cada 3 minutos                      ║\n"
                + "║  - En producción, cambiar a 1 hora (cron = \"0 0 * * * ?\")      ║\n"
                + "║                                                                   ║\n"
                + "╚═══════════════════════════════════════════════════════════════════╝\n");
        };
    }
}