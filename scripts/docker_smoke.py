"""Verifica a API real e a persistencia do volume PostgreSQL com Docker Compose.

Executar depois de `docker compose up --build -d` em um ambiente de testes.
O script cria uma empresa propria e recria db/app preservando o volume.
"""
import json
import os
import subprocess
import time
import uuid
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

BASE = os.environ.get("SMOKE_BASE_URL", "http://127.0.0.1:8080")
TOKEN = None


def call(method, path, body=None, expected=200, authenticated=True):
    headers = {"Content-Type": "application/json"}
    if authenticated and TOKEN:
        headers["Authorization"] = "Bearer " + TOKEN
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = Request(BASE + "/api" + path, data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=20) as response:
            status, raw = response.status, response.read()
    except HTTPError as error:
        status, raw = error.code, error.read()
    assert status == expected, f"{method} {path}: esperado {expected}, recebido {status}"
    return json.loads(raw) if raw else None


def ready():
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        try:
            health = call("GET", "/health", authenticated=False)
            if health.get("status") == "UP" and health.get("database") == "UP":
                return
        except (URLError, OSError, AssertionError):
            pass
        time.sleep(2)
    raise RuntimeError("Aplicacao/banco nao ficaram saudaveis em 180 segundos.")


def run():
    global TOKEN
    ready()
    slug = "smoke-" + uuid.uuid4().hex[:12]
    credentials = {"slug": slug, "email": "smoke@example.com", "password": "Smoke-AgendaPro-2026!"}
    registered = call("POST", "/auth/register", {
        **credentials, "companyName": "Docker Smoke Test", "timezone": "UTC", "name": "Smoke Test"
    }, expected=201, authenticated=False)
    TOKEN = registered["accessToken"]
    tenant_id = registered["tenantId"]
    client = call("POST", "/clients", {"name": "Cliente persistente"}, expected=201)
    professional = call("POST", "/professionals", {"name": "Profissional de teste"}, expected=201)
    service = call("POST", "/services", {"name": "Consulta", "durationMinutes": 30, "price": 100}, expected=201)
    call("PUT", f"/professionals/{professional['id']}/services", [service["id"]])
    date = (datetime.now(timezone.utc) + timedelta(days=2)).date()
    call("PUT", f"/professionals/{professional['id']}/working-hours", [
        {"dayOfWeek": date.isoweekday(), "startTime": "09:00", "endTime": "17:00"}
    ])
    slots = call("GET", f"/availability?professionalId={professional['id']}&serviceId={service['id']}&date={date}")
    assert slots, "Nenhum horario oferecido na jornada de teste."
    body = {"professionalId": professional["id"], "clientId": client["id"],
            "serviceId": service["id"], "startsAt": slots[0]["startsAt"], "notes": "Verificacao Docker"}
    appointment = call("POST", "/appointments", body, expected=201)
    call("POST", "/appointments", body, expected=409)
    call("PATCH", f"/appointments/{appointment['id']}/status", {"status": "CONFIRMED"})
    print("OK: saude, cadastro, login, disponibilidade, reserva e conflito no container.")

    # Remove containers e rede, mantendo o volume nomeado. Apenas no ambiente de testes.
    subprocess.run(["docker", "compose", "down"], check=True, timeout=120)
    subprocess.run(["docker", "compose", "up", "--no-build", "-d", "db", "app"], check=True, timeout=120)
    ready()
    TOKEN = call("POST", "/auth/login", credentials, authenticated=False)["accessToken"]
    assert call("GET", "/auth/me")["tenantId"] == tenant_id
    assert call("GET", f"/clients/{client['id']}")["name"] == "Cliente persistente"
    saved = call("GET", f"/appointments/{appointment['id']}")
    assert saved["status"] == "CONFIRMED"
    assert saved["clientId"] == client["id"]
    assert len(call("GET", f"/appointments/{appointment['id']}/events")) == 2
    call("POST", "/appointments", body, expected=409)
    print("OK: empresa, senha, cliente, reserva, historico e bloqueio de horario preservados apos recriar db/app.")

    second = call("POST", "/auth/register", {
        "companyName": "Outra empresa", "slug": "other-" + uuid.uuid4().hex[:12], "timezone": "UTC",
        "name": "Outra pessoa", "email": "other@example.com", "password": "Smoke-AgendaPro-2026!"
    }, expected=201, authenticated=False)
    TOKEN = second["accessToken"]
    call("GET", f"/clients/{client['id']}", expected=404)
    call("GET", f"/appointments/{appointment['id']}", expected=404)
    print("OK: isolamento entre empresas no ambiente Docker/PostgreSQL.")


if __name__ == "__main__":
    if not os.environ.get("COMPOSE_PROJECT_NAME", "").startswith("agendapro-test"):
        raise SystemExit("Use um projeto Compose isolado: COMPOSE_PROJECT_NAME=agendapro-test. O teste recria db/app.")
    run()
