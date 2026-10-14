import asyncio
import base64
import io
import json
import logging
import os
import re
import threading
import time
import uuid
from functools import lru_cache

import numpy as np
import pypdfium2 as pdfium
from fastapi import FastAPI, Header, HTTPException, Request, Response
from PIL import Image, ImageSequence, UnidentifiedImageError
from rapidocr import EngineType, LangDet, LangRec, ModelType, OCRVersion, RapidOCR
from searchable_pdf import build_pdf
from artifact_store import ArtifactError, ArtifactStore
from starlette.responses import StreamingResponse

app = FastAPI(title="OCR Archivo Municipal", docs_url=None, redoc_url=None)

MAX_BYTES = int(os.getenv("OCR_MAX_BYTES", str(25 * 1024 * 1024)))
MAX_PAGES = int(os.getenv("OCR_MAX_PAGES", "100"))
PDF_DPI = int(os.getenv("OCR_PDF_DPI", "220"))
MAX_PIXELS = int(os.getenv("OCR_MAX_PIXELS", "250000000"))
IMAGE_BATCH_MAX_BYTES = 20 * 1024 * 1024
IMAGE_BATCH_MAX_FILES = 100
MAX_ARTIFACT_BYTES = min(int(os.getenv("OCR_ARTIFACT_MAX_BYTES", "134217728")), 128 * 1024 * 1024)
MAX_METADATA_BYTES = 16 * 1024 * 1024
logger = logging.getLogger("archivo.ocr")
_engine_lock = threading.Lock()


class MetadataLimitError(ValueError):
    pass


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
        # pypdfium2 4.30 no implementa el protocolo de context manager en
        # PdfDocument (falla con "does not support the context manager
        # protocol" si se usa "with"); se libera a mano con close().
        document = pdfium.PdfDocument(data)
        try:
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
        finally:
            document.close()
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


def recognize(data: bytes, content_type: str, filename: str, binary_pdf: bool = False) -> dict:
    pages = render_pages(data, content_type, filename)
    try:
        return _recognize_pages(pages, binary_pdf)
    finally:
        for page in pages:
            page.close()


def _recognize_pages(pages: list[Image.Image], binary_pdf: bool) -> dict:
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

    pdf = build_pdf(pages, layout, PDF_DPI, max_output_bytes=MAX_ARTIFACT_BYTES if binary_pdf else None)
    result = {
        "text": "\n".join(texts).strip(),
        "confidence": round(weighted_score / weight, 4) if weight else 0.0,
        "pages": len(pages),
        "lines": total_lines,
        "model": "PP-OCRv5 latin / ONNX Runtime",
        "layout": layout,
    }
    if binary_pdf:
        result["pdfBytes"] = pdf
    else:
        result["pdfBase64"] = base64.b64encode(pdf).decode("ascii")
    return result


@lru_cache(maxsize=1)
def get_artifact_store() -> ArtifactStore:
    return ArtifactStore(os.getenv("OCR_ARTIFACT_DIR", "/var/lib/archivo-ocr/artifacts"),
                         max_bytes=MAX_ARTIFACT_BYTES,
                         max_total_bytes=int(os.getenv("OCR_ARTIFACT_TOTAL_BYTES", "536870912")),
                         max_count=int(os.getenv("OCR_ARTIFACT_MAX_COUNT", "32")),
                         ttl_seconds=int(os.getenv("OCR_ARTIFACT_TTL_SECONDS", "3600")))


@app.post("/v2/ocr")
async def ocr_v2(request: Request, content_type: str = Header(default="application/octet-stream"),
                 x_filename_base64: str | None = Header(default=None),
                 x_request_id: str | None = Header(default=None)) -> Response:
    request_id = x_request_id if isinstance(x_request_id, str) and re.fullmatch(r"[a-f0-9]{32}", x_request_id) else uuid.uuid4().hex
    start = time.monotonic()
    data = bytearray()
    artifact = None
    artifact_bytes = metadata_bytes = 0
    try:
        async for chunk in request.stream():
            if len(data) + len(chunk) > MAX_BYTES:
                raise HTTPException(status_code=413, detail="OCR_INPUT_LIMIT")
            data.extend(chunk)
        if not data:
            raise HTTPException(status_code=400, detail="OCR_INPUT_EMPTY")
        result = await asyncio.to_thread(recognize, bytes(data), content_type,
                                         decode_filename(x_filename_base64), True)
        pdf = result.pop("pdfBytes")
        artifact_bytes = len(pdf)
        artifact = await asyncio.to_thread(get_artifact_store().put, pdf)
        result.update({"artifact": artifact, "requestId": request_id})
        payload = json.dumps(result, ensure_ascii=False, allow_nan=False).encode("utf-8")
        metadata_bytes = len(payload)
        if metadata_bytes > MAX_METADATA_BYTES:
            raise MetadataLimitError()
        logger.info(json.dumps({"event": "ocr_complete", "requestId": request_id,
                    "inputBytes": len(data), "artifactBytes": len(pdf), "metadataBytes": len(payload),
                    "pages": result["pages"], "durationMs": round((time.monotonic() - start) * 1000)}))
        return Response(payload, media_type="application/json",
                        headers={"Cache-Control": "private, no-store", "X-Request-ID": request_id})
    except Exception as error:
        if artifact is not None:
            try:
                get_artifact_store().discard(artifact["id"], artifact["capability"])
            except ArtifactError:
                logger.warning(json.dumps({"event": "artifact_cleanup_failed", "requestId": request_id}))
        code = (error.code if isinstance(error, ArtifactError) else
                "OCR_METADATA_LIMIT" if isinstance(error, MetadataLimitError) else "OCR_PROCESSING_FAILED")
        logger.warning(json.dumps({"event": "ocr_failed", "requestId": request_id,
                       "inputBytes": len(data), "artifactBytes": artifact_bytes, "metadataBytes": metadata_bytes,
                       "code": code, "category": type(error).__name__,
                       "durationMs": round((time.monotonic() - start) * 1000)}))
        if isinstance(error, HTTPException):
            error.headers = {"X-Request-ID": request_id}
            raise
        status = 503 if isinstance(error, ArtifactError) and error.code == "cache_busy" else 422
        raise HTTPException(status_code=status, detail="OCR_PROCESSING_FAILED",
                            headers={"X-Request-ID": request_id}) from error


