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
import hashlib
from importlib.metadata import version
from pathlib import Path
import rapidocr
from functools import lru_cache, wraps
from contextlib import closing, asynccontextmanager
import math

import numpy as np
import pypdfium2 as pdfium
from fastapi import FastAPI, Header, HTTPException, Request, Response
from PIL import Image, UnidentifiedImageError
from rapidocr import EngineType, LangDet, LangRec, ModelType, OCRVersion, RapidOCR
from searchable_pdf import build_pdf, ImagePdfBuilder, overlay_pdf
from artifact_store import ArtifactError, ArtifactStore
from job_store import JobError, JobStore
from starlette.responses import StreamingResponse

@asynccontextmanager
async def lifespan(application):
    worker = JobWorker(get_job_store())
    application.state.job_worker = worker
    worker.start()
    try:
        yield
    finally:
        await asyncio.to_thread(worker.close)


app = FastAPI(title="OCR Archivo Municipal", docs_url=None, redoc_url=None, lifespan=lifespan)

MAX_BYTES = int(os.getenv("OCR_MAX_BYTES", str(25 * 1024 * 1024)))
MAX_PAGES = int(os.getenv("OCR_MAX_PAGES", "100"))
PDF_DPI = int(os.getenv("OCR_PDF_DPI", "220"))


def cpu_threads(name: str, default: int) -> int:
    value = int(os.getenv(name, str(default)))
    if value not in (1, 2):
        raise ValueError("OCR CPU threads must be 1 or 2")
    return value


ORT_INTRA_THREADS = cpu_threads("OCR_ORT_INTRA_THREADS", 2)
ORT_INTER_THREADS = cpu_threads("OCR_ORT_INTER_THREADS", 1)
MAX_PIXELS = int(os.getenv("OCR_MAX_PIXELS", "250000000"))
IMAGE_BATCH_MAX_BYTES = 20 * 1024 * 1024
IMAGE_BATCH_MAX_FILES = 100
MAX_ARTIFACT_BYTES = min(int(os.getenv("OCR_ARTIFACT_MAX_BYTES", "134217728")), 128 * 1024 * 1024)
MAX_METADATA_BYTES = 16 * 1024 * 1024
logger = logging.getLogger("archivo.ocr")
_engine_lock = threading.Lock()
_processing_lock = threading.Lock()
_upload_lock = threading.Lock()


async def bounded_body(request, limit):
    body = bytearray()
    async for chunk in request.stream():
        if len(body) + len(chunk) > limit:
            raise HTTPException(status_code=413, detail='OCR_INPUT_LIMIT')
        body.extend(chunk)
    return bytes(body)


def bounded_request(lock):
    def decorate(function):
        @wraps(function)
        async def wrapper(*args, **kwargs):
            if not lock.acquire(blocking=False):
                raise HTTPException(status_code=503, detail='OCR_SERVICE_BUSY')
            task = asyncio.create_task(function(*args, **kwargs))
            try:
                return await asyncio.shield(task)
            except asyncio.CancelledError:
                # to_thread cancellation does not stop inference; retain admission until it ends.
                while not task.done():
                    try:
                        await asyncio.shield(task)
                    except asyncio.CancelledError:
                        continue
                # Retrieve an error without disclosing it or leaving an unobserved task.
                if not task.cancelled():
                    task.exception()
                raise
            finally:
                lock.release()
        return wrapper
    return decorate


class MetadataLimitError(ValueError):
    pass


