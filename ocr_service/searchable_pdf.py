"""Preserve scanned pages and add a selectable, positioned Unicode text layer."""
import io
import math
import os
from functools import lru_cache

from PIL import Image
import pypdfium2 as pdfium
from pypdf import PdfReader, PdfWriter, Transformation
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
    return validate_layout_dimensions([(image.width, image.height) for image in pages], layout)


def validate_layout_dimensions(dimensions, layout: object) -> list[dict]:
    if not isinstance(layout, list) or len(layout) != len(dimensions):
        raise ValueError("La distribución debe contener todas las páginas del documento")
    count = chars = 0
    for (image_width, image_height), page in zip(dimensions, layout):
        if not isinstance(page, dict):
            raise ValueError("Página de distribución inválida")
        if page.get("width") != image_width or page.get("height") != image_height:
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
            if x < 0 or y < 0 or width <= 0 or height <= 0 or x + width > image_width + 1 or y + height > image_height + 1:
                raise ValueError("El bloque de texto está fuera de la página")
            count += 1
            chars += len(text)
        if count > MAX_BLOCKS or chars > MAX_TEXT_CHARS:
            raise ValueError("La distribución supera el límite de texto permitido")
    return layout


class BoundedPdfBuffer(io.BytesIO):
    def __init__(self, max_bytes: int):
        super().__init__()
        self.max_bytes = max_bytes

    def write(self, data):
        if self.tell() + len(data) > self.max_bytes:
            raise ValueError("El PDF combinado supera el tamaño máximo permitido")
        return super().write(data)


def draw_text_layer(pdf, page, page_width, page_height):
    font = text_font()
    x_scale, y_scale = page_width / page['width'], page_height / page['height']
    for block in page['blocks']:
        text = ' '.join(block['text'].split())
        if not text:
            continue
        height = block['height'] * y_scale
        font_size = max(0.1, height / 1.15)
        text_width = pdfmetrics.stringWidth(text, font, font_size)
        layer = pdf.beginText()
        layer.setTextRenderMode(3)
        layer.setFont(font, font_size)
        layer.setHorizScale(block['width'] * x_scale / max(text_width, 0.01) * 100)
        layer.setTextOrigin(block['x'] * x_scale,
                            page_height - (block['y'] + block['height']) * y_scale + height * 0.15)
        layer.textLine(text)
        pdf.drawText(layer)


class ImagePdfBuilder:
    """Encode one raster at a time; ReportLab still retains encoded PDF objects."""
    def __init__(self, dpi=220, max_output_bytes=None):
        self.output = BoundedPdfBuffer(max_output_bytes) if max_output_bytes is not None else io.BytesIO()
        self.pdf = canvas.Canvas(self.output, pageCompression=1)
        self.pdf.setTitle('Documento digitalizado con texto OCR')
        self.scale = 72 / dpi
        self.blocks = self.chars = 0

    def add(self, image, page):
        validate_layout_dimensions([(image.width, image.height)], [page])
        self.blocks += len(page['blocks'])
        self.chars += sum(len(block['text']) for block in page['blocks'])
        if self.blocks > MAX_BLOCKS or self.chars > MAX_TEXT_CHARS:
            raise ValueError('La distribución supera el límite de texto permitido')
        width, height = image.width * self.scale, image.height * self.scale
        self.pdf.setPageSize((width, height))
        self.pdf.drawImage(ImageReader(image), 0, 0, width=width, height=height)
        draw_text_layer(self.pdf, page, width, height)
        self.pdf.showPage()

    def finish(self):
        self.pdf.save()
        return self.output.getvalue()


def build_pdf(pages: list[Image.Image], layout: object, dpi: int = 220,
              max_output_bytes: int | None = None) -> bytes:
    validate_layout(pages, layout)
    builder = ImagePdfBuilder(dpi, max_output_bytes)
    for image, page in zip(pages, layout):
        builder.add(image, page)
    return builder.finish()


def overlay_pdf(original: bytes, layout: object, dpi: int = 220,
                max_output_bytes: int | None = None) -> bytes:
    """Merge only invisible text; preserve source boxes, rotation and image streams."""
    reader = PdfReader(io.BytesIO(original), strict=True)
    if reader.is_encrypted:
        raise ValueError('No se puede procesar un PDF protegido')
    dimensions = []
    document = pdfium.PdfDocument(original)
    try:
        if not 1 <= len(document) <= 100:
            raise ValueError('El PDF supera el máximo de 100 páginas o está vacío')
        pixels = 0
        for index in range(len(document)):
            page = document[index]
            try:
                width, height = page.get_size()
                raster = (math.ceil(width * dpi / 72), math.ceil(height * dpi / 72))
                pixels += raster[0] * raster[1]
                if pixels > 250_000_000:
                    raise ValueError('El documento supera el máximo de píxeles permitido')
                dimensions.append(raster)
            finally:
                page.close()
    finally:
        document.close()
    validate_layout_dimensions(dimensions, layout)
    if len(reader.pages) != len(layout):
        raise ValueError('El PDF y su distribución no coinciden')
    writer = PdfWriter(clone_from=reader)
    overlay_output = BoundedPdfBuffer(max_output_bytes) if max_output_bytes is not None else io.BytesIO()
    overlay = canvas.Canvas(overlay_output, pageCompression=1)
    transforms = []
    for source, page_layout in zip(writer.pages, layout):
        left = max(float(source.cropbox.left), float(source.mediabox.left))
        bottom = max(float(source.cropbox.bottom), float(source.mediabox.bottom))
        right = min(float(source.cropbox.right), float(source.mediabox.right))
        top = min(float(source.cropbox.top), float(source.mediabox.top))
        width, height = right - left, top - bottom
        rotation = source.rotation % 360
        if (not all(math.isfinite(value) for value in (left, bottom, right, top, width, height))
                or width <= 0 or height <= 0 or rotation not in {0, 90, 180, 270}):
            raise ValueError('La geometría de la página PDF no es válida')
        display_width, display_height = (height, width) if rotation in {90, 270} else (width, height)
        # Inversa del recorte y la rotación mostrados; no normalizar la rotación de origen.
        matrices = {0: (1, 0, 0, 1, left, bottom),
                    90: (0, 1, -1, 0, right, bottom),
                    180: (-1, 0, 0, -1, right, top),
                    270: (0, -1, 1, 0, left, top)}
        transforms.append(Transformation(matrices[rotation]))
        overlay.setPageSize((display_width, display_height))
        draw_text_layer(overlay, page_layout, display_width, display_height)
        overlay.showPage()
    overlay.save()
    stamp = PdfReader(io.BytesIO(overlay_output.getvalue()), strict=True)
    for source, text_page, transform in zip(writer.pages, stamp.pages, transforms):
        source.merge_transformed_page(text_page, transform, over=True, expand=False)
    output = BoundedPdfBuffer(max_output_bytes) if max_output_bytes is not None else io.BytesIO()
    writer.write(output)
    writer.close()
    return output.getvalue()
