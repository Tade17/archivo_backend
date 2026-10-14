import unittest
from unittest.mock import patch
import benchmark_ocr as benchmark


class BenchmarkTests(unittest.TestCase):
    def test_metrics_use_actual_elapsed_and_no_unmeasured_percentiles(self):
        result = benchmark.measurement(10, 2.0, 123, 100, 200, 'a' * 64)
        self.assertEqual(result['pagesPerMinute'], 300)
        self.assertEqual(result['wallSeconds'], 2.0)
        self.assertNotIn('p95', result)
        self.assertNotIn('speedup', result)

    def test_bounds_are_checked_before_corpus_allocation(self):
        with patch.object(benchmark.Image, 'new') as allocate:
            for count in (0, -1, 41, 100000):
                with self.assertRaises(ValueError):
                    benchmark.make_corpus(count)
            allocate.assert_not_called()

    def test_character_error_rate_is_ground_truth_based(self):
        self.assertEqual(benchmark.character_error_rate('PAGE 01', 'PAGE 01'), 0)
        self.assertGreater(benchmark.character_error_rate('PAGE 01', 'PAGE 02'), 0)
        self.assertEqual(benchmark.character_error_rate('ABC', ''), 1)

    def test_parser_has_no_private_document_input_option(self):
        parser = benchmark.parser()
        self.assertNotIn('--input', parser._option_string_actions)
        self.assertNotIn('--file', parser._option_string_actions)
        self.assertNotIn('--directory', parser._option_string_actions)

    def test_unknown_case_and_threads_are_refused(self):
        for mode, threads in [('private-file', 1), ('candidate', 0), ('candidate', 64)]:
            with self.assertRaises(ValueError):
                benchmark.validate_case(10, mode, threads)

    def test_output_names_cannot_escape_approved_output_mount(self):
        for name in ('../secret.json', '/tmp/test.json', 'C:/private/file.json'):
            with self.assertRaises(ValueError):
                benchmark.validate_output_name(name)

class EngineTuningTests(unittest.TestCase):
    def tearDown(self):
        benchmark.app.get_engine.cache_clear()
        benchmark.app.engine_fingerprint.cache_clear()

    def test_cpu_default_is_explicit_and_bounded(self):
        self.assertEqual(benchmark.app.ORT_INTRA_THREADS, 2)
        self.assertEqual(benchmark.app.ORT_INTER_THREADS, 1)
        with patch.object(benchmark.app, 'RapidOCR') as engine:
            benchmark.app.get_engine.cache_clear()
            benchmark.app.get_engine()
            params = engine.call_args.kwargs['params']
            self.assertEqual(params['EngineConfig.onnxruntime.intra_op_num_threads'], 2)
            self.assertEqual(params['EngineConfig.onnxruntime.inter_op_num_threads'], 1)

    def test_durable_fingerprint_includes_cpu_settings(self):
        benchmark.app.engine_fingerprint.cache_clear()
        original = benchmark.app.engine_fingerprint()
        with patch.object(benchmark.app, 'ORT_INTRA_THREADS', 1):
            benchmark.app.engine_fingerprint.cache_clear()
            changed = benchmark.app.engine_fingerprint()
        self.assertNotEqual(original, changed)

    def test_cpu_configuration_rejects_unbounded_values(self):
        for value in ('0', '-1', '3', '64', 'not-a-number'):
            with patch.dict(benchmark.app.os.environ, {'OCR_ORT_INTRA_THREADS': value}):
                with self.assertRaises(ValueError):
                    benchmark.app.cpu_threads('OCR_ORT_INTRA_THREADS', 2)
