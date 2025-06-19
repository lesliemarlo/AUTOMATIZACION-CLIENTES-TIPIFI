package com.informaperu.cliente.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;

import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/cliente")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class LogController {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(LogController.class);
    
    // Lista thread-safe para almacenar logs
    private static final List<Map<String, Object>> logBuffer = new CopyOnWriteArrayList<>();
    private static final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    
    @PostConstruct
    public void init() {
        // Configurar el appender personalizado para interceptar todos los logs
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // Crear un appender personalizado
        CustomLogAppender customAppender = new CustomLogAppender();
        customAppender.setContext(loggerContext);
        customAppender.start();
        
        // Agregar el appender al logger raíz para capturar todos los logs
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(customAppender);
        
        logger.info("LogController inicializado con interceptor de logs");
    }
    
    // Appender personalizado para interceptar logs
    private static class CustomLogAppender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            // Filtrar solo los logs de las clases que nos interesan
            String loggerName = event.getLoggerName();
            
            // Solo capturar logs de las clases específicas que queremos mostrar
            if (loggerName.contains("com.informaperu.cliente") && 
                !loggerName.contains("LogController")) { // Evitar loops
                
                String level = event.getLevel().toString();
                String message = event.getFormattedMessage();
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                
                Map<String, Object> logEntry = Map.of(
                    "level", level,
                    "message", message,
                    "timestamp", timestamp,
                    "logger", loggerName.substring(loggerName.lastIndexOf('.') + 1) // Solo el nombre de la clase
                );
                
                logBuffer.add(logEntry);
                
                // Mantener solo los últimos 500 logs
                if (logBuffer.size() > 500) {
                    logBuffer.remove(0);
                }
                
                // Enviar a todos los clientes SSE conectados
                broadcastLog(logEntry);
            }
        }
    }
    
    // Método para agregar logs desde cualquier parte (mantener compatibilidad)
    public static void addLog(String level, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        Map<String, Object> logEntry = Map.of(
            "level", level,
            "message", message,
            "timestamp", timestamp,
            "logger", "Manual"
        );
        
        logBuffer.add(logEntry);
        
        // Mantener solo los últimos 500 logs
        if (logBuffer.size() > 500) {
            logBuffer.remove(0);
        }
        
        // Enviar a todos los clientes SSE conectados
        broadcastLog(logEntry);
        
        // También log en consola del servidor
        switch (level.toUpperCase()) {
            case "ERROR": logger.error("[FRONTEND] {}", message); break;
            case "WARN": logger.warn("[FRONTEND] {}", message); break;
            case "INFO": logger.info("[FRONTEND] {}", message); break;
            default: logger.debug("[FRONTEND] {}", message); break;
        }
    }
    
    private static void broadcastLog(Map<String, Object> logEntry) {
        Set<SseEmitter> deadEmitters = new HashSet<>();
        
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .data(logEntry)
                    .name("log"));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }
        
        emitters.removeAll(deadEmitters);
    }

    // Endpoint para obtener logs (fallback si SSE no funciona)
    @GetMapping("/logs")
    public ResponseEntity<?> getLogs() {
        try {
            return ResponseEntity.ok(Map.of(
                "logs", new ArrayList<>(logBuffer),
                "totalLines", logBuffer.size(),
                "timestamp", System.currentTimeMillis(),
                "status", "success"
            ));
        } catch (Exception e) {
            logger.error("Error al obtener logs: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "logs", Collections.emptyList()
            ));
        }
    }

    // Server-Sent Events para tiempo real
    @GetMapping(value = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs() {
        SseEmitter emitter = new SseEmitter(0L); // Sin timeout
        emitters.add(emitter);
        
        logger.debug("Nueva conexión SSE establecida. Total conexiones: {}", emitters.size());
        
        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            logger.debug("Conexión SSE cerrada. Conexiones restantes: {}", emitters.size());
        });
        
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            logger.debug("Conexión SSE timeout");
        });
        
        emitter.onError((ex) -> {
            emitters.remove(emitter);
            logger.debug("Error en conexión SSE: {}", ex.getMessage());
        });
        
        // Enviar logs existentes inmediatamente
        try {
            for (Map<String, Object> log : new ArrayList<>(logBuffer)) {
                emitter.send(SseEmitter.event().data(log).name("log"));
            }
        } catch (IOException e) {
            emitters.remove(emitter);
            logger.error("Error enviando logs iniciales: {}", e.getMessage());
        }
        
        return emitter;
    }

    // Endpoint de prueba para generar logs
    @PostMapping("/test")
    public ResponseEntity<?> testLogs() {
        addLog("INFO", "🧪 Iniciando test desde frontend");
        addLog("INFO", "🔄 Procesando datos de prueba...");
        addLog("WARN", "⚠️ Advertencia de prueba");
        addLog("ERROR", "❌ Error simulado para testing");
        addLog("INFO", "✅ Test completado exitosamente");
        
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Test logs generados",
            "totalLogs", logBuffer.size()
        ));
    }

    // Endpoint para limpiar logs
    @DeleteMapping("/logs")
    public ResponseEntity<?> clearLogs() {
        int previousSize = logBuffer.size();
        logBuffer.clear();
        addLog("INFO", "🗑️ Logs limpiados. Eliminados: " + previousSize + " logs");
        
        return ResponseEntity.ok(Map.of("message", "Logs limpiados", "cleared", previousSize));
    }
}