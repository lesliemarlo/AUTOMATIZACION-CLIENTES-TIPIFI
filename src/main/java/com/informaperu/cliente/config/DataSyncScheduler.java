package com.informaperu.cliente.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.informaperu.cliente.model.ClienteDTO;
import com.informaperu.cliente.service.ClienteService;

@Component
public class DataSyncScheduler {
    private final ClienteService clienteService;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public DataSyncScheduler(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @Scheduled(cron = "0 0 0 * * ?") // Ejecutar cada día a medianoche
    public void syncData() {
        // Definir el rango de fechas (por ejemplo, el último mes)
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusMonths(1);

        int intervalDays = 1;
        int limit = 999999;
        int offset = 1;
        String portfolio = "04";

        LocalDateTime currentStart = startDate;
        while (!currentStart.isAfter(endDate)) {
            LocalDateTime currentEnd = currentStart.plusDays(intervalDays).minusSeconds(1);
            if (currentEnd.isAfter(endDate)) {
                currentEnd = endDate;
            }

            String formattedStart = currentStart.format(DATE_TIME_FORMATTER);
            String formattedEnd = currentEnd.format(DATE_TIME_FORMATTER);

            try {
                List<ClienteDTO> datos = clienteService.obtenerDatosDesdeAPI(limit, offset, portfolio, formattedStart, formattedEnd);
                clienteService.guardarDatosEnBD(datos);
            } catch (Exception e) {
                // Log error but continue with the next interval
                System.err.println("Error procesando intervalo " + formattedStart + " a " + formattedEnd + ": " + e.getMessage());
            }

            currentStart = currentEnd.plusSeconds(1);
        }
    }
}
