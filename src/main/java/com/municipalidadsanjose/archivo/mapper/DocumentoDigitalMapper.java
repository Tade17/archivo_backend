package com.municipalidadsanjose.archivo.mapper;

import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalRequestDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalResponseDTO;
import com.municipalidadsanjose.archivo.entity.DocumentoDigital;
import com.municipalidadsanjose.archivo.entity.Expediente;
import com.municipalidadsanjose.archivo.entity.Usuario;
import org.springframework.stereotype.Component;

@Component
public class DocumentoDigitalMapper {

    public DocumentoDigital toEntity(DocumentoDigitalRequestDTO dto, Expediente expediente, Usuario tecnicoResponsable) {
        DocumentoDigital documento = new DocumentoDigital();
        documento.setExpediente(expediente);
        documento.setNombreArchivo(dto.nombreArchivo());
        documento.setRutaAlmacenamiento(dto.rutaAlmacenamiento());
        documento.setTipoMime(dto.tipoMime());
        documento.setHashSha256(dto.hashSha256());
        documento.setTecnicoResponsable(tecnicoResponsable);
        documento.setEscanerUtilizado(dto.escanerUtilizado());
        documento.setResolucionDpi(dto.resolucionDpi());
        documento.setFormatoSalida(dto.formatoSalida());
        documento.setOcrTexto(dto.ocrTexto());
        return documento;
    }

    public DocumentoDigitalResponseDTO toResponseDTO(DocumentoDigital documento) {
        return new DocumentoDigitalResponseDTO(
                documento.getId(),
                documento.getExpediente().getId(),
                documento.getExpediente().getCodigoUnico(),
                documento.getNombreArchivo(),
                documento.getRutaAlmacenamiento(),
                documento.getTipoMime(),
                documento.getHashSha256(),
                documento.getFechaDigitalizacion(),
                documento.getTecnicoResponsable().getNombre(),
                documento.getEscanerUtilizado(),
                documento.getResolucionDpi(),
                documento.getFormatoSalida(),
                documento.getOcrTexto(),
                documento.getFechaActualizacion()
        );
    }
}
