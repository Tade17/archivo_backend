import io
import unittest

from PIL import Image
from PIL import ImageChops, ImageDraw
import pypdfium2 as pdfium
from pypdf import PdfReader

from searchable_pdf import build_pdf


class SearchablePdfTests(unittest.TestCase):
    def setUp(self):
        self.pages = [Image.new("RGB", (1000, 1400), "white"), Image.new("RGB", (1200, 1600), "white")]
        self.layout = [
            {"width": 1000, "height": 1400, "blocks": [
                {"id": "0", "text": "María Núñez - San José", "x": 100, "y": 200, "width": 600, "height": 40},
            ]},
            {"width": 1200, "height": 1600, "blocks": [
                {"id": "0", "text": "OFICIO N.° 045-2026 - S/ 12,450.75", "x": 120, "y": 300, "width": 900, "height": 50},
            ]},
        ]

    def test_unicode_text_and_page_coordinates_survive_pdf(self):
        reader = PdfReader(io.BytesIO(build_pdf(self.pages, self.layout)))
        self.assertEqual(len(reader.pages), 2)
        self.assertIn("María Núñez - San José", reader.pages[0].extract_text())
        self.assertIn("OFICIO N.° 045-2026 - S/ 12,450.75", reader.pages[1].extract_text())
        self.assertAlmostEqual(float(reader.pages[1].mediabox.width), 1200 * 72 / 220, places=3)
        positions = []
        reader.pages[1].extract_text(visitor_text=lambda text, cm, tm, font, size: positions.append((text, tm.copy())))
        text, transform = next(item for item in positions if "OFICIO" in item[0])
        self.assertAlmostEqual(transform[4], 120 * 72 / 220, places=3)
        self.assertAlmostEqual(transform[5], (1600 - 350 + 7.5) * 72 / 220, places=3)

    def test_corrected_text_replaces_old_text_and_can_be_cleared(self):
        self.layout[0]["blocks"][0]["text"] = "CORREGIDO - García"
        self.layout[1]["blocks"][0]["text"] = ""
        reader = PdfReader(io.BytesIO(build_pdf(self.pages, self.layout)))
        self.assertIn("CORREGIDO - García", reader.pages[0].extract_text())
        self.assertNotIn("Núñez", reader.pages[0].extract_text())
        self.assertEqual(reader.pages[1].extract_text(), "")

    def test_invisible_layer_preserves_visible_scan(self):
        image = self.pages[0]
        ImageDraw.Draw(image).text((100, 200), "Original scanned document", fill="black")
        data = build_pdf([image], self.layout[:1])
        with pdfium.PdfDocument(data) as document:
            bitmap = document[0].render(scale=220 / 72)
            rendered = bitmap.to_pil().convert("RGB")
            # PDF rendering rounds dimensions; compare the original pixel area.
            rendered = rendered.crop((0, 0, image.width, image.height))
            difference = ImageChops.difference(image, rendered)
            self.assertIsNone(difference.getbbox())

    def test_layout_must_match_original_and_stay_inside_page(self):
        with self.assertRaises(ValueError):
            build_pdf(self.pages, self.layout[:1])
        self.layout[0]["blocks"][0]["x"] = 900
        with self.assertRaises(ValueError):
            build_pdf(self.pages, self.layout)

    def test_nonfinite_coordinates_and_duplicate_ids_rejected(self):
        self.layout[0]["blocks"][0]["x"] = float("nan")
        with self.assertRaises(ValueError):
            build_pdf(self.pages, self.layout)
        self.layout[0]["blocks"][0]["x"] = 100
        self.layout[0]["blocks"].append(self.layout[0]["blocks"][0].copy())
        with self.assertRaises(ValueError):
            build_pdf(self.pages, self.layout)


if __name__ == "__main__":
    unittest.main()
