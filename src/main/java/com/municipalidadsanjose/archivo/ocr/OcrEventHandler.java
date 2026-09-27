package com.municipalidadsanjose.archivo.ocr;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OcrEventHandler {

    private final OcrWorker worker;

    public OcrEventHandler(OcrWorker worker) {
        this.worker = worker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void procesar(OcrSolicitadoEvent event) {
        worker.procesar(event.documentoId());
    }
}
