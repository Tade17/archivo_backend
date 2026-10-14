"""Bounded synthetic, single-process OCR microbenchmark; never accepts user files."""
import argparse
import base64
import hashlib
import io
import json
import math
import re
import resource
import time
from contextlib import closing
from pathlib import Path
from unittest.mock import patch

import numpy as np
import pypdfium2 as pdfium
from PIL import Image, ImageDraw, ImageFont
from pypdf import PdfReader
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas
import app
from searchable_pdf import build_pdf

WIDTH, HEIGHT = 1000, 1400
MAX_SYNTHETIC_PAGES = 40


def validate_case(pages, mode, threads):
    if type(pages) is not int or not 1 <= pages <= MAX_SYNTHETIC_PAGES:
        raise ValueError('Benchmark page bound')
    if mode not in {'baseline', 'candidate', 'image-batch'} or threads not in {-1, 1, 2}:
        raise ValueError('Benchmark case unsupported')
    if mode == 'image-batch' and pages != 3:
        raise ValueError('Image batch is exactly three ordered images')


def validate_output_name(name):
    if not isinstance(name, str) or not re.fullmatch(r'[a-z0-9_-]{1,80}\.json', name):
        raise ValueError('Benchmark output name unsupported')


def make_corpus(pages):
    if type(pages) is not int or not 1 <= pages <= MAX_SYNTHETIC_PAGES:
        raise ValueError('Benchmark page bound')
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 40)
    source, expected, jpegs = io.BytesIO(), [], []
    writer = canvas.Canvas(source, pagesize=(WIDTH * 72 / app.PDF_DPI, HEIGHT * 72 / app.PDF_DPI),
                           invariant=1, pageCompression=1)
    for index in range(pages):
        noise = np.random.default_rng(20261014 + index).normal(245, 3, (HEIGHT, WIDTH)).clip(0, 255).astype('uint8')
        image = Image.fromarray(noise).convert('RGB')
        try:
            lines = [f'PAGE {index + 1:02d}', 'ARCHIVO MUNICIPAL', f'REGISTRO SINTETICO {index + 1:04d}']
            draw = ImageDraw.Draw(image)
            for y, text in zip((130, 260, 390), lines):
                draw.text((70, y), text, font=font, fill='black')
            encoded = io.BytesIO(); image.save(encoded, format='JPEG', quality=70, optimize=False)
            data = encoded.getvalue(); jpegs.append(data); expected.append(' '.join(lines))
            writer.drawImage(ImageReader(io.BytesIO(data)), 0, 0, width=WIDTH * 72 / app.PDF_DPI,
                             height=HEIGHT * 72 / app.PDF_DPI)
            writer.showPage()
        finally:
            image.close()
    writer.save()
    data = source.getvalue()
    if len(data) > min(app.MAX_BYTES, 25 * 1024 * 1024):
        raise ValueError('Synthetic source exceeds production admission')
    return data, expected, jpegs


def normalized(text):
    return ' '.join(text.upper().split())


def character_error_rate(expected, actual):
    expected, actual = normalized(expected), normalized(actual)
    if not expected:
        return 0 if not actual else 1
    if max(len(expected), len(actual)) > 10000:
        raise ValueError('Synthetic accuracy bound')
    row = list(range(len(actual) + 1))
    for index, left in enumerate(expected, 1):
        current = [index]
        for offset, right in enumerate(actual, 1):
            current.append(min(current[-1] + 1, row[offset] + 1, row[offset - 1] + (left != right)))
        row = current
    return row[-1] / len(expected)


def measurement(pages, elapsed, peak_rss, input_bytes, output_bytes, source_hash):
    if not math.isfinite(elapsed) or elapsed <= 0:
        raise ValueError('No positive measured duration')
    return {'pages': pages, 'wallSeconds': round(elapsed, 6), 'pagesPerMinute': round(60 * pages / elapsed, 3),
            'maxRSSKiB': peak_rss, 'inputBytes': input_bytes, 'outputBytes': output_bytes,
            'outputInputRatio': round(output_bytes / input_bytes, 4), 'sourceSha256': source_hash}


def make_engine(threads):
    original = app.RapidOCR
    settings = {}
    def factory(params):
        settings.update(params)
        settings['EngineConfig.onnxruntime.intra_op_num_threads'] = threads
        settings['EngineConfig.onnxruntime.inter_op_num_threads'] = -1 if threads == -1 else 1
        return original(params=settings)
    app.get_engine.cache_clear()
    started = time.perf_counter()
    with patch.object(app, 'RapidOCR', side_effect=factory):
        engine = app.get_engine()
    for module in (engine.text_det, engine.text_cls, engine.text_rec):
        options = module.session.session.get_session_options()
        if options.intra_op_num_threads != (0 if threads == -1 else threads) or options.inter_op_num_threads != (0 if threads == -1 else 1):
            raise ValueError('Requested CPU thread settings were not applied')
    return engine, time.perf_counter() - started


def image_streams(data):
    result = []
    for page in PdfReader(io.BytesIO(data)).pages:
        streams = []
        for value in page['/Resources'].get('/XObject', {}).values():
            obj = value.get_object()
            if obj.get('/Subtype') == '/Image':
                streams.append(hashlib.sha256(obj._data).hexdigest())
        result.append(streams)
    return result


