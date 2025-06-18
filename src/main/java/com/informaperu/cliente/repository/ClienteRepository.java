package com.informaperu.cliente.repository;


import com.informaperu.cliente.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByIdGestion(Integer idGestion);
    Optional<Cliente> findByIdGestionAndFechaTipificacion(Integer idGestion, LocalDateTime fechaTipificacion);
}
