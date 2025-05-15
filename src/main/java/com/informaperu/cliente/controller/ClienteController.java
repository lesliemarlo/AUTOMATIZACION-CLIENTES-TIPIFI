package com.informaperu.cliente.controller;

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

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public ResponseEntity<?> obtenerDatos(
            @RequestParam(value = "limit", defaultValue = "99999") int limit,
            @RequestParam(value = "offset", defaultValue = "1") int offset,
            @RequestParam(value = "portfolio", defaultValue = "04") String portfolio,
            @RequestParam(value = "start_date") String startDate,
            @RequestParam(value = "end_date") String endDate) {
        try {
            // Validar y formatear las fechas
            validateDateFormat(startDate);
            validateDateFormat(endDate);
            
            List<ClienteDTO> datos = clienteService.obtenerDatosDesdeAPI(limit, offset, portfolio, startDate, endDate);
            return ResponseEntity.ok(datos);
        } catch (DateTimeParseException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Formato de fecha inválido. Utilice el formato: yyyy-MM-dd HH:mm:ss");
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body("Error al consumir la API: " + e.getResponseBodyAsString());
        } catch (Exception e) {
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
            // Validar y formatear las fechas
            validateDateFormat(startDate);
            validateDateFormat(endDate);
            
            List<ClienteDTO> datos = clienteService.obtenerDatosDesdeAPI(limit, offset, portfolio, startDate, endDate);
            clienteService.guardarDatosEnBD(datos);
            return ResponseEntity.ok("Datos guardados correctamente");
        } catch (DateTimeParseException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Formato de fecha inválido. Utilice el formato: yyyy-MM-dd HH:mm:ss");
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body("Error al consumir la API: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al procesar la solicitud: " + e.getMessage());
        }
    }
    
    private void validateDateFormat(String date) {
        try {
            LocalDateTime.parse(date, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new DateTimeParseException("Formato de fecha inválido. Utilice el formato: yyyy-MM-dd HH:mm:ss", date, e.getErrorIndex());
        }
    }
    
    @PostMapping("/batch")
    public ResponseEntity<?> procesarRangoFechas(
            @RequestParam(value = "start_date") String startDate,
            @RequestParam(value = "end_date") String endDate,
            @RequestParam(value = "interval_days", defaultValue = "1") int intervalDays,
            @RequestParam(value = "limit", defaultValue = "999999") int limit,
            @RequestParam(value = "offset", defaultValue = "1") int offset,
            @RequestParam(value = "portfolio", defaultValue = "04") String portfolio) {
        try {
            // Validar y parsear fechas
            LocalDateTime start = LocalDateTime.parse(startDate, DATE_TIME_FORMATTER);
            LocalDateTime end = LocalDateTime.parse(endDate, DATE_TIME_FORMATTER);

            if (start.isAfter(end)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("start_date debe ser anterior a end_date");
            }

            // Iterar sobre el rango de fechas en intervalos
            LocalDateTime currentStart = start;
            while (!currentStart.isAfter(end)) {
                LocalDateTime currentEnd = currentStart.plusDays(intervalDays).minusSeconds(1);
                if (currentEnd.isAfter(end)) {
                    currentEnd = end;
                }

                String formattedStart = currentStart.format(DATE_TIME_FORMATTER);
                String formattedEnd = currentEnd.format(DATE_TIME_FORMATTER);

                // Llamar al método existente para procesar este intervalo
                List<ClienteDTO> datos = clienteService.obtenerDatosDesdeAPI(limit, offset, portfolio, formattedStart, formattedEnd);
                clienteService.guardarDatosEnBD(datos);

                currentStart = currentEnd.plusSeconds(1);
            }

            return ResponseEntity.ok("Procesamiento de rango de fechas completado");
        } catch (DateTimeParseException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Formato de fecha inválido. Utilice el formato: yyyy-MM-dd HH:mm:ss");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al procesar el rango de fechas: " + e.getMessage());
        }
    }
}