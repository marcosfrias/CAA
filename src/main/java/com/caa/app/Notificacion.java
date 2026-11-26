package com.caa.app;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notificación asociada a un evento: archivo Word adjunto y fecha límite.
 * Se guarda una línea por notificación en {@code notificaciones.txt}.
 */
public final class Notificacion {

    private final String id;
    private String tituloEvento = "";
    private LocalDateTime fechaEvento;
    private String archivo = "";
    private String descripcion = "";
    private LocalDate fechaLimite;
    private LocalDateTime fechaCreacion = LocalDateTime.now();
    private boolean atendida;

    public Notificacion() {
        this(UUID.randomUUID().toString());
    }

    public Notificacion(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getTituloEvento() {
        return tituloEvento;
    }

    public void setTituloEvento(String tituloEvento) {
        this.tituloEvento = tituloEvento == null ? "" : tituloEvento;
    }

    public LocalDateTime getFechaEvento() {
        return fechaEvento;
    }

    public void setFechaEvento(LocalDateTime fechaEvento) {
        this.fechaEvento = fechaEvento;
    }

    public String getArchivo() {
        return archivo;
    }

    public void setArchivo(String archivo) {
        this.archivo = archivo == null ? "" : archivo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion == null ? "" : descripcion;
    }

    public LocalDate getFechaLimite() {
        return fechaLimite;
    }

    public void setFechaLimite(LocalDate fechaLimite) {
        this.fechaLimite = fechaLimite;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public boolean isAtendida() {
        return atendida;
    }

    public void setAtendida(boolean atendida) {
        this.atendida = atendida;
    }

    /** Verdadero cuando la fecha límite ya llegó (o pasó) y no fue atendida. */
    public boolean estaVencida(LocalDate hoy) {
        return !atendida && fechaLimite != null && !fechaLimite.isAfter(hoy);
    }

    public String toLinea() {
        return String.join("\t",
                cod(id),
                cod(tituloEvento),
                cod(fechaEvento == null ? "" : fechaEvento.toString()),
                cod(archivo),
                cod(descripcion),
                cod(fechaLimite == null ? "" : fechaLimite.toString()),
                cod(fechaCreacion == null ? "" : fechaCreacion.toString()),
                atendida ? "1" : "0");
    }

    public static Notificacion desdeLinea(String linea) {
        String[] f = linea.split("\t", -1);
        if (f.length < 8) return null;
        try {
            Notificacion n = new Notificacion(dec(f[0]));
            n.tituloEvento = dec(f[1]);
            String fechaEvento = dec(f[2]);
            if (!fechaEvento.isBlank()) n.fechaEvento = LocalDateTime.parse(fechaEvento);
            n.archivo = dec(f[3]);
            n.descripcion = dec(f[4]);
            String limite = dec(f[5]);
            if (!limite.isBlank()) n.fechaLimite = LocalDate.parse(limite);
            String creacion = dec(f[6]);
            if (!creacion.isBlank()) n.fechaCreacion = LocalDateTime.parse(creacion);
            n.atendida = "1".equals(f[7]);
            return n;
        } catch (RuntimeException lineaDanada) {
            return null;
        }
    }

    private static String cod(String s) {
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String dec(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
