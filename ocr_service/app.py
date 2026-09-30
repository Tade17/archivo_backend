import asyncio
import base64
import io
import os
import threading
from functools import lru_cache

import numpy as np
import pypdfium2 as pdfium
from fastapi import FastAPI, Header, HTTPException, Request, Response
from PIL import Image, ImageSequence
from rapidocr import EngineType, LangDet, LangRec, ModelType, OCRVersion, RapidOCR
from searchable_pdf import build_pdf

app = FastAPI(title="OCR Archivo Municipal", docs_url=None, redoc_url=None)

MAX_BYTES = int(os.getenv("OCR_MAX_BYTES", str(25 * 1024 * 1024)))
MAX_PAGES = int(os.getenv("OCR_MAX_PAGES", "100"))
PDF_DPI = int(os.getenv("OCR_PDF_DPI", "220"))
MAX_PIXELS = int(os.getenv("OCR_MAX_PIXELS", "250000000"))
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
        pages = []
        pixels = 0
        with pdfium.PdfDocument(data) as document:
            if len(document) > MAX_PAGES:
                raise ValueError(f"El PDF supera el máximo de {MAX_PAGES} páginas")
            scale = PDF_DPI / 72
            for index in range(len(document)):
                page = document[index]
                try:
                    width, height = page.get_size()
                    pixels += int(width * scale + 1) * int(height * scale + 1)
                    if pixels > MAX_PIXELS:
                        raise ValueError("El documento supera el máximo de píxeles permitido")
                    bitmap = page.render(scale=scale)
                    try:
                        pages.append(bitmap.to_pil().convert("RGB"))
                    finally:
                        bitmap.close()
                finally:
                    page.close()
        return pages

    with Image.open(io.BytesIO(data)) as image:
        pages = []
        pixels = 0
        for frame in ImageSequence.Iterator(image):
            if len(pages) >= MAX_PAGES:
                raise ValueError(f"El documento supera el máximo de {MAX_PAGES} páginas")
            pixels += frame.width * frame.height
            if pixels > MAX_PIXELS:
                raise ValueError("El documento supera el máximo de píxeles permitido")
            pages.append(frame.copy().convert("RGB"))
    if not pages:
        raise ValueError("El documento no contiene páginas")
    return pages


def recognize(data: bytes, content_type: str, filename: str) -> dict:
    pages = render_pages(data, content_type, filename)
    texts: list[str] = []
    weighted_score = 0.0
    weight = 0
    total_lines = 0
    layout = []
    engine = get_engine()

    for page_number, image in enumerate(pages, start=1):
        # RapidOCR mantiene el orden de lectura de las líneas detectadas.
        with _engine_lock:
            result = engine(np.asarray(image))
        page_texts = list(result.txts) if result.txts is not None else []
        page_scores = list(result.scores) if result.scores is not None else []
        page_boxes = list(result.boxes) if result.boxes is not None else []
        blocks = []
        for index, (text, box) in enumerate(zip(page_texts, page_boxes)):
            points = np.asarray(box, dtype=float).reshape(-1, 2)
            if not np.isfinite(points).all():
                continue
            x = max(0.0, min(float(points[:, 0].min()), image.width - 1.0))
            y = max(0.0, min(float(points[:, 1].min()), image.height - 1.0))
            right = max(x + 1, min(float(points[:, 0].max()), float(image.width)))
            bottom = max(y + 1, min(float(points[:, 1].max()), float(image.height)))
            blocks.append({"id": str(index), "text": text.strip(), "x": x, "y": y,
                           "width": right - x, "height": bottom - y,
                           "confidence": float(page_scores[index]) if index < len(page_scores) else 0.0})
        layout.append({"width": image.width, "height": image.height, "blocks": blocks})
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
        "layout": layout,
        "pdfBase64": base64.b64encode(build_pdf(pages, layout, PDF_DPI)).decode("ascii"),
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


@app.post("/pdf")
async def regenerate_pdf(request: Request) -> Response:
    # Bound the JSON envelope as well as the decoded original before rendering it.
    body = await request.body()
    if len(body) > MAX_BYTES * 2 + 12_000_000:
        raise HTTPException(status_code=413, detail="La solicitud supera el tamaño permitido")
    try:
        import json
        payload = json.loads(body)
        if not isinstance(payload, dict) or not isinstance(payload.get("fileBase64"), str):
            raise ValueError("Debe proporcionar el archivo original")
        data = base64.b64decode(payload["fileBase64"], validate=True)
        if not data:
            raise ValueError("El archivo está vacío")
        if len(data) > MAX_BYTES:
            raise HTTPException(status_code=413, detail="El archivo supera el tamaño permitido")
        content_type = payload.get("contentType", "application/octet-stream")
        filename = payload.get("filename", "documento")
        if not isinstance(content_type, str) or not isinstance(filename, str):
            raise ValueError("Tipo de archivo o nombre inválido")
        def generate() -> bytes:
            pages = render_pages(data, content_type, filename)
            return build_pdf(pages, payload.get("layout"), PDF_DPI)
        result = await asyncio.to_thread(generate)
        return Response(content=result, media_type="application/pdf")
    except (ValueError, TypeError) as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    except HTTPException:
        raise
    except Exception as error:
        raise HTTPException(status_code=422, detail="No se pudo generar el PDF del documento") from error
