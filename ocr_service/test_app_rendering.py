import unittest
import io
from contextlib import closing
from types import SimpleNamespace
from unittest.mock import patch, Mock

from PIL import Image

import app
from searchable_pdf import build_pdf


class PdfRenderingTests(unittest.TestCase):
    def setUp(self):
        self.images = [Image.new("RGB", (100, 160), "red"),
                       Image.new("RGB", (140, 200), "blue")]
        self.pdf = build_pdf(self.images, [
            {"width": image.width, "height": image.height, "blocks": []}
            for image in self.images
        ])

    def test_pdfium_rendering_preserves_page_order_and_pixels(self):
        pages = app.render_pages(self.pdf, "application/pdf", "test.pdf")
        self.assertEqual(len(pages), 2)
        self.assertEqual(pages[0].getpixel((10, 10)), (255, 0, 0))
        self.assertEqual(pages[1].getpixel((10, 10)), (0, 0, 255))
        self.assertEqual(pages[0].mode, "RGB")
        for page in pages:
            page.close()

    def test_page_limit_is_enforced(self):
        with patch.object(app, "MAX_PAGES", 1):
            with self.assertRaisesRegex(ValueError, "máximo de 1 páginas"):
                app.render_pages(self.pdf, "application/pdf", "test.pdf")

    def test_pixel_limit_is_enforced(self):
        with patch.object(app, "MAX_PIXELS", 1):
            with self.assertRaisesRegex(ValueError, "píxeles"):
                app.render_pages(self.pdf, "application/pdf", "test.pdf")

    def test_iterator_closes_previous_raster_before_next_and_on_cancel(self):
        with closing(app.iter_render_pages(self.pdf, 'application/pdf', 'test.pdf')) as pages:
            first = next(pages)
            second = next(pages)
            with self.assertRaises(ValueError): first.getpixel((0, 0))
        with self.assertRaises(ValueError): second.getpixel((0, 0))

    def test_preflight_checks_all_pages_before_any_render(self):
        with patch.object(app, 'MAX_PIXELS', 1), patch.object(app.pdfium.PdfPage, 'render') as render:
            with self.assertRaises(ValueError): next(app.iter_render_pages(self.pdf, 'application/pdf', 'test.pdf'))
            render.assert_not_called()

    def test_recognition_uses_iterator_and_releases_on_engine_error(self):
        tracked = []
        original = app.iter_render_pages
        def tracking(*args):
            with closing(original(*args)) as pages:
                for image in pages:
                    tracked.append(image)
                    yield image
        def engine(_):
            if len(tracked) == 2:
                with self.assertRaises(ValueError): tracked[0].getpixel((0, 0))
                raise RuntimeError('synthetic inference failure')
            return SimpleNamespace(txts=None, scores=None, boxes=None)
        with patch.object(app, 'iter_render_pages', side_effect=tracking), \
                patch.object(app, 'render_pages', side_effect=AssertionError('Eager raster collection forbidden')), \
                patch.object(app, 'get_engine', return_value=engine):
            with self.assertRaises(RuntimeError): app.recognize(self.pdf, 'application/pdf', 'test.pdf', True)
        self.assertEqual(len(tracked), 2)
        for image in tracked:
            with self.assertRaises(ValueError): image.getpixel((0, 0))

    def test_tiff_frames_are_yielded_and_closed_one_at_a_time(self):
        output = io.BytesIO()
        self.images[0].save(output, format='TIFF', save_all=True, append_images=self.images[1:])
        with closing(app.iter_render_pages(output.getvalue(), 'image/tiff', 'test.tiff')) as pages:
            first = next(pages)
            self.assertEqual(first.getpixel((1, 1)), (255, 0, 0))
            second = next(pages)
            self.assertEqual(second.getpixel((1, 1)), (0, 0, 255))
            with self.assertRaises(ValueError): first.getpixel((0, 0))
        with self.assertRaises(ValueError): second.getpixel((0, 0))

    def test_document_handle_closes_after_inference_error(self):
        document = app.pdfium.PdfDocument(self.pdf)
        failing_engine = Mock(side_effect=RuntimeError('synthetic failure'))
        with patch.object(document, 'close', wraps=document.close) as close, \
                patch.object(app.pdfium, 'PdfDocument', return_value=document), \
                patch.object(app, 'get_engine', return_value=failing_engine):
            with self.assertRaises(RuntimeError): app.recognize(self.pdf, 'application/pdf', 'test.pdf', True)
            close.assert_called_once()


if __name__ == "__main__":
    unittest.main()
