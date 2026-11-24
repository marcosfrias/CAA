package com.eia.documentos;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Preferencias de tamaño, distribución y orden para las imágenes exportadas. */
final class DisposicionImagenes {

    enum Orden {
        AGREGADO("Orden de agregado"),
        NOMBRE_ASCENDENTE("Nombre: A a Z"),
        NOMBRE_DESCENDENTE("Nombre: Z a A");

        private final String etiqueta;

        Orden(String etiqueta) { this.etiqueta = etiqueta; }

        @Override public String toString() { return etiqueta; }
    }

    static final DisposicionImagenes PREDETERMINADA = new DisposicionImagenes(6, 2, Orden.AGREGADO);

    private final int tamanoCentimetros;
    private final int columnas;
    private final Orden orden;

    DisposicionImagenes(int tamanoCentimetros, int columnas, Orden orden) {
        this.tamanoCentimetros = tamanoCentimetros;
        this.columnas = columnas;
        this.orden = orden;
    }

    int getTamanoCentimetros() { return tamanoCentimetros; }
    int getColumnas() { return columnas; }
    Orden getOrden() { return orden; }

    List<File> ordenar(List<File> imagenes) {
        List<File> resultado = new ArrayList<>(imagenes);
        if (orden == Orden.NOMBRE_ASCENDENTE) {
            resultado.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        } else if (orden == Orden.NOMBRE_DESCENDENTE) {
            resultado.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER).reversed());
        }
        return resultado;
    }
}
