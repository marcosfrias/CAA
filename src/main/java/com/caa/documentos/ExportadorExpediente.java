package com.caa.documentos;

import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;
import java.awt.Graphics2D;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.DeflaterOutputStream;

/** Escribe los formatos de archivo disponibles para un expediente. */
final class ExportadorExpediente {

    private static final Charset PDF_CHARSET = Charset.forName("windows-1252");

    private ExportadorExpediente() {
    }

    static void exportar(String nombre, String contenido, Path carpeta, List<String> formatos, List<File> imagenes,
                         DisposicionImagenes disposicion, File firma, double anchoFirmaCm)
            throws IOException {
        String base = nombre.replaceAll("[\\\\/:*?\"<>|]", "_");
        for (String formato : formatos) {
            switch (formato) {
                case "word":
                    escribirWord(carpeta.resolve(base + ".docx"), contenido, imagenes, disposicion, firma, anchoFirmaCm);
                    break;
                case "pdf":
                    escribirPdf(carpeta.resolve(base + ".pdf"), contenido, imagenes, disposicion, firma, anchoFirmaCm);
                    break;
                case "excel":
                    {
                        Path excel = carpeta.resolve(base + ".xlsx");
                        escribirExcel(excel, contenido, imagenes, disposicion, firma, anchoFirmaCm);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Formato de exportación desconocido: " + formato);
            }
        }
    }

    private static void escribirWord(Path archivo, String contenido, List<File> imagenes,
                                     DisposicionImagenes disposicion, File firma, double anchoFirmaCm) throws IOException {
        StringBuilder parrafos = new StringBuilder();
        for (String linea : contenido.split("\\R", -1)) {
            parrafos.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                    .append(xml(linea)).append("</w:t></w:r></w:p>");
        }
        List<byte[]> imagenesPng = new ArrayList<>();
        List<String> celdasImagenes = new ArrayList<>();
        StringBuilder relaciones = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < imagenes.size(); i++) {
            File archivoImagen = imagenes.get(i);
            if (!archivoImagen.isFile()) continue;
            BufferedImage original = leerImagenReducida(archivoImagen, 900, 1200);
            if (original == null) continue;
            original = recortarAlCuadrado(original);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(original, "png", png);
            imagenesPng.add(png.toByteArray());
            double margenCeldaCm = 0.2;
            double cajaCm = Math.min(disposicion.getTamanoCentimetros(),
                    (18.0 - margenCeldaCm * 2 * disposicion.getColumnas()) / disposicion.getColumnas());
            double ladoEmu = cajaCm * 360_000.0;
            long anchoEmu = Math.round(ladoEmu);
            long altoEmu = Math.round(ladoEmu);
            int rel = imagenesPng.size();
            relaciones.append("<Relationship Id=\"rId").append(rel)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image")
                    .append(rel).append(".png\"/>");
            StringBuilder celda = new StringBuilder("<w:tc><w:tcPr><w:tcW w:w=\"")
                    .append(Math.round((cajaCm + margenCeldaCm * 2) * 567))
                    .append("\" w:type=\"dxa\"/><w:tcMar><w:top w:w=\"113\" w:type=\"dxa\"/><w:start w:w=\"113\" w:type=\"dxa\"/><w:bottom w:w=\"113\" w:type=\"dxa\"/><w:end w:w=\"113\" w:type=\"dxa\"/></w:tcMar></w:tcPr><w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">")
                    .append("<wp:extent cx=\"").append(anchoEmu).append("\" cy=\"").append(altoEmu).append("\"/>")
                    .append("<wp:docPr id=\"").append(rel).append("\" name=\"")
                    .append(xml(archivoImagen.getName())).append("\"/><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
                    .append("<pic:pic><pic:nvPicPr><pic:cNvPr id=\"0\" name=\"").append(xml(archivoImagen.getName()))
                    .append("\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"rId").append(rel)
                    .append("\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/>")
                    .append("<a:ext cx=\"").append(anchoEmu).append("\" cy=\"").append(altoEmu)
                    .append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>")
                    .append("</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>")
                    .append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr><w:sz w:val=\"18\"/></w:rPr><w:t>Imagen ")
                    .append(rel).append("</w:t></w:r></w:p></w:tc>");
            celdasImagenes.add(celda.toString());
        }
        if (!celdasImagenes.isEmpty()) {
            StringBuilder tabla = new StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/>")
                    .append("<w:tblLayout w:type=\"fixed\"/><w:jc w:val=\"center\"/></w:tblPr><w:tblGrid>");
            double cajaCm = Math.min(disposicion.getTamanoCentimetros(),
                    (18.0 - 0.4 * disposicion.getColumnas()) / disposicion.getColumnas());
            for (int columna = 0; columna < disposicion.getColumnas(); columna++) {
                tabla.append("<w:gridCol w:w=\"").append(Math.round((cajaCm + 0.4) * 567)).append("\"/>");
            }
            tabla.append("</w:tblGrid>");
            for (int inicio = 0; inicio < celdasImagenes.size(); inicio += disposicion.getColumnas()) {
                tabla.append("<w:tr>");
                int fin = Math.min(inicio + disposicion.getColumnas(), celdasImagenes.size());
                for (int i = inicio; i < fin; i++) tabla.append(celdasImagenes.get(i));
                for (int i = fin - inicio; i < disposicion.getColumnas(); i++) {
                    tabla.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(Math.round((cajaCm + 0.4) * 567))
                            .append("\" w:type=\"dxa\"/><w:tcMar><w:top w:w=\"113\" w:type=\"dxa\"/><w:start w:w=\"113\" w:type=\"dxa\"/><w:bottom w:w=\"113\" w:type=\"dxa\"/><w:end w:w=\"113\" w:type=\"dxa\"/></w:tcMar></w:tcPr><w:p/></w:tc>");
                }
                tabla.append("</w:tr>");
            }
            parrafos.append(tabla).append("</w:tbl>");
        }
        if (firma != null && firma.isFile()) {
            BufferedImage original = leerImagenReducida(firma, 900, 900);
            if (original != null) {
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(original, "png", png);
                imagenesPng.add(png.toByteArray());
                int rel = imagenesPng.size();
                double anchoCm = Math.max(3.0, Math.min(12.0, anchoFirmaCm));
                double altoCm = anchoCm * original.getHeight() / (double) original.getWidth();
                if (altoCm > 6.0) {
                    anchoCm *= 6.0 / altoCm;
                    altoCm = 6.0;
                }
                long anchoEmu = Math.round(anchoCm * 360_000.0);
                long altoEmu = Math.round(altoCm * 360_000.0);
                relaciones.append("<Relationship Id=\"rId").append(rel)
                        .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image")
                        .append(rel).append(".png\"/>");
                parrafos.append("<w:p><w:pPr><w:jc w:val=\"right\"/><w:spacing w:before=\"240\"/></w:pPr>")
                        .append("<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">")
                        .append("<wp:extent cx=\"").append(anchoEmu).append("\" cy=\"").append(altoEmu).append("\"/>")
                        .append("<wp:docPr id=\"").append(rel).append("\" name=\"Firma\"/>")
                        .append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
                        .append("<pic:pic><pic:nvPicPr><pic:cNvPr id=\"0\" name=\"Firma\"/><pic:cNvPicPr/></pic:nvPicPr>")
                        .append("<pic:blipFill><a:blip r:embed=\"rId").append(rel)
                        .append("\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>")
                        .append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"")
                        .append(anchoEmu).append("\" cy=\"").append(altoEmu)
                        .append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>")
                        .append("</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>");
            }
        }
        relaciones.append("</Relationships>");
        String documento = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" "
                + "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" "
                + "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" "
                + "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:body>"
                + parrafos + "<w:sectPr/></w:body></w:document>";
        try (ZipOutputStream docx = new ZipOutputStream(new FileOutputStream(archivo.toFile()))) {
            zip(docx, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
                    + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                    + "</Types>");
            zip(docx, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                    + "</Relationships>");
            zip(docx, "word/document.xml", documento);
            zip(docx, "word/_rels/document.xml.rels", relaciones.toString());
            for (int i = 0; i < imagenesPng.size(); i++) {
                docx.putNextEntry(new ZipEntry("word/media/image" + (i + 1) + ".png"));
                docx.write(imagenesPng.get(i));
                docx.closeEntry();
            }
        }
    }

    private static void escribirExcel(Path archivo, String contenido, List<File> imagenes,
                                      DisposicionImagenes disposicion, File firma, double anchoFirmaCm) throws IOException {
        String[] lineas = contenido.split("\\R", -1);
        StringBuilder filas = new StringBuilder();
        for (int i = 0; i < lineas.length; i++) {
            filas.append("<row r=\"").append(i + 1).append("\"><c r=\"A")
                    .append(i + 1).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(xml(lineas[i])).append("</t></is></c></row>");
        }
        int altoEnFilas = (int) Math.ceil(disposicion.getTamanoCentimetros() * 28.35 / 15.0);
        int altoFilaImagen = altoEnFilas + 2;
        for (int inicio = 0; inicio < imagenes.size(); inicio += disposicion.getColumnas()) {
            int fila = lineas.length + 1 + (inicio / disposicion.getColumnas()) * altoFilaImagen + altoEnFilas;
            filas.append("<row r=\"").append(fila).append("\">");
            int fin = Math.min(inicio + disposicion.getColumnas(), imagenes.size());
            for (int i = inicio; i < fin; i++) {
                char columna = (char) ('A' + i - inicio);
                filas.append("<c r=\"").append(columna).append(fila)
                        .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Imagen ")
                        .append(i + 1).append("</t></is></c>");
            }
            filas.append("</row>");
        }
        List<byte[]> imagenesPng = new ArrayList<>();
        StringBuilder anclas = new StringBuilder();
        StringBuilder relacionesImagen = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int indiceImagen = 0; indiceImagen < imagenes.size(); indiceImagen++) {
            File imagen = imagenes.get(indiceImagen);
            if (!imagen.isFile()) continue;
            BufferedImage original = leerImagenReducida(imagen, 600, 600);
            if (original == null) continue;
            original = recortarAlCuadrado(original);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(original, "png", png);
            imagenesPng.add(png.toByteArray());
            int id = imagenesPng.size();
            double cajaCm = Math.min(disposicion.getTamanoCentimetros(), 18.0 / disposicion.getColumnas());
            double ladoEmu = cajaCm * 360_000.0;
            long anchoEmu = Math.round(ladoEmu);
            long altoEmu = Math.round(ladoEmu);
            int fila = lineas.length + (indiceImagen / disposicion.getColumnas()) * altoFilaImagen;
            int columna = indiceImagen % disposicion.getColumnas();
            anclas.append("<xdr:oneCellAnchor><xdr:from><xdr:col>").append(columna)
                    .append("</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>")
                    .append(fila).append("</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from><xdr:ext cx=\"")
                    .append(anchoEmu).append("\" cy=\"").append(altoEmu)
                    .append("\"/><xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"").append(id)
                    .append("\" name=\"").append(xml(imagen.getName())).append("\"/><xdr:cNvPicPr/></xdr:nvPicPr>")
                    .append("<xdr:blipFill><a:blip r:embed=\"rId").append(id)
                    .append("\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill><xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/>")
                    .append("<a:ext cx=\"").append(anchoEmu).append("\" cy=\"").append(altoEmu)
                    .append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor>");
            relacionesImagen.append("<Relationship Id=\"rId").append(id)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image")
                    .append(id).append(".png\"/>");
        }
        if (firma != null && firma.isFile()) {
            BufferedImage original = leerImagenReducida(firma, 900, 900);
            if (original != null) {
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(original, "png", png);
                imagenesPng.add(png.toByteArray());
                int id = imagenesPng.size();
                double anchoCm = Math.max(3.0, Math.min(12.0, anchoFirmaCm));
                long anchoEmu = Math.round(anchoCm * 360_000.0);
                long altoEmu = Math.round(anchoEmu * original.getHeight() / (double) original.getWidth());
                int filaFirma = lineas.length
                        + (int) Math.ceil(imagenes.size() / (double) disposicion.getColumnas()) * altoFilaImagen + 2;
                int columnaFirma = disposicion.getColumnas() - 1;
                filas.append("<row r=\"").append(filaFirma).append("\"><c r=\"")
                        .append((char) ('A' + columnaFirma)).append(filaFirma)
                        .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Firma</t></is></c></row>");
                anclas.append("<xdr:oneCellAnchor><xdr:from><xdr:col>").append(columnaFirma)
                        .append("</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>")
                        .append(filaFirma).append("</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from><xdr:ext cx=\"")
                        .append(anchoEmu).append("\" cy=\"").append(altoEmu)
                        .append("\"/><xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"").append(id)
                        .append("\" name=\"Firma\"/><xdr:cNvPicPr/></xdr:nvPicPr>")
                        .append("<xdr:blipFill><a:blip r:embed=\"rId").append(id)
                        .append("\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill><xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/>")
                        .append("<a:ext cx=\"").append(anchoEmu).append("\" cy=\"").append(altoEmu)
                        .append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor>");
                relacionesImagen.append("<Relationship Id=\"rId").append(id)
                        .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image")
                        .append(id).append(".png\"/>");
            }
        }
        relacionesImagen.append("</Relationships>");
        String hoja = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheetData>"
                + filas + "</sheetData>"
                + (imagenesPng.isEmpty() ? "" : "<drawing r:id=\"rId1\"/>") + "</worksheet>";
        try (ZipOutputStream xlsx = new ZipOutputStream(new FileOutputStream(archivo.toFile()))) {
            zip(xlsx, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + (imagenesPng.isEmpty() ? "" : "<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>")
                    + "</Types>");
            zip(xlsx, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            zip(xlsx, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"
                    + "<sheet name=\"Expediente\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            zip(xlsx, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "</Relationships>");
            zip(xlsx, "xl/worksheets/sheet1.xml", hoja);
            if (!imagenesPng.isEmpty()) {
                zip(xlsx, "xl/worksheets/_rels/sheet1.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\" Target=\"../drawings/drawing1.xml\"/>"
                        + "</Relationships>");
                zip(xlsx, "xl/drawings/drawing1.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" "
                        + "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" "
                        + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                        + anclas + "</xdr:wsDr>");
                zip(xlsx, "xl/drawings/_rels/drawing1.xml.rels", relacionesImagen.toString());
                for (int i = 0; i < imagenesPng.size(); i++) {
                    xlsx.putNextEntry(new ZipEntry("xl/media/image" + (i + 1) + ".png"));
                    xlsx.write(imagenesPng.get(i));
                    xlsx.closeEntry();
                }
            }
        }
    }

    private static void escribirPdf(Path archivo, String contenido, List<File> imagenes,
                                    DisposicionImagenes disposicion, File firma, double anchoFirmaCm) throws IOException {
        List<String> lineas = envolver(contenido, 92);
        List<List<String>> paginasTexto = new ArrayList<>();
        for (int inicio = 0; inicio < lineas.size(); inicio += 58) {
            paginasTexto.add(lineas.subList(inicio, Math.min(inicio + 58, lineas.size())));
        }
        if (paginasTexto.isEmpty()) paginasTexto.add(List.of(""));

        List<BufferedImage> imagenesValidas = new ArrayList<>();
        for (File archivoImagen : imagenes) {
            if (archivoImagen.isFile()) {
                BufferedImage leida = leerImagenReducida(archivoImagen, 700, 1000);
                if (leida != null) imagenesValidas.add(recortarAlCuadrado(leida));
            }
        }
        int columnas = disposicion.getColumnas();
        int filasPorPagina = 3;
        double separacionImagenes = 12.0;
        double ladoMaximo = Math.min(disposicion.getTamanoCentimetros() * 28.346,
                (520.0 - separacionImagenes * (columnas - 1)) / columnas);
        double separacionTextoImagen = 8.0;
        int imagenesPorPagina = columnas * filasPorPagina;
        double altoFila = ladoMaximo + 24.0;
        int lineasUltimaPagina = paginasTexto.get(paginasTexto.size() - 1).size();
        double espacioDespuesTexto = 790 - 12.0 * lineasUltimaPagina - 61 - separacionTextoImagen;
        int filasDisponibles = Math.max(0, (int) Math.floor(
                (espacioDespuesTexto + separacionImagenes) / (altoFila + separacionImagenes)));
        int imagenesEnUltimaPaginaTexto = Math.min(imagenesValidas.size(),
                Math.min(filasDisponibles, filasPorPagina) * columnas);
        int cantidadImagenesRestantes = imagenesValidas.size() - imagenesEnUltimaPaginaTexto;
        int paginasImagenes = (int) Math.ceil(cantidadImagenesRestantes / (double) imagenesPorPagina);
        List<byte[]> objetos = new ArrayList<>();
        objetos.add(null);
        objetos.add("<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.US_ASCII));
        objetos.add(null);
        List<Integer> referenciasPaginas = new ArrayList<>();
        List<Integer> idsImagenes = new ArrayList<>();

        for (BufferedImage original : imagenesValidas) {
            idsImagenes.add(agregarImagenPdf(objetos, original));
        }

        int idFirma = -1;
        double anchoFirmaPuntos = 0;
        double altoFirmaPuntos = 0;
        if (firma != null && firma.isFile()) {
            BufferedImage original = leerImagenReducida(firma, 900, 900);
            if (original != null) {
                idFirma = agregarImagenPdf(objetos, original);
                anchoFirmaPuntos = Math.max(3.0, Math.min(12.0, anchoFirmaCm)) * 28.346;
                altoFirmaPuntos = anchoFirmaPuntos * original.getHeight() / (double) original.getWidth();
                if (altoFirmaPuntos > 180.0) {
                    anchoFirmaPuntos *= 180.0 / altoFirmaPuntos;
                    altoFirmaPuntos = 180.0;
                }
            }
        }
        boolean firmaIncluida = false;

        for (int indicePaginaTexto = 0; indicePaginaTexto < paginasTexto.size(); indicePaginaTexto++) {
            List<String> paginaTexto = paginasTexto.get(indicePaginaTexto);
            int idContenido = objetos.size();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            stream.write("BT\n/F1 10 Tf\n50 790 Td\n12 TL\n".getBytes(StandardCharsets.US_ASCII));
            for (String linea : paginaTexto) {
                stream.write(("(" + pdfString(linea) + ") Tj\nT*\n").getBytes(PDF_CHARSET));
            }
            stream.write("ET\n".getBytes(StandardCharsets.US_ASCII));
            StringBuilder recursos = new StringBuilder("<< /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica ")
                    .append("/Encoding /WinAnsiEncoding >> >> /XObject << ");
            double limiteInferior = 790 - 12.0 * paginaTexto.size();
            if (indicePaginaTexto == paginasTexto.size() - 1 && imagenesEnUltimaPaginaTexto > 0) {
                double cursorY = 790 - 12.0 * paginaTexto.size() - separacionTextoImagen;
                StringBuilder dibujos = new StringBuilder();
                for (int indice = 0; indice < imagenesEnUltimaPaginaTexto; indice++) {
                    int columna = indice % columnas;
                    int fila = indice / columnas;
                    int imagenesEnFila = Math.min(columnas, imagenesEnUltimaPaginaTexto - fila * columnas);
                    double anchoFila = imagenesEnFila * ladoMaximo + (imagenesEnFila - 1) * separacionImagenes;
                    double x = (612 - anchoFila) / 2 + columna * (ladoMaximo + separacionImagenes);
                    double y = cursorY - fila * (altoFila + separacionImagenes) - ladoMaximo;
                    int idImagen = idsImagenes.get(indice);
                    dibujos.append(String.format(java.util.Locale.ROOT,
                            "q\n%.2f 0 0 %.2f %.2f %.2f cm\n/Im%d Do\nQ\n",
                            ladoMaximo, ladoMaximo, x, y, indice));
                    recursos.append("/Im").append(indice).append(' ').append(idImagen).append(" 0 R ");
                    String etiqueta = String.format(java.util.Locale.ROOT,
                            "BT\n/F1 9 Tf\n%.2f %.2f Td\n(Imagen %d) Tj\nET\n",
                            x + ladoMaximo / 2 - 19, y - 12, indice + 1);
                    dibujos.append(etiqueta);
                    limiteInferior = Math.min(limiteInferior, y - 24);
                }
                stream.write(dibujos.toString().getBytes(StandardCharsets.US_ASCII));
            }
            if (indicePaginaTexto == paginasTexto.size() - 1 && paginasImagenes == 0
                    && idFirma >= 0 && limiteInferior >= 50 + altoFirmaPuntos + 14) {
                stream.write(dibujoFirmaPdf(anchoFirmaPuntos, altoFirmaPuntos).getBytes(StandardCharsets.US_ASCII));
                recursos.append("/Firma ").append(idFirma).append(" 0 R ");
                firmaIncluida = true;
            }
            recursos.append(">> >>");
            byte[] datos = stream.toByteArray();
            objetos.add(concatenar(("<< /Length " + datos.length + " >>\nstream\n").getBytes(StandardCharsets.US_ASCII),
                    datos, "\nendstream".getBytes(StandardCharsets.US_ASCII)));
            int idPagina = objetos.size();
            objetos.add(("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources "
                    + recursos + " /Contents " + idContenido + " 0 R >>")
                    .getBytes(StandardCharsets.US_ASCII));
            referenciasPaginas.add(idPagina);
        }

        for (int pagina = 0; pagina < paginasImagenes; pagina++) {
            int inicio = imagenesEnUltimaPaginaTexto + pagina * imagenesPorPagina;
            int fin = Math.min(inicio + imagenesPorPagina, imagenesValidas.size());
            StringBuilder dibujo = new StringBuilder();
            StringBuilder recursos = new StringBuilder("<< /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica ")
                    .append("/Encoding /WinAnsiEncoding >> >> /XObject << ");
            int cantidadEnPagina = fin - inicio;
            int filasEnPagina = (int) Math.ceil(cantidadEnPagina / (double) columnas);
            double altoGrupo = filasEnPagina * altoFila + Math.max(0, filasEnPagina - 1) * separacionImagenes;
            double baseY = (842 - altoGrupo) / 2;
            for (int indice = inicio; indice < fin; indice++) {
                int posicion = indice - inicio;
                int columna = posicion % columnas;
                int fila = posicion / columnas;
                int imagenesEnFila = Math.min(columnas, fin - (inicio + fila * columnas));
                double anchoFila = imagenesEnFila * ladoMaximo + (imagenesEnFila - 1) * separacionImagenes;
                double x = (612 - anchoFila) / 2 + columna * (ladoMaximo + separacionImagenes);
                double y = baseY + (filasEnPagina - 1 - fila) * (altoFila + separacionImagenes) + 16;
                int idImagen = idsImagenes.get(indice);
                dibujo.append(String.format(java.util.Locale.ROOT,
                        "q\n%.2f 0 0 %.2f %.2f %.2f cm\n/Im%d Do\nQ\n",
                        ladoMaximo, ladoMaximo, x, y, posicion));
                recursos.append("/Im").append(posicion).append(' ').append(idImagen).append(" 0 R ");
                dibujo.append(String.format(java.util.Locale.ROOT,
                        "BT\n/F1 9 Tf\n%.2f %.2f Td\n(Imagen %d) Tj\nET\n",
                        x + ladoMaximo / 2 - 19, y - 12, indice + 1));
            }
            if (pagina == paginasImagenes - 1 && idFirma >= 0
                    && baseY + 4 >= 50 + altoFirmaPuntos + 14) {
                dibujo.append(dibujoFirmaPdf(anchoFirmaPuntos, altoFirmaPuntos));
                recursos.append("/Firma ").append(idFirma).append(" 0 R ");
                firmaIncluida = true;
            }
            recursos.append(">> >>");
            byte[] datos = dibujo.toString().getBytes(StandardCharsets.US_ASCII);
            int idContenido = objetos.size();
            objetos.add(concatenar(("<< /Length " + datos.length + " >>\nstream\n").getBytes(StandardCharsets.US_ASCII),
                    datos, "\nendstream".getBytes(StandardCharsets.US_ASCII)));
            int idPagina = objetos.size();
            objetos.add(("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources "
                    + recursos + " /Contents " + idContenido + " 0 R >>").getBytes(StandardCharsets.US_ASCII));
            referenciasPaginas.add(idPagina);
        }
        if (idFirma >= 0 && !firmaIncluida) {
            byte[] datos = dibujoFirmaPdf(anchoFirmaPuntos, altoFirmaPuntos)
                    .getBytes(StandardCharsets.US_ASCII);
            int idContenido = objetos.size();
            objetos.add(concatenar(("<< /Length " + datos.length + " >>\nstream\n")
                    .getBytes(StandardCharsets.US_ASCII), datos, "\nendstream".getBytes(StandardCharsets.US_ASCII)));
            int idPagina = objetos.size();
            String recursos = "<< /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                    + "/Encoding /WinAnsiEncoding >> >> /XObject << /Firma " + idFirma + " 0 R >> >>";
            objetos.add(("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources "
                    + recursos + " /Contents " + idContenido + " 0 R >>").getBytes(StandardCharsets.US_ASCII));
            referenciasPaginas.add(idPagina);
        }
        StringBuilder kids = new StringBuilder();
        for (int id : referenciasPaginas) kids.append(id).append(" 0 R ");
        objetos.set(2, ("<< /Type /Pages /Kids [" + kids + "] /Count " + referenciasPaginas.size() + " >>")
                .getBytes(StandardCharsets.US_ASCII));

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        pdf.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        long[] offsets = new long[objetos.size()];
        for (int i = 1; i < objetos.size(); i++) {
            offsets[i] = pdf.size();
            pdf.write((i + " 0 obj\n").getBytes(StandardCharsets.US_ASCII));
            pdf.write(objetos.get(i));
            pdf.write("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        }
        long xref = pdf.size();
        pdf.write(("xref\n0 " + objetos.size() + "\n0000000000 65535 f \n").getBytes(StandardCharsets.US_ASCII));
        for (int i = 1; i < objetos.size(); i++) {
            pdf.write(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offsets[i])
                    .getBytes(StandardCharsets.US_ASCII));
        }
        pdf.write(("trailer\n<< /Size " + objetos.size() + " /Root 1 0 R >>\nstartxref\n"
                + xref + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
        java.nio.file.Files.write(archivo, pdf.toByteArray());
    }

    private static List<String> envolver(String texto, int ancho) {
        List<String> resultado = new ArrayList<>();
        for (String linea : texto.split("\\R", -1)) {
            if (linea.isEmpty()) {
                resultado.add("");
                continue;
            }
            for (int inicio = 0; inicio < linea.length(); inicio += ancho) {
                resultado.add(linea.substring(inicio, Math.min(inicio + ancho, linea.length())));
            }
        }
        return resultado;
    }

    private static int agregarImagenPdf(List<byte[]> objetos, BufferedImage original) throws IOException {
        BufferedImage argb = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graficos = argb.createGraphics();
        graficos.drawImage(original, 0, 0, null);
        graficos.dispose();
        ByteArrayOutputStream pixeles = new ByteArrayOutputStream();
        ByteArrayOutputStream alfa = new ByteArrayOutputStream();
        try (DeflaterOutputStream compresorColor = new DeflaterOutputStream(pixeles);
             DeflaterOutputStream compresorAlfa = new DeflaterOutputStream(alfa)) {
            for (int py = 0; py < argb.getHeight(); py++) {
                for (int px = 0; px < argb.getWidth(); px++) {
                    int color = argb.getRGB(px, py);
                    compresorColor.write((color >> 16) & 0xff);
                    compresorColor.write((color >> 8) & 0xff);
                    compresorColor.write(color & 0xff);
                    compresorAlfa.write((color >>> 24) & 0xff);
                }
            }
        }
        byte[] rgb = pixeles.toByteArray();
        byte[] transparencia = alfa.toByteArray();
        int idMascara = objetos.size();
        objetos.add(concatenar(
                ("<< /Type /XObject /Subtype /Image /Width " + argb.getWidth() + " /Height "
                        + argb.getHeight() + " /ColorSpace /DeviceGray /BitsPerComponent 8 "
                        + "/Filter /FlateDecode /Length " + transparencia.length + " >>\nstream\n")
                        .getBytes(StandardCharsets.US_ASCII),
                transparencia, "\nendstream".getBytes(StandardCharsets.US_ASCII)));
        int idImagen = objetos.size();
        objetos.add(concatenar(
                ("<< /Type /XObject /Subtype /Image /Width " + argb.getWidth() + " /Height "
                        + argb.getHeight() + " /ColorSpace /DeviceRGB /BitsPerComponent 8 "
                        + "/SMask " + idMascara + " 0 R /Filter /FlateDecode /Length "
                        + rgb.length + " >>\nstream\n").getBytes(StandardCharsets.US_ASCII),
                rgb, "\nendstream".getBytes(StandardCharsets.US_ASCII)));
        return idImagen;
    }

    private static String dibujoFirmaPdf(double ancho, double alto) {
        double x = 562 - ancho;
        return String.format(java.util.Locale.ROOT,
                "q\n%.2f 0 0 %.2f %.2f 50.00 cm\n/Firma Do\nQ\n"
                        + "BT\n/F1 9 Tf\n%.2f 38.00 Td\n(Firma) Tj\nET\n",
                ancho, alto, x, x);
    }

    private static BufferedImage leerImagenReducida(File archivo, int anchoMaximo, int altoMaximo)
            throws IOException {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(archivo)) {
            if (entrada == null) return null;
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) return null;
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                int muestreo = Math.max(1, Math.max((int) Math.ceil(ancho / (double) anchoMaximo),
                        (int) Math.ceil(alto / (double) altoMaximo)));
                ImageReadParam parametros = lector.getDefaultReadParam();
                parametros.setSourceSubsampling(muestreo, muestreo, 0, 0);
                return lector.read(0, parametros);
            } finally {
                lector.dispose();
            }
        }
    }

    /** Recorta el centro para llenar los cuadros cuadrados sin estirar el contenido. */
    private static BufferedImage recortarAlCuadrado(BufferedImage imagen) {
        int lado = Math.min(imagen.getWidth(), imagen.getHeight());
        int x = (imagen.getWidth() - lado) / 2;
        int y = (imagen.getHeight() - lado) / 2;
        BufferedImage cuadrada = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graficos = cuadrada.createGraphics();
        graficos.drawImage(imagen, 0, 0, lado, lado, x, y, x + lado, y + lado, null);
        graficos.dispose();
        return cuadrada;
    }

    private static String pdfString(String texto) {
        return texto.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static byte[] concatenar(byte[]... partes) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        for (byte[] parte : partes) salida.write(parte);
        return salida.toByteArray();
    }

    private static void zip(ZipOutputStream zip, String ruta, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(ruta));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String xml(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