class ArtifactResponse(StreamingResponse):
    def __init__(self, lease):
        self.lease = lease
        super().__init__(lease.chunks(), media_type="application/pdf",
                         headers={"Content-Length": str(lease.size), "Cache-Control": "private, no-store"})

    async def __call__(self, scope, receive, send):
        try:
            await super().__call__(scope, receive, send)
        finally:
            self.lease.close()


@app.get("/v2/artifacts/{artifact_id}")
def download_artifact(artifact_id: str, x_artifact_capability: str | None = Header(default=None)) -> Response:
    try:
        return ArtifactResponse(get_artifact_store().open(artifact_id, x_artifact_capability))
    except ArtifactError as error:
        raise HTTPException(status_code=404, detail="OCR_ARTIFACT_UNAVAILABLE") from error


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


def combine_images(files: object) -> bytes:
    if not isinstance(files, list) or not 1 <= len(files) <= IMAGE_BATCH_MAX_FILES:
        raise ValueError("Selecciona entre 1 y 100 imágenes")
    pages = []
    total_bytes = pixels = 0
    try:
        for item in files:
            if not isinstance(item, dict) or not isinstance(item.get("fileBase64"), str):
                raise ValueError("Imagen inválida")
            encoded = item["fileBase64"]
            if len(encoded) > 4 * ((IMAGE_BATCH_MAX_BYTES - total_bytes + 2) // 3):
                raise ValueError("Las imágenes superan el tamaño máximo permitido")
            data = base64.b64decode(encoded, validate=True)
            total_bytes += len(data)
            if not data or total_bytes > IMAGE_BATCH_MAX_BYTES:
                raise ValueError("Las imágenes están vacías o superan el tamaño máximo permitido")
            # Decode actual image headers; client MIME and extension are not authority.
            with Image.open(io.BytesIO(data)) as image:
                if image.format not in {"PNG", "JPEG", "TIFF"}:
                    raise ValueError("Solo se permiten imágenes PNG, JPEG o TIFF")
                frame_count = getattr(image, "n_frames", 1)
                page_limit = min(MAX_PAGES, 100)
                if len(pages) + frame_count > page_limit:
                    raise ValueError(f"Las imágenes superan el máximo de {page_limit} páginas")
                for index in range(frame_count):
                    image.seek(index)
                    pixels += image.width * image.height
                    if pixels > min(MAX_PIXELS, 250_000_000):
                        raise ValueError("Las imágenes superan el máximo de píxeles permitido")
                    pages.append(image.convert("RGB"))
        layout = [{"width": page.width, "height": page.height, "blocks": []} for page in pages]
        return build_pdf(pages, layout, PDF_DPI, max_output_bytes=IMAGE_BATCH_MAX_BYTES)
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError) as error:
        raise ValueError("Una imagen está dañada o no es válida") from error
    finally:
        for page in pages:
            page.close()


@app.post("/images/pdf")
async def image_batch_pdf(request: Request) -> Response:
    # Bound the envelope while reading, before JSON/base64 allocations.
    max_envelope = 4 * ((IMAGE_BATCH_MAX_BYTES + 2) // 3) + 100_000
    body = bytearray()
    async for chunk in request.stream():
        if len(body) + len(chunk) > max_envelope:
            raise HTTPException(status_code=413, detail="La solicitud supera el tamaño permitido")
        body.extend(chunk)
    try:
        payload = json.loads(body)
        if not isinstance(payload, dict):
            raise ValueError("Solicitud de imágenes inválida")
        result = await asyncio.to_thread(combine_images, payload.get("images"))
        return Response(content=result, media_type="application/pdf")
    except (ValueError, TypeError) as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    except Exception as error:
        raise HTTPException(status_code=422, detail="No se pudo unir las imágenes en PDF") from error