def visible_samples_equal(source, output, pages):
    documents = [pdfium.PdfDocument(data) for data in (source, output)]
    try:
        for index in sorted({0, pages - 1}):
            images = []
            for document in documents:
                page = document[index]
                try:
                    bitmap = page.render(scale=1)
                    try:
                        image = bitmap.to_pil().convert('RGB')
                        images.append((image.size, image.tobytes()))
                        image.close()
                    finally:
                        bitmap.close()
                finally:
                    page.close()
            if images[0] != images[1]:
                return False
        return True
    finally:
        for document in documents:
            document.close()


def run_case(pages, mode, threads):
    validate_case(pages, mode, threads)
    build_start = time.perf_counter()
    source, expected, jpegs = make_corpus(pages)
    corpus_seconds = time.perf_counter() - build_start
    source_hash = hashlib.sha256(source).hexdigest()
    engine, startup_seconds = make_engine(threads)
    with Image.open(io.BytesIO(jpegs[0])) as image:
        rgb = image.convert('RGB')
        try:
            warm_start = time.perf_counter(); engine(np.asarray(rgb)); warmup_seconds = time.perf_counter() - warm_start
        finally:
            rgb.close()
    start, cpu_start = time.perf_counter(), time.process_time()
    retained = []
    try:
        if mode == 'baseline':
            # Explicit pre-streaming-style eager rasters and background redraw, same current engine/layout.
            retained = app.render_pages(source, 'application/pdf', 'synthetic.pdf')
            def retained_iterator(*_):
                yield from retained
            def raster_export(_data, layout, dpi, limit):
                return build_pdf(retained, layout, dpi, max_output_bytes=limit)
            with patch.object(app, 'iter_render_pages', side_effect=retained_iterator), patch.object(app, 'overlay_pdf', side_effect=raster_export):
                result = app.recognize(source, 'application/pdf', 'synthetic.pdf', True)
        elif mode == 'image-batch':
            combined = app.combine_images([{'fileBase64': base64.b64encode(data).decode('ascii')} for data in jpegs])
            result = app.recognize(combined, 'application/pdf', 'synthetic.pdf', True)
        else:
            result = app.recognize(source, 'application/pdf', 'synthetic.pdf', True)
        elapsed, cpu_seconds = time.perf_counter() - start, time.process_time() - cpu_start
        peak = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
        output = result['pdfBytes']
        layout_text = [normalized(' '.join(block['text'] for block in page['blocks'])) for page in result['layout']]
        labels = [f'PAGE {index + 1:02d}' in text for index, text in enumerate(layout_text)]
        cer = [character_error_rate(truth, text) for truth, text in zip(expected, layout_text)]
        if len(layout_text) != pages or len(PdfReader(io.BytesIO(output)).pages) != pages:
            raise ValueError('Synthetic page count mismatch')
        measured = measurement(pages, elapsed, peak, len(source), len(output), source_hash)
        measured.update({'status': 'ok', 'mode': mode, 'intraOpThreads': threads, 'interOpThreads': -1 if threads == -1 else 1,
                         'dpi': app.PDF_DPI, 'rasterSize': [WIDTH, HEIGHT], 'sourceBuildSeconds': round(corpus_seconds, 6),
                         'modelStartupSeconds': round(startup_seconds, 6), 'warmupSeconds': round(warmup_seconds, 6),
                         'cpuSeconds': round(cpu_seconds, 6), 'engineModelFingerprint': app.engine_fingerprint(),
                         'pageLabelsCorrect': sum(labels), 'pageOrderLabelsVerified': all(labels),
                         'meanCharacterErrorRate': round(sum(cer) / len(cer), 6),
                         'sourceHashUnchanged': hashlib.sha256(source).hexdigest() == source_hash,
                         'compressedImageStreamsPreserved': image_streams(source) == image_streams(output) if mode != 'image-batch' else None,
                         'firstLastVisiblePixelsEqual': visible_samples_equal(source, output, pages) if mode != 'image-batch' else None,
                         'scope': 'synthetic local processing only; excludes HTTP, durable checkpoints, Java/database/frontend'})
        measured['maxRSSKiB'] = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
        return measured
    finally:
        for image in retained:
            image.close()


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument('--pages', type=int, choices=(1, 3, 10, 40), required=True)
    result.add_argument('--mode', choices=('baseline', 'candidate', 'image-batch'), required=True)
    result.add_argument('--threads', type=int, choices=(-1, 1, 2), default=-1)
    result.add_argument('--output', required=True, help='JSON basename under the /results output mount')
    return result


def main():
    args = parser().parse_args(); validate_output_name(args.output)
    try:
        result = run_case(args.pages, args.mode, args.threads)
    except Exception as error:
        result = {'status': 'failed', 'mode': args.mode, 'pages': args.pages, 'intraOpThreads': args.threads,
                  'category': type(error).__name__}
    output = Path('/results') / args.output
    body = json.dumps(result, allow_nan=False, sort_keys=True, indent=2)
    if len(body.encode()) > 16384:
        raise ValueError('Benchmark output bound')
    output.write_text(body + '\n')
    print(json.dumps(result, allow_nan=False))
    if result['status'] != 'ok':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
