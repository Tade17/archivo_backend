import unittest
from unittest.mock import patch

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


if __name__ == "__main__":
    unittest.main()
