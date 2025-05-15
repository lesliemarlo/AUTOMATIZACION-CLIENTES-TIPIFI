package com.informaperu.cliente.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tipificaciones_por_cliente", schema = "datos")
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String empresa;
    private String campana;
    private String base;
    private String estrategia;
    @Column(name = "tipo_canal")
    private String tipoCanal;
    @Column(name = "tipo_marcacion")
    private String tipoMarcacion;
    @Column(name = "codigo_contacto")
    private String codigoContacto;
    private String contacto;
    @Column(name = "fecha_de_creacion_del_contacto")
    private LocalDateTime fechaCreacionContacto;
    @Column(name = "fecha_ultima_de_actualizacion_del_contacto")
    private LocalDateTime fechaUltimaActualizacionContacto;
    @Column(name = "codigo_producto")
    private String codigoProducto;
    private String telefono;
    private String grupo;
    @Column(name = "codigo_de_grupo")
    private String codigoGrupo;
    private String resultado;
    @Column(name = "codigo_de_resultado")
    private String codigoResultado;
    private String motivo;
    @Column(name = "codigo_de_motivo")
    private String codigoMotivo;
    private String submotivo;
    @Column(name = "codigo_de_submotivo")
    private String codigoSubmotivo;
    @Column(name = "comentario_de_tipificacion")
    private String comentarioTipificacion;
    @Column(name = "resultado_atk")
    private String resultadoAtk;
    @Column(name = "zip_code")
    private String zipCode;
    @Column(name = "fecha_de_asignacion_de_ticket")
    private LocalDateTime fechaAsignacionTicket;
    @Column(name = "fecha_de_tipificacion")
    private LocalDateTime fechaTipificacion;
    @Column(name = "fecha_de_resolucion_de_ticket")
    private LocalDateTime fechaResolucionTicket;
    @Column(name = "fecha_pdp")
    private String fechaPdp;
    @Column(name = "monto_pdp")
    private Double montoPdp;
    @Column(name = "fecha_agenda")
    private String fechaAgenda;
    @Column(name = "id_usuario")
    private Integer idUsuario;
    @Column(name = "usuario_gestion")
    private String usuarioGestion;
    @Column(name = "anexo_usuario")
    private String anexoUsuario;
    @Column(name = "id_gestion")
    private Integer idGestion;
    @Column(name = "id_llamada")
    private String idLlamada;
    @Column(name = "id_ticket")
    private Integer idTicket;
    @Column(name = "fecha_sincronizacion")
    private LocalDateTime fechaSincronizacion;

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }
    public String getCampana() { return campana; }
    public void setCampana(String campana) { this.campana = campana; }
    public String getBase() { return base; }
    public void setBase(String base) { this.base = base; }
    public String getEstrategia() { return estrategia; }
    public void setEstrategia(String estrategia) { this.estrategia = estrategia; }
    public String getTipoCanal() { return tipoCanal; }
    public void setTipoCanal(String tipoCanal) { this.tipoCanal = tipoCanal; }
    public String getTipoMarcacion() { return tipoMarcacion; }
    public void setTipoMarcacion(String tipoMarcacion) { this.tipoMarcacion = tipoMarcacion; }
    public String getCodigoContacto() { return codigoContacto; }
    public void setCodigoContacto(String codigoContacto) { this.codigoContacto = codigoContacto; }
    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = contacto; }
    public LocalDateTime getFechaCreacionContacto() { return fechaCreacionContacto; }
    public void setFechaCreacionContacto(LocalDateTime fechaCreacionContacto) { this.fechaCreacionContacto = fechaCreacionContacto; }
    public LocalDateTime getFechaUltimaActualizacionContacto() { return fechaUltimaActualizacionContacto; }
    public void setFechaUltimaActualizacionContacto(LocalDateTime fechaUltimaActualizacionContacto) { this.fechaUltimaActualizacionContacto = fechaUltimaActualizacionContacto; }
    public String getCodigoProducto() { return codigoProducto; }
    public void setCodigoProducto(String codigoProducto) { this.codigoProducto = codigoProducto; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getGrupo() { return grupo; }
    public void setGrupo(String grupo) { this.grupo = grupo; }
    public String getCodigoGrupo() { return codigoGrupo; }
    public void setCodigoGrupo(String codigoGrupo) { this.codigoGrupo = codigoGrupo; }
    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }
    public String getCodigoResultado() { return codigoResultado; }
    public void setCodigoResultado(String codigoResultado) { this.codigoResultado = codigoResultado; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getCodigoMotivo() { return codigoMotivo; }
    public void setCodigoMotivo(String codigoMotivo) { this.codigoMotivo = codigoMotivo; }
    public String getSubmotivo() { return submotivo; }
    public void setSubmotivo(String submotivo) { this.submotivo = submotivo; }
    public String getCodigoSubmotivo() { return codigoSubmotivo; }
    public void setCodigoSubmotivo(String codigoSubmotivo) { this.codigoSubmotivo = codigoSubmotivo; }
    public String getComentarioTipificacion() { return comentarioTipificacion; }
    public void setComentarioTipificacion(String comentarioTipificacion) { this.comentarioTipificacion = comentarioTipificacion; }
    public String getResultadoAtk() { return resultadoAtk; }
    public void setResultadoAtk(String resultadoAtk) { this.resultadoAtk = resultadoAtk; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    public LocalDateTime getFechaAsignacionTicket() { return fechaAsignacionTicket; }
    public void setFechaAsignacionTicket(LocalDateTime fechaAsignacionTicket) { this.fechaAsignacionTicket = fechaAsignacionTicket; }
    public LocalDateTime getFechaTipificacion() { return fechaTipificacion; }
    public void setFechaTipificacion(LocalDateTime fechaTipificacion) { this.fechaTipificacion = fechaTipificacion; }
    public LocalDateTime getFechaResolucionTicket() { return fechaResolucionTicket; }
    public void setFechaResolucionTicket(LocalDateTime fechaResolucionTicket) { this.fechaResolucionTicket = fechaResolucionTicket; }
    public String getFechaPdp() { return fechaPdp; }
    public void setFechaPdp(String fechaPdp) { this.fechaPdp = fechaPdp; }
    public Double getMontoPdp() { return montoPdp; }
    public void setMontoPdp(Double montoPdp) { this.montoPdp = montoPdp; }
    public String getFechaAgenda() { return fechaAgenda; }
    public void setFechaAgenda(String fechaAgenda) { this.fechaAgenda = fechaAgenda; }
    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }
    public String getUsuarioGestion() { return usuarioGestion; }
    public void setUsuarioGestion(String usuarioGestion) { this.usuarioGestion = usuarioGestion; }
    public String getAnexoUsuario() { return anexoUsuario; }
    public void setAnexoUsuario(String anexoUsuario) { this.anexoUsuario = anexoUsuario; }
    public Integer getIdGestion() { return idGestion; }
    public void setIdGestion(Integer idGestion) { this.idGestion = idGestion; }
    public String getIdLlamada() { return idLlamada; }
    public void setIdLlamada(String idLlamada) { this.idLlamada = idLlamada; }
    public Integer getIdTicket() { return idTicket; }
    public void setIdTicket(Integer idTicket) { this.idTicket = idTicket; }
    public LocalDateTime getFechaSincronizacion() { return fechaSincronizacion; }
    public void setFechaSincronizacion(LocalDateTime fechaSincronizacion) { this.fechaSincronizacion = fechaSincronizacion; }
}
