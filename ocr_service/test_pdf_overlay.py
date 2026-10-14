import ctypes
import hashlib
import io
import unittest
from unittest.mock import patch

from PIL import Image, ImageChops
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas
from pypdf import PdfReader, PdfWriter
from pypdf.generic import RectangleObject
import pypdfium2 as pdfium
import app
import searchable_pdf


def source_pdf(rotations=(0, 90, 180, 270), crop=True):
    image = Image.new('RGB', (120, 80), 'white')
    for y in range(80):
        for x in range(120):
            image.putpixel((x, y), ((x * 9) % 256, (y * 13) % 256, ((x + y) * 7) % 256))
    jpeg = io.BytesIO(); image.save(jpeg, format='JPEG', quality=85); image.close()
    output = io.BytesIO(); pdf = canvas.Canvas(output)
    for index in range(len(rotations)):
        width, height = 300 + index * 17, 240 + index * 13
        pdf.setPageSize((width, height))
        pdf.drawImage(ImageReader(io.BytesIO(jpeg.getvalue())), 0, 0, width=width, height=height)
        pdf.setFillColorRGB(1, 0, 0); pdf.rect(80, 70, 30, 20, fill=1, stroke=0)
        pdf.showPage()
    pdf.save(); writer = PdfWriter(clone_from=io.BytesIO(output.getvalue()))
    for page, rotation in zip(writer.pages, rotations):
        if crop:
            page.cropbox = RectangleObject((21, 31, float(page.mediabox.right) - 18, float(page.mediabox.top) - 20))
        if rotation:
            page.rotate(rotation)
    result = io.BytesIO(); writer.write(result)
    return result.getvalue()


def layout_for(data):
    images = app.render_pages(data, 'application/pdf', 'test.pdf')
    try:
        return [{'width': image.width, 'height': image.height, 'blocks': [
            {'id': '0', 'text': f'María Núñez página {index + 1}', 'x': 30, 'y': 50,
             'width': 240, 'height': 40}]} for index, image in enumerate(images)]
    finally:
        for image in images:
            image.close()


def image_streams(data):
    reader = PdfReader(io.BytesIO(data))
    return [[(str(obj.get_object().get('/Filter')), obj.get_object()._data)
             for obj in page.get('/Resources', {}).get('/XObject', {}).values()
             if obj.get_object().get('/Subtype') == '/Image'] for page in reader.pages]


class PdfOverlayTests(unittest.TestCase):
    def test_rotations_crop_offsets_unicode_and_visible_pixels(self):
        original = source_pdf(); original_hash = hashlib.sha256(original).digest()
        layout = layout_for(original)
        with patch.object(searchable_pdf, 'ImageReader', side_effect=AssertionError('PDF background must not be redrawn')):
            result = searchable_pdf.overlay_pdf(original, layout, app.PDF_DPI, 128 * 1024 * 1024)
        before, after = PdfReader(io.BytesIO(original)), PdfReader(io.BytesIO(result))
        self.assertEqual(len(after.pages), 4)
        self.assertEqual(image_streams(original), image_streams(result))
        self.assertEqual(hashlib.sha256(original).digest(), original_hash)
        source, derived = pdfium.PdfDocument(original), pdfium.PdfDocument(result)
        try:
            for index in range(4):
                with self.subTest(rotation=index * 90):
                    self.assertEqual(before.pages[index].mediabox, after.pages[index].mediabox)
                    self.assertEqual(before.pages[index].cropbox, after.pages[index].cropbox)
                    self.assertEqual(before.pages[index].rotation, after.pages[index].rotation)
                    self.assertIn(f'María Núñez página {index + 1}', after.pages[index].extract_text())
                    a, b = source[index], derived[index]
                    try:
                        ba, bb = a.render(scale=app.PDF_DPI / 72), b.render(scale=app.PDF_DPI / 72)
                        try:
                            ia, ib = ba.to_pil().convert('RGB'), bb.to_pil().convert('RGB')
                            self.assertEqual(ia.size, ib.size)
                            self.assertIsNone(ImageChops.difference(ia, ib).getbbox())
                            ia.close(); ib.close()
                            text = b.get_textpage()
                            try:
                                self.assertIn('María Núñez', text.get_text_bounded())
                                left, bottom, right, top = text.get_charbox(0)
                                dx, dy = ctypes.c_int(), ctypes.c_int()
                                pdfium.raw.FPDF_PageToDevice(b.raw, 0, 0, bb.width, bb.height, 0,
                                                            (left + right) / 2, (bottom + top) / 2,
                                                            ctypes.byref(dx), ctypes.byref(dy))
                                self.assertGreaterEqual(dx.value, 28)
                                self.assertLessEqual(dx.value, 275)
                                self.assertGreaterEqual(dy.value, 48)
                                self.assertLessEqual(dy.value, 93)
                            finally:
                                text.close()
                        finally:
                            ba.close(); bb.close()
                    finally:
                        a.close(); b.close()
        finally:
            source.close(); derived.close()

    def test_empty_overlay_preserves_background_streams_and_page_order(self):
        original = source_pdf((0, 90), crop=False)
        layout = layout_for(original)
        for page in layout: page['blocks'] = []
        result = searchable_pdf.overlay_pdf(original, layout, app.PDF_DPI, 128 * 1024 * 1024)
        self.assertEqual(image_streams(original), image_streams(result))
        self.assertEqual([page.mediabox for page in PdfReader(io.BytesIO(original)).pages],
                         [page.mediabox for page in PdfReader(io.BytesIO(result)).pages])

    def test_layout_and_output_limits_fail_closed(self):
        original = source_pdf((0,))
        layout = layout_for(original)
        with self.assertRaises(ValueError): searchable_pdf.overlay_pdf(original, [], app.PDF_DPI, 100000)
        with self.assertRaises(ValueError): searchable_pdf.overlay_pdf(original, layout, app.PDF_DPI, 10)
        layout[0]['width'] += 3
        with self.assertRaises(ValueError): searchable_pdf.overlay_pdf(original, layout, app.PDF_DPI, 100000)

    def test_original_media_origin_is_preserved(self):
        writer = PdfWriter(clone_from=io.BytesIO(source_pdf((270,), crop=False)))
        writer.pages[0].mediabox = RectangleObject((10, 20, 300, 240))
        writer.pages[0].cropbox = RectangleObject((31, 42, 270, 210))
        output = io.BytesIO(); writer.write(output); original = output.getvalue()
        result = searchable_pdf.overlay_pdf(original, layout_for(original), app.PDF_DPI, 100000)
        self.assertEqual(PdfReader(io.BytesIO(original)).pages[0].mediabox, PdfReader(io.BytesIO(result)).pages[0].mediabox)
        self.assertEqual(PdfReader(io.BytesIO(original)).pages[0].cropbox, PdfReader(io.BytesIO(result)).pages[0].cropbox)


if __name__ == '__main__': unittest.main()
