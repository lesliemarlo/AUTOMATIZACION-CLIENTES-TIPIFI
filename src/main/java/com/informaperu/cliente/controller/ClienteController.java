package com.informaperu.cliente.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import com.informaperu.cliente.model.ClienteDTO;
import com.informaperu.cliente.service.ClienteService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/cliente")
public class ClienteController {

    private static final Logger logger = LoggerFactory.getLogger(ClienteController.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
        logger.info("✅ ClienteController inicializado correctamente");
    }

    @GetMapping
    public ResponseEntity<?> obtenerDatos(
            @RequestParam(value = "limit", defaultValue = "99999") int limit,
            @RequestParam(value = "offset", defaultValue = "1") int offset,
            @RequestParam(value = "portfolio", defaultValue = "04") String portfolio,
            @RequestParam(value = "start_date") String startDate,
            @RequestParam(value = "end_date") String endDate) {
        try {
            logger.info("╔═══════════════════════════════════════════════════════════════════╗");
            logger.info("║                      SOLICITUD DE DATOS                           ║");
            logger.info("╠═══════════════════════════════════════════════════════════════════╣");
            logger.info("║ Portfolio: {}", String.format("%-52s ║", portfolio));
            logger.info("║ Fecha inicio: {}", String.format("%-49s ║", startDate));
            logger.info("║ Fecha fin: {}", String.format("%-51s ║", endDate));
            logger.info("║ Límite: {}", String.format("%-53s ║", limit));
            logger.info("║ Offset: {}", String.format("%-53s ║", offset));
            logger.info("╚═══════════════════════════════════════════════════════════════════╝");
            
            // Validar y formatear las fechas
            validateDateFormat(startDate);
            validateDateFormat(endDate);
            
            logger.info("🔄 Obteniendo datos desde la API...");
            List<ClienteDTO> datos = clienteService.obtenerDatosDesdeAPI(limit, offset, portfolio, startDate, endDate);
            logger.info("✅ Datos obtenidos correctamente: {} registros", datos.size());
            
            return ResponseEntity.ok(datos);
        } catch (DateTimeParseException e) {
            logger.error("❌ Error en formato de fecha: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Formato de fecha inválido. Utilice el formato: yyyy-MM-dd HH:mm:ss");
        } catch (HttpClientErrorException e) {
            logger.error("❌ Error en API: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                    .body("Error al consumir la API: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            logger.error("❌ Error inesperado: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al procesar la solicitud: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> guardarDatos(
            @RequestParam(value = "limit", defaultValue = "1000") int limit,
            @RequestParam(value = "offset", defaultValue = "1") int offset,
            @RequestParam(value = "portfolio", defaultValue = "07") String portfolio,
            @RequestParam(value = "start_date") String startDate,
            @RequestParam(value = "end_date") String endDate) {
        try {
            logger.info("╔═══════════════════════════════════════════════════════════════════╗");
            logger.info("║                  SOLICITUD DE GUARDAR DATOS                       ║");
            logger.info("╠═══════════════════════════════════════════════════════════════════╣");
            logger.info("║ Portfolio: {}", String.format("%-52s ║", portfolio));
            logger.info("║ Fecha inicio: {}", String.format("%-49s ║", startDate));
            logger.info("║ Fecha fin: {}", String.format("%-51s ║", endDate));
            logger.info("║ Límite: {}", String.format("%-53s ║", limit));
            logger.info("║ Offset: {}", String.format("%-53s ║", offset));
            logger.info("╚═══════════════════════════════════════════════════════════════════╝");
            
            // Validar y formatear las fechas
            validateDateFormat(startDate);
            validateDateFormat(endDate);
            
            logger.info("🔄 Obteniendo datos desde la API...");
            List<ClienteDTO> datos = clienteService.obtenerDatosDesdeAPI(limit, offset, portfolio, startDate, endDate);
            logger.info("✅ Datos obtenidos correctamente: {} registros", datos.size());
            
            logger.info("💾 Guardando datos en la base de datos...");
            clienteService.guardarDatosEnBD(datos);
            logger.info("✅ Datos guardados correctamente en la base de datos");
            
            return ResponseEntity.ok("✅ Datos guardados correctamente: " + datos.size() + " registros");
        } catch (DateTimeParseException e) {
            logger.error("❌ Error en formato de fecha: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Formato de fecha inválido. Utilice el formato: yyyy-MM-dd HH:mm:ss");
        } catch (HttpClientErrorException e) {
            logger.error("❌ Error en API: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                    .body("Error al consumir la API: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            logger.error("❌ Error inesperado: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al procesar la solicitud: " + e.getMessage());
        }
    }
    
    @PostMapping("/batch")
    public ResponseEntity<?> procesarRangoFechas(
            @RequestParam(value = "start_date") String startDate,
            @RequestParam(value = "end_date") String endDate,
            @RequestParam(value = "interval_days", defaultValue = "14") int intervalDays,
            @RequestParam(value = "limit", defaultValue = "999999") int limit,
            @RequestParam(value = "offset", defaultValue = "1") int offset,
            @RequestParam(value = "portfolio", defaultValue = "04") String portfolio) {
        try {
            logger.info("╔═══════════════════════════════════════════════════════════════════╗");
            logger.info("║            SOLICITUD DE PROCESAMIENTO POR LOTES (BATCH)           ║");
            logger.info("╠═══════════════════════════════════════════════════════════════════╣");
            logger.info("║ Portfolio: {}", String.format("%-52s ║", portfolio));
            logger.info("║ Fecha inicio: {}", String.format("%-49s ║", startDate));
            logger.info("║ Fecha fin: {}", String.format("%-51s ║", endDate));
            logger.info("║ Intervalo (días): {}", String.format("%-46s ║", intervalDays));
            logger.info("║ Límite: {}", String.format("%-53s ║", limit));
            logger.info("║ Offset: {}", String.format("%-53s ║", offset));
            logger.info("╚═══════════════════════════════════════════════════════════════════╝");
            
            // Validar y parsear fechas
            LocalDateTime start = LocalDateTime.parse(startDate, DATE_TIME_FORMATTER);
            LocalDateTime end = LocalDateTime.parse(endDate, DATE_TIME_FORMATTER);

            if (start.isAfter(end)) {
                logger.error("❌ Error: La fecha de inicio es posterior a la fecha fin");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("start_date debe ser anterior a end_date");
            }

            logger.info("🔄 Configurando parámetros del batch...");
            // Guardar parámetros para el scheduler
            clienteService.setBatchParameters(startDate, endDate, intervalDays, limit, offset, portfolio);

            logger.info("🚀 Iniciando procesamiento de rango de fechas...");
            // Iniciar el procesamiento inmediato
            clienteService.procesarBatchConReintentos();

            return ResponseEntity.ok("✅ Procesamiento de rango de fechas iniciado correctamente. " +
                    "Se ejecutará cada 3 minutos para pruebas (solo para modo prueba, en producción será cada hora).");
                    
        } catch (DateTimeParseException e) {
            logger.error("❌ Error en formato de fecha: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Formato de fecha inválido. Utilice el formato: yyyy-MM-dd HH:mm:ss");
        } catch (Exception e) {
            logger.error("❌ Error inesperado: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al iniciar el procesamiento: " + e.getMessage());
        }
    }

    private void validateDateFormat(String date) {
        try {
            LocalDateTime.parse(date, DATE_TIME_FORMATTER);
            logger.debug("✅ Formato de fecha validado correctamente: {}", date);
        } catch (DateTimeParseException e) {
            logger.error("❌ Error validando formato de fecha: {}", date);
            throw new DateTimeParseException("Formato de fecha inválido. Utilice el formato: yyyy-MM-dd HH:mm:ss", date, e.getErrorIndex());
        }
    }
}