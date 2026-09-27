import asyncio
import base64
import io
import os
import threading
from functools import lru_cache

import numpy as np
import pypdfium2 as pdfium
from fastapi import FastAPI, Header, HTTPException, Request
from PIL import Image, ImageSequence
from rapidocr import EngineType, LangDet, LangRec, ModelType, OCRVersion, RapidOCR

app = FastAPI(title="OCR Archivo Municipal", docs_url=None, redoc_url=None)

MAX_BYTES = int(os.getenv("OCR_MAX_BYTES", str(25 * 1024 * 1024)))
MAX_PAGES = int(os.getenv("OCR_MAX_PAGES", "100"))
PDF_DPI = int(os.getenv("OCR_PDF_DPI", "220"))
_engine_lock = threading.Lock()


@lru_cache(maxsize=1)
def get_engine() -> RapidOCR:
    # Misma familia usada por ImageTrans: detección PaddleOCR, clasificación de
    # orientación y reconocimiento latino PP-OCRv5 ejecutados localmente con ONNX.
    return RapidOCR(
        params={
            "Global.text_score": 0.45,
            "Det.engine_type": EngineType.ONNXRUNTIME,
            "Det.lang_type": LangDet.CH,
            "Det.model_type": ModelType.MOBILE,
            "Det.ocr_version": OCRVersion.PPOCRV5,
            "Cls.engine_type": EngineType.ONNXRUNTIME,
            "Rec.engine_type": EngineType.ONNXRUNTIME,
            "Rec.lang_type": LangRec.LATIN,
            "Rec.model_type": ModelType.MOBILE,
            "Rec.ocr_version": OCRVersion.PPOCRV5,
        }
    )


def decode_filename(value: str | None) -> str:
    if not value:
        return "documento"
    try:
        return base64.b64decode(value).decode("utf-8")
    except (ValueError, UnicodeDecodeError):
        return "documento"


def render_pages(data: bytes, content_type: str, filename: str) -> list[Image.Image]:
    is_pdf = content_type == "application/pdf" or filename.lower().endswith(".pdf")
    if is_pdf:
        document = pdfium.PdfDocument(data)
        if len(document) > MAX_PAGES:
            raise ValueError(f"El PDF supera el máximo de {MAX_PAGES} páginas")
        scale = PDF_DPI / 72
        return [page.render(scale=scale).to_pil().convert("RGB") for page in document]

    with Image.open(io.BytesIO(data)) as image:
        pages = [frame.copy().convert("RGB") for frame in ImageSequence.Iterator(image)]
    if len(pages) > MAX_PAGES:
        raise ValueError(f"El documento supera el máximo de {MAX_PAGES} páginas")
    return pages


def recognize(data: bytes, content_type: str, filename: str) -> dict:
    pages = render_pages(data, content_type, filename)
    texts: list[str] = []
    weighted_score = 0.0
    weight = 0
    total_lines = 0
    engine = get_engine()

    for page_number, image in enumerate(pages, start=1):
        # RapidOCR mantiene el orden de lectura de las líneas detectadas.
        with _engine_lock:
            result = engine(np.asarray(image))
        page_texts = list(result.txts or ())
        page_scores = list(result.scores or ())
        if len(pages) > 1:
            texts.append(f"[Página {page_number}]")
        texts.extend(text.strip() for text in page_texts if text and text.strip())
        if page_number < len(pages):
            texts.append("")
        for text, score in zip(page_texts, page_scores):
            line_weight = max(1, len(text.strip()))
            weighted_score += float(score) * line_weight
            weight += line_weight
        total_lines += len(page_texts)

    return {
        "text": "\n".join(texts).strip(),
        "confidence": round(weighted_score / weight, 4) if weight else 0.0,
        "pages": len(pages),
        "lines": total_lines,
        "model": "PP-OCRv5 latin / ONNX Runtime",
    }


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": "PP-OCRv5-latin"}


@app.post("/ocr")
async def ocr(
    request: Request,
    content_type: str = Header(default="application/octet-stream"),
    x_filename_base64: str | None = Header(default=None),
) -> dict:
    data = await request.body()
    if not data:
        raise HTTPException(status_code=400, detail="El archivo está vacío")
    if len(data) > MAX_BYTES:
        raise HTTPException(status_code=413, detail="El archivo supera el tamaño permitido")
    filename = decode_filename(x_filename_base64)
    try:
        return await asyncio.to_thread(recognize, data, content_type, filename)
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    except Exception as error:
        raise HTTPException(
            status_code=422,
            detail=f"No se pudo reconocer el contenido de {filename}",
        ) from error
