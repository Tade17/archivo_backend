"""Opt-in local end-to-end check; creates and deletes only its own test upload."""
import copy
import io
import os
import time
from pathlib import Path

import requests
from pypdf import PdfReader


def main():
    api = os.getenv("OCR_TEST_API", "http://localhost:8080/api")
    session = requests.Session()
    login = session.post(api + "/auth/login", json={
        "correo": os.environ["OCR_TEST_EMAIL"],
        "password": os.environ["OCR_TEST_PASSWORD"],
    }, timeout=30)
    login.raise_for_status()
    user = login.json()
    session.headers["Authorization"] = "Bearer " + user["token"]
    sample = Path(__file__).resolve().parents[2] / "docs/ejemplos-ocr/documento-prueba-ocr.png"
    with sample.open("rb") as source:
        upload = session.post(api + "/documentos-digitales", data={
            "expedienteId": os.environ["OCR_TEST_EXPEDIENTE"],
            "tecnicoResponsableId": user["usuarioId"],
        }, files={"archivos": ("qa-pdf-ocr.png", source, "image/png")}, timeout=30)
    upload.raise_for_status()
    created = upload.json()[0]
    resource = api + "/documentos-digitales/" + created["id"]
    try:
        for _ in range(30):
            response = session.get(resource, timeout=30)
            response.raise_for_status()
            doc = response.json()
            if doc["pdfDisponible"]:
                break
            assert doc["ocrEstado"] != "ERROR", doc.get("ocrError")
            time.sleep(2)
        else:
            raise AssertionError("PDF generation did not finish in 60 seconds")
        original = session.get(resource + "/archivo", timeout=30)
        original.raise_for_status()
        assert original.content == sample.read_bytes(), "Original scan changed"
        layout_response = session.get(resource + "/ocr/layout", timeout=30)
        layout_response.raise_for_status()
        layout = layout_response.json()
        assert layout["layout"][0]["blocks"], "OCR returned no blocks"
        edited = copy.deepcopy(layout)
        marker = "CORRECCIONUNICAPRUEBA García Núñez"
        edited["layout"][0]["blocks"][0]["text"] = marker
        saved = session.put(resource + "/ocr/layout", json=edited, timeout=60)
        saved.raise_for_status()
        assert marker in saved.json()["ocrTexto"]
        assert saved.json()["ocrRevisado"] is True
        pdf = session.get(resource + "/pdf", timeout=30)
        pdf.raise_for_status()
        reader = PdfReader(io.BytesIO(pdf.content))
        assert marker in reader.pages[0].extract_text(), "Corrected PDF text missing"
        search = session.get(api + "/workspace/buscar", params={"texto": "CORRECCIONUNICAPRUEBA"}, timeout=30)
        search.raise_for_status()
        assert search.json()["totalElementos"] >= 1, "Search index did not update"
        stale = session.put(resource + "/ocr/layout", json=layout, timeout=60)
        assert stale.status_code == 400, (stale.status_code, stale.text)
        current = session.get(resource + "/ocr/layout", timeout=30).json()
        tampered = copy.deepcopy(current)
        tampered["layout"][0]["blocks"][0]["x"] += 10
        bad = session.put(resource + "/ocr/layout", json=tampered, timeout=30)
        assert bad.status_code == 400, (bad.status_code, bad.text)
        print("PASS: upload -> OCR/PDF -> correction -> Unicode PDF text -> search; stale version and geometry rejected; original preserved")
    finally:
        deleted = session.delete(resource, timeout=30)
        deleted.raise_for_status()
        print("Temporary QA document removed (existing documents untouched).")


if __name__ == "__main__":
    main()
