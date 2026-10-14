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
from contextlib import closing
import math

import numpy as np
import pypdfium2 as pdfium
from fastapi import FastAPI, Header, HTTPException, Request, Response
from PIL import Image, UnidentifiedImageError
from rapidocr import EngineType, LangDet, LangRec, ModelType, OCRVersion, RapidOCR
from searchable_pdf import build_pdf, ImagePdfBuilder, overlay_pdf
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


def is_pdf_source(data, content_type, filename):
    return data.startswith(b'%PDF-') or content_type == 'application/pdf' or filename.lower().endswith('.pdf')


def preflight_pdf(document):
    limit = min(MAX_PAGES, 100)
    if not 1 <= len(document) <= limit:
        raise ValueError(f'El PDF supera el máximo de {limit} páginas o está vacío')
    pixels = 0
    for index in range(len(document)):
        page = document[index]
        try:
            width, height = page.get_size()
            if not math.isfinite(width) or not math.isfinite(height) or min(width, height) <= 0:
                raise ValueError('La página PDF no tiene dimensiones válidas')
            pixels += math.ceil(width * PDF_DPI / 72) * math.ceil(height * PDF_DPI / 72)
            if pixels > min(MAX_PIXELS, 250_000_000):
                raise ValueError('El documento supera el máximo de píxeles permitido')
        finally:
            page.close()


def iter_render_pages(data: bytes, content_type: str, filename: str):
    """Preflight every page/frame, then own at most the current decoded raster."""
    if is_pdf_source(data, content_type, filename):
        document = pdfium.PdfDocument(data)
        try:
            preflight_pdf(document)
            for index in range(len(document)):
                page = document[index]
                try:
                    bitmap = page.render(scale=PDF_DPI / 72)
                    try:
                        image = bitmap.to_pil().convert('RGB')
                        try:
                            yield image
                        finally:
                            image.close()
                    finally:
                        bitmap.close()
                finally:
                    page.close()
        finally:
            document.close()
    else:
        with Image.open(io.BytesIO(data)) as source:
            count = getattr(source, 'n_frames', 1)
            if not 1 <= count <= min(MAX_PAGES, 100):
                raise ValueError(f'El documento supera el máximo de {min(MAX_PAGES, 100)} páginas')
            pixels = 0
            for index in range(count):
                source.seek(index)
                pixels += source.width * source.height
                if pixels > min(MAX_PIXELS, 250_000_000):
                    raise ValueError('El documento supera el máximo de píxeles permitido')
            for index in range(count):
                source.seek(index)
                image = source.convert('RGB')
                try:
                    yield image
                finally:
                    image.close()


def render_pages(data: bytes, content_type: str, filename: str) -> list[Image.Image]:
    # Compatibility helper for callers needing independent images, not the OCR path.
    copies = []
    try:
        with closing(iter_render_pages(data, content_type, filename)) as pages:
            for image in pages:
                copies.append(image.copy())
        return copies
    except Exception:
        for image in copies:
            image.close()
        raise


def recognize(data: bytes, content_type: str, filename: str, binary_pdf: bool = False) -> dict:
    source_pdf = is_pdf_source(data, content_type, filename)
    output_limit = MAX_ARTIFACT_BYTES if binary_pdf else None
    builder = None if source_pdf else ImagePdfBuilder(PDF_DPI, output_limit)
    page_texts_by_page = []
    weighted_score = 0.0
    weight = total_lines = 0
    layout = []
    engine = get_engine()
    with closing(iter_render_pages(data, content_type, filename)) as pages:
        for image in pages:
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
                blocks.append({'id': str(index), 'text': text.strip(), 'x': x, 'y': y,
                               'width': right - x, 'height': bottom - y,
                               'confidence': float(page_scores[index]) if index < len(page_scores) else 0.0})
            page_layout = {'width': image.width, 'height': image.height, 'blocks': blocks}
            layout.append(page_layout)
            if builder is not None:
                builder.add(image, page_layout)
            page_texts_by_page.append(page_texts)
            for text, score in zip(page_texts, page_scores):
                line_weight = max(1, len(text.strip()))
                weighted_score += float(score) * line_weight
                weight += line_weight
            total_lines += len(page_texts)
    texts = []
    for number, page_texts in enumerate(page_texts_by_page, start=1):
        if len(layout) > 1:
            texts.append(f'[Página {number}]')
        texts.extend(text.strip() for text in page_texts if text and text.strip())
        if number < len(layout):
            texts.append('')
    pdf = overlay_pdf(data, layout, PDF_DPI, output_limit) if source_pdf else builder.finish()
    result = {'text': '\n'.join(texts).strip(), 'confidence': round(weighted_score / weight, 4) if weight else 0.0,
              'pages': len(layout), 'lines': total_lines, 'model': 'PP-OCRv5 latin / ONNX Runtime', 'layout': layout}
    if binary_pdf:
        result['pdfBytes'] = pdf
    else:
        result['pdfBase64'] = base64.b64encode(pdf).decode('ascii')
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
            if is_pdf_source(data, content_type, filename):
                document = pdfium.PdfDocument(data)
                try:
                    preflight_pdf(document)
                finally:
                    document.close()
                return overlay_pdf(data, payload.get('layout'), PDF_DPI, MAX_ARTIFACT_BYTES)
            layout = payload.get('layout')
            if not isinstance(layout, list):
                raise ValueError('La distribución debe contener todas las páginas del documento')
            builder = ImagePdfBuilder(PDF_DPI, MAX_ARTIFACT_BYTES)
            count = 0
            with closing(iter_render_pages(data, content_type, filename)) as pages:
                for image in pages:
                    if count >= len(layout):
                        raise ValueError('La distribución debe contener todas las páginas del documento')
                    builder.add(image, layout[count])
                    count += 1
            if count != len(layout):
                raise ValueError('La distribución debe contener todas las páginas del documento')
            return builder.finish()
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
