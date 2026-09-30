package com.municipalidadsanjose.archivo.ocr;

import com.municipalidadsanjose.archivo.exception.SolicitudInvalidaException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OcrPdfServiceTest {
    private List<OcrPagina> pagina(String texto, double x) {
        return List.of(new OcrPagina(600, 800, List.of(
                new OcrPagina.OcrBloque("p1-b1", texto, x, 20, 200, 30, .98))));
    }
    @Test void permiteCorregirSinCambiarLaUbicacion() {
        assertDoesNotThrow(() -> OcrPdfService.validarCorreccion(pagina("O45", 10), pagina("045", 10)));
    }
    @Test void rechazaMoverLosFragmentos() {
        assertThrows(SolicitudInvalidaException.class,
                () -> OcrPdfService.validarCorreccion(pagina("O45", 10), pagina("045", 40)));
    }
    @Test void rechazaEliminarPaginas() {
        assertThrows(SolicitudInvalidaException.class,
                () -> OcrPdfService.validarCorreccion(pagina("045", 10), List.of()));
    }
}