@lru_cache(maxsize=1)
def get_engine() -> RapidOCR:
    # Misma familia usada por ImageTrans: detección PaddleOCR, clasificación de
    # orientación y reconocimiento latino PP-OCRv5 ejecutados localmente con ONNX.
    return RapidOCR(
        params={
            "Global.text_score": 0.45,
            "EngineConfig.onnxruntime.intra_op_num_threads": ORT_INTRA_THREADS,
            "EngineConfig.onnxruntime.inter_op_num_threads": ORT_INTER_THREADS,
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


def iter_render_pages(data: bytes, content_type: str, filename: str, start_page: int = 0):
    """Preflight every page/frame, then own at most the current decoded raster."""
    if is_pdf_source(data, content_type, filename):
        document = pdfium.PdfDocument(data)
        try:
            preflight_pdf(document)
            for index in range(start_page, len(document)):
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
            for index in range(start_page, count):
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


def recognize(data: bytes, content_type: str, filename: str, binary_pdf: bool = False, job=None, stop=None) -> dict:
    source_pdf = is_pdf_source(data, content_type, filename)
    output_limit = MAX_ARTIFACT_BYTES if binary_pdf else None
    builder = None if source_pdf else ImagePdfBuilder(PDF_DPI, output_limit)
    page_texts_by_page = []
    weighted_score = 0.0
    weight = total_lines = 0
    cached = job[0].checkpoints(job[1]) if job else []
    layout = []
    if source_pdf:
        layout.extend(cached)
        for page in cached:
            page_texts_by_page.append(page.get('texts', [b['text'] for b in page['blocks']]))
            scores = page.get('scores', [b.get('confidence', 0) for b in page['blocks']])
            for text, score in zip(page_texts_by_page[-1], scores):
                line_weight = max(1, len(text.strip()))
                weighted_score += float(score) * line_weight
                weight += line_weight
            total_lines += len(page_texts_by_page[-1])
    engine = get_engine()
    start_page = len(cached) if source_pdf else 0
    rendered = iter_render_pages(data, content_type, filename, start_page) if job else iter_render_pages(data, content_type, filename)
    with closing(rendered) as pages:
        for image in pages:
            if stop and stop.is_set():
                raise JobError('OCR_JOB_PAUSED')
            page_index = len(layout)
            if not source_pdf and page_index < len(cached):
                page_layout = cached[page_index]
                layout.append(page_layout)
                builder.add(image, page_layout)
                page_texts = page_layout.get('texts', [b['text'] for b in page_layout['blocks']])
                page_scores = page_layout.get('scores', [b.get('confidence', 0) for b in page_layout['blocks']])
                page_texts_by_page.append(page_texts)
                total_lines += len(page_texts)
                for text, score in zip(page_texts, page_scores):
                    line_weight = max(1, len(text.strip()))
                    weighted_score += float(score) * line_weight
                    weight += line_weight
                continue
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
            if job:
                page_layout.update(texts=page_texts, scores=[float(s) for s in page_scores])
                job[0].checkpoint(job[1], page_index, page_layout)
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
    if job:
        if stop and stop.is_set():
            raise JobError('OCR_JOB_PAUSED')
        job[0].exporting(job[1])
    layout = [{key: page[key] for key in ('width', 'height', 'blocks')} for page in layout]
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
@bounded_request(_processing_lock)
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
    except ArtifactError:
        try:
            return ArtifactResponse(get_job_store().open(artifact_id, x_artifact_capability))
        except JobError as error:
            raise HTTPException(status_code=404, detail="OCR_ARTIFACT_UNAVAILABLE") from error


@app.get("/health")
def health() -> dict:
    worker = getattr(app.state, 'job_worker', None)
    return {"status": "ok", "model": "PP-OCRv5-latin",
            "jobsReady": bool(worker and worker.thread and worker.thread.is_alive())}


@app.post("/ocr")
@bounded_request(_processing_lock)
async def ocr(
    request: Request,
    content_type: str = Header(default="application/octet-stream"),
    x_filename_base64: str | None = Header(default=None),
) -> dict:
    data = await bounded_body(request, MAX_BYTES)
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
@bounded_request(_processing_lock)
async def regenerate_pdf(request: Request) -> Response:
    # Bound the JSON envelope as well as the decoded original before rendering it.
    body = await bounded_body(request, MAX_BYTES * 2 + 12_000_000)
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
@bounded_request(_processing_lock)
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
# Durable job configuration deliberately excludes document names and secret capabilities.
@lru_cache(maxsize=1)
def engine_fingerprint():
    config = {'schema': 1, 'rapidocr': version('rapidocr'), 'onnxruntime': version('onnxruntime'),
              'model': 'PP-OCRv5-latin-mobile', 'textScore': 0.45,
              'dpi': PDF_DPI, 'pages': min(MAX_PAGES, 100), 'pixels': min(MAX_PIXELS, 250000000),
              'pypdf': version('pypdf'), 'intraOpThreads': ORT_INTRA_THREADS, 'interOpThreads': ORT_INTER_THREADS}
    model_root = Path(rapidocr.__file__).parent / 'models'
    digests = {}
    for name in ('ch_PP-OCRv5_det_mobile.onnx', 'ch_ppocr_mobile_v2.0_cls_mobile.onnx', 'latin_PP-OCRv5_rec_mobile.onnx'):
        digest = hashlib.sha256()
        with (model_root / name).open('rb') as handle:
            while chunk := handle.read(1024 * 1024):
                digest.update(chunk)
        digests[name] = digest.hexdigest()
    config['modelSha256'] = digests
    return hashlib.sha256(json.dumps(config, sort_keys=True).encode()).hexdigest()


@lru_cache(maxsize=1)
def get_job_store():
    return JobStore(get_artifact_store(), engine_fingerprint(), max_input=MAX_BYTES,
                    max_output=MAX_ARTIFACT_BYTES)


def source_page_count(data):
    # Actual signature, not uploaded MIME/name, determines durable source handling.
    if data.startswith(b'%PDF-'):
        document = pdfium.PdfDocument(data)
        try:
            preflight_pdf(document)
            return len(document)
        finally:
            document.close()
    with Image.open(io.BytesIO(data)) as image:
        if image.format not in {'PNG', 'JPEG', 'TIFF'}:
            raise ValueError('Unsupported image')
        count = getattr(image, 'n_frames', 1)
        if not 1 <= count <= min(MAX_PAGES, 100):
            raise ValueError('Page limit')
        pixels = 0
        for index in range(count):
            image.seek(index)
            pixels += image.width * image.height
            if pixels > min(MAX_PIXELS, 250000000):
                raise ValueError('Pixel limit')
        return count


class JobWorker:
    def __init__(self, store):
        self.store = store
        self.stop = threading.Event()
        self.thread = None

    def start(self):
        with self.store.lock:
            if getattr(self.store, 'worker_started', False):
                raise JobError('OCR_JOB_BUSY')
            self.store.worker_started = True
        self.thread = threading.Thread(target=self._run, name='ocr-jobs', daemon=False)
        self.thread.start()

    def close(self):
        self.stop.set()
        self.store.wake.set()
        if self.thread:
            self.thread.join()
        self.store.worker_started = False

    def _run(self):
        while not self.stop.is_set():
            if not _processing_lock.acquire(timeout=0.1):
                continue
            ident = None
            try:
                if self.stop.is_set():
                    continue
                ident = self.store.claim()
                if ident is not None:
                    source = self.store.source(ident)
                    result = recognize(source, 'application/pdf' if source.startswith(b'%PDF-') else 'application/octet-stream',
                                       'documento', True, (self.store, ident), self.stop)
                    self.store.complete(ident, result)
            except Exception as error:
                if ident is not None:
                    if isinstance(error, JobError) and error.code == 'OCR_JOB_PAUSED':
                        try:
                            self.store.requeue(ident)
                        except JobError:
                            self.stop.set()
                    else:
                        try:
                            self.store.fail(ident, error.code if isinstance(error, JobError) else 'OCR_PROCESSING_FAILED')
                        except JobError:
                            self.stop.set()
                    logger.warning(json.dumps({'event': 'job_interrupted', 'category': type(error).__name__,
                                               'code': error.code if isinstance(error, JobError) else 'OCR_PROCESSING_FAILED'}))
            finally:
                if ident is not None:
                    self.store.release(ident)
                _processing_lock.release()
            if ident is None:
                self.store.wake.wait(0.2)
                self.store.wake.clear()


def job_http_error(error):
    status = {'OCR_JOB_BUSY': 503, 'OCR_JOB_CONFLICT': 409, 'OCR_JOB_NOT_READY': 409,
              'OCR_JOB_LIMIT': 413, 'OCR_JOB_STORAGE_FAILED': 503, 'OCR_JOB_CORRUPT': 503}.get(error.code, 404)
    return HTTPException(status_code=status, detail=error.code)


@app.get('/v2/jobs/config')
def job_config():
    return {'engineFingerprint': engine_fingerprint(), 'maxInputBytes': min(MAX_BYTES, 25 * 1024 * 1024),
            'maxPages': min(MAX_PAGES, 100)}


@app.post('/v2/jobs/{job_id}', status_code=202)
@bounded_request(_upload_lock)
async def submit_job(job_id: str, request: Request,
                     x_job_capability: str | None = Header(default=None),
                     x_source_sha256: str | None = Header(default=None),
                     x_ocr_fingerprint: str | None = Header(default=None)):
    # Validate opaque identity and capability before accepting a body.
    if not re.fullmatch(r'[a-f0-9]{32}', job_id) or not isinstance(x_job_capability, str) or not re.fullmatch(r'[a-f0-9]{64}', x_job_capability):
        raise HTTPException(status_code=404, detail='OCR_JOB_UNAVAILABLE')
    if x_ocr_fingerprint != engine_fingerprint():
        raise HTTPException(status_code=409, detail='OCR_JOB_CONFIG_CHANGED')
    if not isinstance(x_source_sha256, str) or not re.fullmatch(r'[a-f0-9]{64}', x_source_sha256):
        raise HTTPException(status_code=400, detail='OCR_INPUT_HASH')
    body = bytearray()
    async for chunk in request.stream():
        if len(body) + len(chunk) > min(MAX_BYTES, 25 * 1024 * 1024):
            raise HTTPException(status_code=413, detail='OCR_INPUT_LIMIT')
        body.extend(chunk)
    source = bytes(body)
    if hashlib.sha256(source).hexdigest() != x_source_sha256:
        raise HTTPException(status_code=400, detail='OCR_INPUT_HASH')
    try:
        pages = await asyncio.to_thread(source_page_count, source)
        return await asyncio.to_thread(get_job_store().submit, job_id, x_job_capability, source, pages)
    except JobError as error:
        raise job_http_error(error) from error
    except (ValueError, OSError, pdfium.PdfiumError) as error:
        raise HTTPException(status_code=422, detail='OCR_INPUT_INVALID') from error


@app.get('/v2/jobs/{job_id}')
def job_status(job_id: str, x_job_capability: str | None = Header(default=None)):
    try:
        return get_job_store().status(job_id, x_job_capability)
    except JobError as error:
        raise job_http_error(error) from error


@app.get('/v2/jobs/{job_id}/result')
def job_result(job_id: str, x_job_capability: str | None = Header(default=None)):
    try:
        return get_job_store().result(job_id, x_job_capability)
    except JobError as error:
        raise job_http_error(error) from error


@app.delete('/v2/jobs/{job_id}')
def cancel_job(job_id: str, x_job_capability: str | None = Header(default=None)):
    try:
        return get_job_store().cancel(job_id, x_job_capability)
    except JobError as error:
        raise job_http_error(error) from error


@app.middleware('http')
async def private_job_responses(request, call_next):
    response = await call_next(request)
    if request.url.path.startswith('/v2/jobs'):
        response.headers['Cache-Control'] = 'private, no-store'
    return response


@app.delete('/v2/jobs/{job_id}/result', status_code=204)
def acknowledge_job(job_id: str, x_job_capability: str | None = Header(default=None)):
    try:
        get_job_store().acknowledge(job_id, x_job_capability)
        return Response(status_code=204, headers={'Cache-Control': 'private, no-store'})
    except JobError as error:
        raise job_http_error(error) from error
