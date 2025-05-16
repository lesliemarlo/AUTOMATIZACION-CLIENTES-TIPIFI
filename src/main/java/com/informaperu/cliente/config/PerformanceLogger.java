package com.informaperu.cliente.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clase de utilidad para registrar tiempos de ejecución
 * y mostrar métricas de rendimiento
 */
public class PerformanceLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceLogger.class);
    private final String operationName;
    private final long startTime;
    
    /**
     * Constructor que inicia el contador de tiempo
     * @param operationName Nombre de la operación a medir
     */
    public PerformanceLogger(String operationName) {
        this.operationName = operationName;
        this.startTime = System.currentTimeMillis();
        logger.info("⏱️  Inicio de operación: {}", operationName);
    }
    
    /**
     * Registra el fin de la operación y calcula el tiempo transcurrido
     * @param extraInfo Información adicional opcional
     * @return Tiempo transcurrido en milisegundos
     */
    public long end(String extraInfo) {
        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;
        
        String formattedTime;
        if (elapsedTime < 1000) {
            formattedTime = elapsedTime + " ms";
        } else {
            formattedTime = String.format("%.2f seg", elapsedTime / 1000.0);
        }
        
        String logMessage = String.format("⏱️  Fin de operación: %s - Tiempo: %s", operationName, formattedTime);
        if (extraInfo != null && !extraInfo.isEmpty()) {
            logMessage += " - " + extraInfo;
        }
        
        logger.info(logMessage);
        return elapsedTime;
    }
    
    /**
     * Registra el fin de la operación sin información adicional
     * @return Tiempo transcurrido en milisegundos
     */
    public long end() {
        return end(null);
    }
}