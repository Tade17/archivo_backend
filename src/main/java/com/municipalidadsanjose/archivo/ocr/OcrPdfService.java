package com.municipalidadsanjose.archivo.ocr;

import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.exception.SolicitudInvalidaException;
import com.municipalidadsanjose.archivo.repository.DocumentoDigitalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// A propósito NO tiene ningún método para editar el texto ni la posición de
// un fragmento: el archivo histórico debe quedar exactamente como lo generó
// el OCR, sin edición humana (requisito legal, ver OCR.md). Antes existía un
// método corregir()/validarCorreccion() para eso; se retiró del todo en vez
// de solo ocultarlo en la interfaz.
@Service
public class OcrPdfService {
    private final DocumentoDigitalRepository documentos;

    public OcrPdfService(DocumentoDigitalRepository documentos) {
        this.documentos = documentos;
    }

    @Transactional(readOnly = true)
    public String rutaPdf(UUID id) {
        var d = documentos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("DocumentoDigital", id));
        if (d.getRutaPdf() == null) throw new SolicitudInvalidaException("El PDF digitalizado todavía no está disponible.");
        return d.getRutaPdf();
    }
}
