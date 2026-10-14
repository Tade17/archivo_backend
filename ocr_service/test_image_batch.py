import base64
import io
import unittest
from unittest.mock import patch

from PIL import Image
import pypdfium2 as pdfium
import app


def encoded(color, format="PNG", frames=None):
    image = Image.new("RGB", (40, 60), color)
    output = io.BytesIO()
    image.save(output, format=format, save_all=frames is not None,
               append_images=frames or [])
    return {"fileBase64": base64.b64encode(output.getvalue()).decode("ascii")}


class ImageBatchTests(unittest.TestCase):
    def colors(self, data):
        document = pdfium.PdfDocument(data)
        try:
            colors = []
            for index in range(len(document)):
                page = document[index]
                try:
                    bitmap = page.render(scale=app.PDF_DPI / 72)
                    try:
                        colors.append(bitmap.to_pil().getpixel((5, 5))[:3])
                    finally:
                        bitmap.close()
                finally:
                    page.close()
            return colors
        finally:
            document.close()

    def test_received_order_not_filename_order(self):
        files = [encoded("red"), encoded("blue")]
        files[0]["filename"] = "z.png"
        files[1]["filename"] = "a.png"
        self.assertEqual(self.colors(app.combine_images(files)), [(255, 0, 0), (0, 0, 255)])

    def test_tiff_frames_follow_file_order(self):
        tiff = encoded("red", "TIFF", [Image.new("RGB", (40, 60), "green")])
        self.assertEqual(self.colors(app.combine_images([tiff, encoded("blue")])),
                         [(255, 0, 0), (0, 128, 0), (0, 0, 255)])

    def test_actual_image_content_not_declared_mime(self):
        image = encoded("red")
        image["contentType"] = "application/pdf"
        self.assertEqual(len(self.colors(app.combine_images([image]))), 1)

    def test_corrupt_and_pdf_payloads_are_rejected(self):
        for data in (b"not an image", b"%PDF-1.4", b""):
            with self.subTest(data=data), self.assertRaises(ValueError):
                app.combine_images([{"fileBase64": base64.b64encode(data).decode()}])

    def test_unsupported_image_is_rejected(self):
        with self.assertRaises(ValueError):
            app.combine_images([encoded("red", "GIF")])

    def test_count_limit_precedes_decode(self):
        with self.assertRaises(ValueError):
            app.combine_images([{}] * 101)

    def test_page_limit_is_shared_between_tiff_and_files(self):
        tiff = encoded("red", "TIFF", [Image.new("RGB", (40, 60), "green")])
        with patch.object(app, "MAX_PAGES", 2), self.assertRaises(ValueError):
            app.combine_images([tiff, encoded("blue")])

    def test_pixel_limit_precedes_copy(self):
        with patch.object(app, "MAX_PIXELS", 1), patch.object(Image.Image, "copy") as copy:
            with self.assertRaises(ValueError):
                app.combine_images([encoded("red")])
            copy.assert_not_called()

    def test_total_byte_limit(self):
        with patch.object(app, "IMAGE_BATCH_MAX_BYTES", 1), self.assertRaises(ValueError):
            app.combine_images([encoded("red")])

    def test_output_limit_during_write(self):
        with patch.object(app, "IMAGE_BATCH_MAX_BYTES", 1024), self.assertRaises(ValueError):
            app.combine_images([encoded("red")])

    def test_empty_or_invalid_envelope(self):
        for files in ([], None, "bad", [None], [{"fileBase64": "!"}]):
            with self.subTest(files=files), self.assertRaises(ValueError):
                app.combine_images(files)


if __name__ == "__main__":
    unittest.main()
