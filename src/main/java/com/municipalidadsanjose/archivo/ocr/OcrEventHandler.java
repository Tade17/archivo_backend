package com.municipalidadsanjose.archivo.ocr;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OcrEventHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void procesar(OcrSolicitadoEvent event) {
        // The committed PENDIENTE row is the queue. Continuous claims need no Async submission.
    }
}
