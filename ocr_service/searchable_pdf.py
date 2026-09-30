"""Preserve scanned pages and add a selectable, positioned Unicode text layer."""
import io
import math
import os
from functools import lru_cache

from PIL import Image
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

MAX_BLOCKS = 10000
MAX_TEXT_CHARS = 2_000_000


@lru_cache(maxsize=1)
def text_font() -> str:
    candidates = [
        os.getenv("OCR_PDF_FONT", ""),
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "C:/Windows/Fonts/arial.ttf",
    ]
    for path in candidates:
        if path and os.path.isfile(path):
            pdfmetrics.registerFont(TTFont("OCRUnicode", path))
            return "OCRUnicode"
    raise RuntimeError("No se encontró la fuente Unicode para generar el PDF")


def validate_layout(pages: list[Image.Image], layout: object) -> list[dict]:
    if not isinstance(layout, list) or len(layout) != len(pages):
        raise ValueError("La distribución debe contener todas las páginas del documento")
    count = chars = 0
    for image, page in zip(pages, layout):
        if not isinstance(page, dict):
            raise ValueError("Página de distribución inválida")
        if page.get("width") != image.width or page.get("height") != image.height:
            raise ValueError("Las dimensiones de la página no coinciden con el original")
        blocks = page.get("blocks")
        if not isinstance(blocks, list):
            raise ValueError("Los bloques de texto deben ser una lista")
        ids = set()
        for block in blocks:
            if not isinstance(block, dict):
                raise ValueError("Bloque de texto inválido")
            ident, text = block.get("id"), block.get("text")
            if not isinstance(ident, str) or not ident or ident in ids or len(ident) > 100:
                raise ValueError("Los identificadores de bloque deben ser únicos en cada página")
            ids.add(ident)
            if not isinstance(text, str) or len(text) > 20000:
                raise ValueError("Texto de bloque inválido o demasiado largo")
            if any(ord(character) < 32 and character not in "\n\t\r" for character in text):
                raise ValueError("El texto contiene caracteres de control inválidos")
            values = [block.get(key) for key in ("x", "y", "width", "height")]
            if any(isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) for v in values):
                raise ValueError("Coordenadas de bloque inválidas")
            x, y, width, height = values
            if x < 0 or y < 0 or width <= 0 or height <= 0 or x + width > image.width + 1 or y + height > image.height + 1:
                raise ValueError("El bloque de texto está fuera de la página")
            count += 1
            chars += len(text)
        if count > MAX_BLOCKS or chars > MAX_TEXT_CHARS:
            raise ValueError("La distribución supera el límite de texto permitido")
    return layout


def build_pdf(pages: list[Image.Image], layout: object, dpi: int = 220) -> bytes:
    layout = validate_layout(pages, layout)
    output = io.BytesIO()
    pdf = canvas.Canvas(output, pageCompression=1)
    pdf.setTitle("Documento digitalizado con texto OCR")
    font = text_font()
    scale = 72 / dpi
    for image, page in zip(pages, layout):
        page_width, page_height = image.width * scale, image.height * scale
        pdf.setPageSize((page_width, page_height))
        pdf.drawImage(ImageReader(image), 0, 0, width=page_width, height=page_height)
        for block in page["blocks"]:
            text = " ".join(block["text"].split())
            if not text:
                continue
            height = block["height"] * scale
            font_size = max(0.1, height / 1.15)
            text_width = pdfmetrics.stringWidth(text, font, font_size)
            layer = pdf.beginText()
            layer.setTextRenderMode(3)  # invisible glyphs remain selectable/searchable
            layer.setFont(font, font_size)
            layer.setHorizScale(block["width"] * scale / max(text_width, 0.01) * 100)
            layer.setTextOrigin(block["x"] * scale, page_height - (block["y"] + block["height"]) * scale + height * 0.15)
            layer.textLine(text)
            pdf.drawText(layer)
        pdf.showPage()
    pdf.save()
    return output.getvalue()
