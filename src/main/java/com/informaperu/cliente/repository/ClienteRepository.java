package com.informaperu.cliente.repository;

import com.informaperu.cliente.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    // Método para encontrar UN registro por idGestion (si esperas solo uno)
    Optional<Cliente> findByIdGestion(Integer idGestion);
    
    // Método para encontrar MÚLTIPLES registros por idGestion (para verificar duplicados)
    List<Cliente> findAllByIdGestion(Integer idGestion);
    
    // Si necesitas el método original con fecha (comentado por ahora)
    // List<Cliente> findByIdGestionAndFechaTipificacion(Integer idGestion, LocalDateTime fechaTipificacion);
}