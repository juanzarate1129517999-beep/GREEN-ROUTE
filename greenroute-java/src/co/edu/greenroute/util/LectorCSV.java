package co.edu.greenroute.util;

import co.edu.greenroute.excepciones.DatosInvalidosException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Lee un archivo CSV fila por fila. */
public final class LectorCSV {

    private LectorCSV() {
    }

    public static void leer(Path archivo, Consumer<Map<String, String>> porFila) throws DatosInvalidosException {
        if (!Files.exists(archivo)) {
            throw new DatosInvalidosException("No existe el archivo de datos: " + archivo);
        }
        try (BufferedReader br = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            String encabezado = br.readLine();
            if (encabezado == null) {
                throw new DatosInvalidosException("Archivo vacío: " + archivo);
            }
            List<String> columnas = dividir(encabezado.replace("﻿", ""));
            String linea;
            int numero = 1;
            while ((linea = br.readLine()) != null) {
                numero++;
                if (linea.isEmpty()) continue;
                List<String> valores = dividir(linea);
                if (valores.size() != columnas.size()) {
                    throw new DatosInvalidosException(String.format(
                            "%s, línea %d: se esperaban %d columnas y hay %d",
                            archivo.getFileName(), numero, columnas.size(), valores.size()));
                }
                Map<String, String> fila = new HashMap<>(columnas.size() * 2);
                for (int i = 0; i < columnas.size(); i++) {
                    fila.put(columnas.get(i), valores.get(i));
                }
                porFila.accept(fila);
            }
        } catch (IOException e) {
            throw new DatosInvalidosException("Error leyendo " + archivo + ": " + e.getMessage());
        }
    }

    static List<String> dividir(String linea) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                if (entreComillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else {
                    entreComillas = !entreComillas;
                }
            } else if (c == ',' && !entreComillas) {
                campos.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        campos.add(actual.toString());
        return campos;
    }
}
