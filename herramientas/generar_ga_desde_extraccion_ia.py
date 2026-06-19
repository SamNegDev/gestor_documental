#!/usr/bin/env python3
import argparse
import csv
import json
import re
import unicodedata
from copy import deepcopy
from datetime import datetime
from pathlib import Path
from xml.etree import ElementTree as ET


def text(value):
    if value is None:
        return ""
    return str(value).strip()


def clean_alnum(value):
    return re.sub(r"[^A-Za-z0-9]", "", text(value)).upper()


def load_result(path):
    outer = json.loads(Path(path).read_text(encoding="utf-8"))
    inner = json.loads(outer["resultadoJson"])
    return outer, inner


def field_value(node, *parts):
    current = node or {}
    for part in parts:
        if not isinstance(current, dict):
            return ""
        current = current.get(part, {})
    if isinstance(current, dict) and "valor" in current:
        return text(current.get("valor"))
    return text(current)


def leaves(node):
    for child in list(node):
        if len(list(child)) == 0:
            yield child
        else:
            yield from leaves(child)


def first(root, tag):
    return root.find(".//" + tag)


def set_tag(root, tag, value):
    node = first(root, tag)
    if node is not None:
        node.text = text(value)


def set_under(parent, tag, value):
    node = parent.find(".//" + tag)
    if node is not None:
        node.text = text(value)


def fill_group(root, group_name, values):
    group = first(root, group_name)
    if group is None or not isinstance(values, dict):
        return
    for key, value in values.items():
        if isinstance(value, dict):
            fill_nested(group, key, value)
        else:
            set_under(group, key, value)


def fill_nested(parent, _name, values):
    if not isinstance(values, dict):
        return
    for key, value in values.items():
        if isinstance(value, dict):
            fill_nested(parent, key, value)
        else:
            set_under(parent, key, value)


def parse_decimal(value):
    raw = text(value).replace(",", ".")
    match = re.search(r"\d+(?:\.\d+)?", raw)
    if not match:
        return None
    try:
        return float(match.group(0))
    except ValueError:
        return None


def parse_money(value):
    raw = text(value)
    if not raw:
        return None
    if "," in raw:
        raw = raw.replace(".", "").replace(",", ".")
    raw = re.sub(r"[^0-9.]", "", raw)
    if not raw:
        return None
    try:
        return float(raw)
    except ValueError:
        return None


def money11(value):
    number = parse_money(value)
    if number is None:
        return ""
    return str(max(0, round(number * 100))).zfill(11)


def scaled_number(value, width, scale):
    number = parse_decimal(value)
    if number is None:
        return ""
    scaled = round(number * scale)
    return str(max(0, int(scaled))).zfill(width)


def parse_date(value):
    raw = text(value)
    for fmt in ("%d/%m/%Y", "%Y-%m-%d", "%Y/%m/%d"):
        try:
            return datetime.strptime(raw, fmt).date()
        except ValueError:
            pass
    return None


def depreciation_percent(registration_date, reference_date):
    if registration_date is None:
        return None
    reference = reference_date or datetime.now().date()
    years = reference.year - registration_date.year
    if (reference.month, reference.day) < (registration_date.month, registration_date.day):
        years -= 1
    if years < 1:
        return 100
    if years < 2:
        return 84
    if years < 3:
        return 67
    if years < 4:
        return 56
    if years < 5:
        return 47
    if years < 6:
        return 39
    if years < 7:
        return 34
    if years < 8:
        return 28
    if years < 9:
        return 24
    if years < 10:
        return 19
    if years < 11:
        return 17
    if years < 12:
        return 13
    return 10


def year_from_date(value):
    parsed = parse_date(value)
    return str(parsed.year) if parsed else ""


def percent5(percent):
    if percent is None:
        return ""
    return str(max(0, round(float(percent) * 100))).zfill(5)


def valuation_period(value):
    raw = text(value)
    match = re.search(r"(\d{4})\s*[-/]\s*(\d{4})?", raw, re.IGNORECASE)
    if not match:
        return None, None
    start = int(match.group(1))
    end = int(match.group(2)) if match.group(2) else 9999
    return start, end


def valuation_kw(value):
    match = re.search(r"(\d+(?:[,.]\d+)?)\s*KW", text(value), re.IGNORECASE)
    return parse_decimal(match.group(1)) if match else None


def normalize_for_match(value):
    normalized = unicodedata.normalize("NFD", text(value))
    normalized = "".join(c for c in normalized if unicodedata.category(c) != "Mn")
    return re.sub(r"[^A-Z0-9 ]", " ", normalized.upper())


def vehicle_model_terms(brand, model):
    normalized_brand = normalize_for_match(brand)
    normalized_model = normalize_for_match(model).replace(normalized_brand, " ")
    terms = [term for term in normalized_model.split() if len(term) >= 2]
    return terms or [term for term in normalized_model.split() if term]


def ga_vehicle_model(value):
    normalized = unicodedata.normalize("NFD", text(value).upper())
    normalized = "".join(c for c in normalized if unicodedata.category(c) != "Mn")
    return normalized[:22]


def valuation_paths(base_dir, valuation_year, ca_valoracion):
    suffix = clean_alnum(ca_valoracion) or "GC"
    return [base_dir / f"vehiculos{valuation_year}{suffix}.txt"]


def select_valuation(trans, valuation_dir):
    year_node = first(trans, "ANYOVALORACION")
    ca_node = first(trans, "CAVALORACION")
    valuation_year = text(year_node.text if year_node is not None else "") or str(datetime.now().year)
    ca_valoracion = text(ca_node.text if ca_node is not None else "") or "GC"
    base_dir = Path(valuation_dir) if valuation_dir else Path(__file__).resolve().parent / "referencias"
    paths = valuation_paths(base_dir, valuation_year, ca_valoracion)

    brand_node = first(trans, "MARCA")
    model_node = first(trans, "MODELO")
    cylinder_node = first(trans, "CILINDRADA")
    power_node = first(trans, "POTENCIA")
    class_node = first(trans, "CLASE_VEHICULO")
    date_node = first(trans, "FECHA_PRIMERA_MATRICULACION")

    brand = text(brand_node.text if brand_node is not None else "")
    model = text(model_node.text if model_node is not None else "")
    vehicle_class = text(class_node.text if class_node is not None else "") or "T"
    terms = vehicle_model_terms(brand, model)
    registration_date = parse_date(date_node.text if date_node is not None else "")
    registration_year = registration_date.year if registration_date else None
    cylinder = parse_decimal(cylinder_node.text if cylinder_node is not None else "")
    if cylinder and cylinder > 10000:
        cylinder = cylinder / 100
    power = parse_decimal(power_node.text if power_node is not None else "")
    if power and power > 1000:
        power = power / 100

    candidates = []
    for source_priority, valuation_path in enumerate(paths):
        if not valuation_path.exists():
            continue
        with valuation_path.open("r", encoding="latin-1", errors="replace") as fh:
            for line_number, line in enumerate(fh, 1):
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 9:
                    continue
                tt, row_brand, row_model, features, cylinders, row_cylinder, cvf, first_year_value, code = parts[:9]
                if normalize_for_match(row_brand) != normalize_for_match(brand):
                    continue
                row_model_norm = normalize_for_match(row_model)
                if not all(term in row_model_norm for term in terms[:2]):
                    continue
                start, end = valuation_period(features)
                if registration_year and start and not (start <= registration_year <= end):
                    continue
                row_cylinder_value = parse_decimal(row_cylinder)
                if cylinder and row_cylinder_value and abs(cylinder - row_cylinder_value) > 25:
                    continue
                row_kw = valuation_kw(features)
                if power and row_kw and abs(power - row_kw) > 3:
                    continue
                row_value = parse_money(first_year_value)
                if row_value is None:
                    continue
                class_priority = 0 if text(tt).strip().upper() == vehicle_class else 1
                candidates.append({
                    "source_priority": source_priority,
                    "class_priority": class_priority,
                    "value": row_value,
                    "line": line_number,
                    "source": valuation_path.name,
                    "tt": text(tt),
                    "brand": row_brand,
                    "model": row_model,
                    "features": features,
                    "cylinder": row_cylinder,
                    "first_year_value": row_value,
                    "code": code,
                    "registration_date": registration_date,
                })

    candidates.sort(key=lambda item: (item["source_priority"], item["class_priority"], item["value"], item["line"]))
    return candidates[0] if candidates else None


def apply_valuation(trans, valuation_dir):
    selected = select_valuation(trans, valuation_dir)
    if not selected:
        return None
    valuation_type_map = {
        "C": "20",
        "T": "40",
        "M": "50",
        "S": "90",
    }
    selected_class = text(selected["tt"]).strip().upper()
    if selected_class:
        set_tag(trans, "CLASE_VEHICULO", selected_class)
        set_tag(trans, "TIPO_VEHICULO", valuation_type_map.get(selected_class, text(first(trans, "TIPO_VEHICULO").text if first(trans, "TIPO_VEHICULO") is not None else "")))
    set_tag(trans, "ID_VEHICULO", selected["code"])
    set_tag(trans, "MARCA", selected["brand"])
    set_tag(trans, "MODELO", ga_vehicle_model(selected["model"]))
    first_value = selected["first_year_value"]
    reg_date = selected["registration_date"]
    devengo = first(trans, "FECHA_DEVENGO")
    reference_date = parse_date(devengo.text if devengo is not None else "")
    percent = depreciation_percent(reg_date, reference_date)
    depreciated = first_value * percent / 100 if percent is not None else None
    set_tag(trans, "VALOR_FISCAL_PRIMER_A\u00d1O", money11(first_value))
    set_tag(trans, "PORCENTAJE_DEPRECIACION", percent5(percent))
    set_tag(trans, "VALOR_FISCAL", money11(depreciated))
    return selected


def normalize_vehicle_class(norm_vehicle, raw_vehicle):
    current_class = text(norm_vehicle.get("CLASE_VEHICULO"))
    current_type = text(norm_vehicle.get("TIPO_VEHICULO"))
    raw_type = field_value(raw_vehicle, "tipoVehiculo")
    raw_class = field_value(raw_vehicle, "claseVehiculo")
    descriptor = " ".join([
        text(norm_vehicle.get("MARCA")),
        text(norm_vehicle.get("MODELO")),
        raw_type,
        raw_class,
    ]).upper()

    valid_types = {"20", "40", "50", "90"}
    if current_class and current_type in valid_types:
        return current_class, current_type

    if "CICLOMOTOR" in descriptor or "SCOOTER" in descriptor:
        return "S", "90"
    if "MOTO" in descriptor or "MOTOCICLETA" in descriptor:
        return "M", "50"
    if "FURGON" in descriptor or "COMERCIAL" in descriptor or "MIXTO" in descriptor:
        return "C", "20"
    return "T", "40"


def current_doc_number(prefix, expediente_id, matricula):
    base = f"{prefix}{datetime.now():%Y%m%d%H%M%S}{clean_alnum(matricula)}{expediente_id or ''}"
    return clean_alnum(base).ljust(30, "X")[:30]


def load_interesados(path):
    if not path:
        return []
    tsv_path = Path(path)
    if not tsv_path.exists():
        return []
    with tsv_path.open("r", encoding="utf-8-sig", newline="") as fh:
        return list(csv.DictReader(fh, delimiter="\t"))


def normalize_siglas(value):
    raw = text(value).upper()
    if raw.isdigit():
        return raw
    if raw in {"C", "C/", "CL", "CALLE"}:
        return "6"
    if raw in {"AV", "AVDA", "AVENIDA"}:
        return "2"
    if raw in {"CTRA", "CARRETERA"}:
        return "7"
    if raw in {"CM", "CAMINO"}:
        return "50"
    return "6" if raw else ""


def fill_if_blank(root, tag, value):
    node = first(root, tag)
    if node is not None and not text(node.text) and text(value):
        node.text = text(value)


def set_if_present(root, tag, value):
    node = first(root, tag)
    if node is not None and text(value):
        node.text = text(value)


def split_person_name(full_name):
    parts = [part for part in text(full_name).upper().split() if part]
    if len(parts) >= 3:
        return " ".join(parts[:-2]), parts[-2], parts[-1]
    if len(parts) == 2:
        return parts[0], parts[1], ""
    if len(parts) == 1:
        return parts[0], "", ""
    return "", "", ""


def value_from_field(node, key):
    value = node.get(key, {}) if isinstance(node, dict) else {}
    if isinstance(value, dict):
        return text(value.get("valor"))
    return text(value)


def normalized_id(value):
    return clean_alnum(value)


MONTHS_EN = {
    "JAN": 1,
    "JANUARY": 1,
    "FEB": 2,
    "FEBRUARY": 2,
    "MAR": 3,
    "MARCH": 3,
    "APR": 4,
    "APRIL": 4,
    "MAY": 5,
    "JUN": 6,
    "JUNE": 6,
    "JUL": 7,
    "JULY": 7,
    "AUG": 8,
    "AUGUST": 8,
    "SEP": 9,
    "SEPT": 9,
    "SEPTEMBER": 9,
    "OCT": 10,
    "OCTOBER": 10,
    "NOV": 11,
    "NOVEMBER": 11,
    "DEC": 12,
    "DECEMBER": 12,
}


def format_date_parts(year, month, day, fallback):
    try:
        month_number = int(month) if str(month).isdigit() else MONTHS_EN.get(text(month).upper())
        parsed = datetime(int(year), int(month_number), int(day))
        return parsed.strftime("%d/%m/%Y")
    except (TypeError, ValueError):
        return text(fallback).upper()


def normalize_date(value):
    original = re.sub(r"\s+", " ", text(value))
    if not original:
        return ""
    month_first = re.fullmatch(
        r"(JAN(?:UARY)?|FEB(?:RUARY)?|MAR(?:CH)?|APR(?:IL)?|MAY|JUN(?:E)?|JUL(?:Y)?|AUG(?:UST)?|SEP(?:T|TEMBER)?|OCT(?:OBER)?|NOV(?:EMBER)?|DEC(?:EMBER)?)\s+(\d{1,2}),?\s+(\d{4})(?:\s+.*)?",
        original,
        flags=re.IGNORECASE,
    )
    if month_first:
        month, day, year = month_first.groups()
        return format_date_parts(year, month, day, original)
    day_first = re.fullmatch(
        r"(\d{1,2})\s+(JAN(?:UARY)?|FEB(?:RUARY)?|MAR(?:CH)?|APR(?:IL)?|MAY|JUN(?:E)?|JUL(?:Y)?|AUG(?:UST)?|SEP(?:T|TEMBER)?|OCT(?:OBER)?|NOV(?:EMBER)?|DEC(?:EMBER)?)\s+(\d{4})(?:\s+.*)?",
        original,
        flags=re.IGNORECASE,
    )
    if day_first:
        day, month, year = day_first.groups()
        return format_date_parts(year, month, day, original)
    raw = original.replace(".", "/").replace("-", "/")
    match = re.fullmatch(r"(\d{4})/(\d{1,2})/(\d{1,2})(?:[ T].*)?", raw)
    if match:
        year, month, day = match.groups()
        return format_date_parts(year, month, day, original)
    match = re.fullmatch(r"(\d{1,2})/(\d{1,2})/(\d{4})(?:\s+.*)?", raw)
    if match:
        day, month, year = match.groups()
        return format_date_parts(year, month, day, original)
    return original.upper()


def normalize_province(value, postal_code=""):
    raw = text(value).upper()
    cp = clean_alnum(postal_code)
    if "TENERIFE" in raw or raw == "38" or cp.startswith("38"):
        return "TF"
    if "PALMAS" in raw or raw == "35" or cp.startswith("35"):
        return "GC"
    return raw


def apply_dni_people(trans, dni_people):
    for person in dni_people:
        person_id = normalized_id(value_from_field(person, "dni"))
        if not person_id:
            continue
        suffix = ""
        for candidate in ("TRANSMITENTE", "ADQUIRENTE"):
            node = first(trans, "DNI_" + candidate)
            if node is not None and normalized_id(node.text) == person_id:
                suffix = candidate
                break
        if not suffix:
            continue

        fill_if_blank(trans, "SEXO_" + suffix, value_from_field(person, "sexo"))
        fill_if_blank(trans, "FECHA_NACIMIENTO_" + suffix, normalize_date(value_from_field(person, "fechaNacimiento")))
        address = person.get("direccion") or {}
        fill_if_blank(trans, "SIGLAS_DIRECCION_" + suffix, normalize_siglas(value_from_field(address, "siglas")))
        fill_if_blank(trans, "NOMBRE_VIA_DIRECCION_" + suffix, value_from_field(address, "nombreVia").upper())
        fill_if_blank(trans, "NUMERO_DIRECCION_" + suffix, value_from_field(address, "numero"))
        fill_if_blank(trans, "PISO_DIRECCION_" + suffix, value_from_field(address, "piso").upper())
        fill_if_blank(trans, "PUERTA_DIRECCION_" + suffix, value_from_field(address, "puerta").upper())
        postal_code = clean_alnum(value_from_field(address, "codigoPostal"))[:5]
        fill_if_blank(trans, "CP_" + suffix, postal_code)
        fill_if_blank(trans, "MUNICIPIO_" + suffix, value_from_field(address, "municipio").upper())
        fill_if_blank(trans, "PUEBLO_" + suffix, value_from_field(address, "pueblo").upper())
        fill_if_blank(trans, "PROVINCIA_" + suffix, normalize_province(value_from_field(address, "provincia"), postal_code))
        fill_if_blank(trans, "PAIS_" + suffix, "ESP")


def apply_interesados(trans, interesados):
    for interesado in interesados:
        rol = text(interesado.get("rol")).upper()
        if rol == "COMPRADOR":
            suffix = "ADQUIRENTE"
        elif rol in {"VENDEDOR", "TITULAR"}:
            suffix = "TRANSMITENTE"
        else:
            continue

        set_if_present(trans, "DNI_" + suffix, clean_alnum(interesado.get("dni")))
        full_name = text(interesado.get("nombre")).upper()
        nombre, apellido1, apellido2 = split_person_name(full_name)
        nombre = text(interesado.get("nombre_pila")).upper() or text(interesado.get("nombre_persona")).upper() or nombre
        apellido1 = text(interesado.get("apellido1")).upper() or apellido1
        apellido2 = text(interesado.get("apellido2")).upper() or apellido2
        set_if_present(trans, "NOMBRE_" + suffix, nombre)
        set_if_present(trans, "APELLIDO1_" + suffix, apellido1)
        set_if_present(trans, "APELLIDO2_" + suffix, apellido2)
        birth_date = text(interesado.get("fecha_nacimiento")) or text(interesado.get("fechaNacimiento"))
        set_if_present(trans, "FECHA_NACIMIENTO_" + suffix, normalize_date(birth_date))
        set_if_present(trans, "SEXO_" + suffix, text(interesado.get("sexo")).upper())
        fill_if_blank(trans, "SIGLAS_DIRECCION_" + suffix, normalize_siglas(interesado.get("tipo_via")))
        fill_if_blank(trans, "NOMBRE_VIA_DIRECCION_" + suffix, text(interesado.get("nombre_via")).upper())
        fill_if_blank(trans, "NUMERO_DIRECCION_" + suffix, text(interesado.get("numero")))
        fill_if_blank(trans, "PUEBLO_" + suffix, text(interesado.get("pueblo")).upper())
        fill_if_blank(trans, "CP_" + suffix, clean_alnum(interesado.get("codigo_postal"))[:5])
        fill_if_blank(trans, "MUNICIPIO_" + suffix, text(interesado.get("municipio")).upper())
        fill_if_blank(trans, "PROVINCIA_" + suffix, normalize_province(interesado.get("provincia"), interesado.get("codigo_postal")))
        fill_if_blank(trans, "PAIS_" + suffix, text(interesado.get("pais")).upper())


def apply_required_placeholders(trans):
    for suffix in ("TRANSMITENTE", "ADQUIRENTE"):
        fill_if_blank(trans, "SIGLAS_DIRECCION_" + suffix, "6")
        fill_if_blank(trans, "NOMBRE_VIA_DIRECCION_" + suffix, "REVISAR")
        fill_if_blank(trans, "NUMERO_DIRECCION_" + suffix, "0")
        fill_if_blank(trans, "MUNICIPIO_" + suffix, "SANTA CRUZ DE TENERIFE")
        fill_if_blank(trans, "PROVINCIA_" + suffix, "TF")
        fill_if_blank(trans, "CP_" + suffix, "38000")
        fill_if_blank(trans, "PAIS_" + suffix, "ESP")


def sync_registration_dates(trans):
    first_registration = first(trans, "FECHA_PRIMERA_MATRICULACION")
    registration = first(trans, "FECHA_MATRICULACION")
    reference = text(first_registration.text if first_registration is not None else "")
    if not reference:
        reference = text(registration.text if registration is not None else "")
    if reference:
        if registration is not None:
            registration.text = reference
        if first_registration is not None:
            first_registration.text = reference
        set_tag(trans, "A\u00d1O_FABRICACION", year_from_date(reference))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--template", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--expediente-id", default="")
    parser.add_argument("--tipo-tramite", default="TRASPASO")
    parser.add_argument("--interesados-tsv", default="")
    parser.add_argument("--placeholder-obligatorios", action="store_true")
    parser.add_argument("--valoraciones-dir", default="")
    args = parser.parse_args()

    outer, inner = load_result(args.input)
    norm = inner.get("formatoGaNormalizado") or {}
    consolidated = ((inner.get("bloqueConsolidacion") or {}).get("resultado") or {})
    raw_vehicle = consolidated.get("vehiculo") or {}
    preview = outer.get("preview") or {}

    today = datetime.now().strftime("%d/%m/%Y")
    matricula = text(preview.get("matricula")) or text((norm.get("DATOS_VEHICULO") or {}).get("MATRICULA"))
    expediente_id = args.expediente_id or text(preview.get("expedienteId"))
    doc_number = current_doc_number("IA", expediente_id, matricula)

    parser_xml = ET.XMLParser(encoding="iso-8859-1")
    tree = ET.parse(args.template, parser=parser_xml)
    root = tree.getroot()
    root.set("FechaCreacion", today)
    trans = first(root, "TRANSMISION")
    if trans is None:
        raise RuntimeError("La plantilla no contiene TRANSMISION")

    presenter = first(trans, "DATOS_PRESENTADOR")
    presenter_copy = deepcopy(presenter) if presenter is not None else None

    for leaf in leaves(trans):
        leaf.text = ""

    if presenter is not None and presenter_copy is not None:
        for original in leaves(presenter_copy):
            set_under(presenter, original.tag, original.text or "")

    for leaf in leaves(trans):
        if leaf.tag == "EXENTO_CADU_DOI_REP_TRANSMITENTE":
            leaf.text = "No"
        elif leaf.tag.startswith("EXENTO_CADU_DOI_"):
            leaf.text = "Si"

    for group in [
        "DATOS_TRANSMITENTE",
        "DATOS_REPRESENTANTE_TRANSMITENTE",
        "DATOS_ADQUIRENTE",
        "DATOS_VEHICULO",
        "DATOS_IMPUESTOS",
        "ACREDITACION_DERECHO",
        "ACREDITACION_FISCAL",
    ]:
        fill_group(trans, group, norm.get(group) or {})

    apply_interesados(trans, load_interesados(args.interesados_tsv))
    apply_dni_people(trans, ((inner.get("bloqueDni") or {}).get("resultado") or {}).get("personas") or [])
    if args.placeholder_obligatorios:
        apply_required_placeholders(trans)
    sync_registration_dates(trans)

    vehicle_norm = norm.get("DATOS_VEHICULO") or {}
    clase, tipo = normalize_vehicle_class(vehicle_norm, raw_vehicle)
    set_tag(trans, "CLASE_VEHICULO", clase)
    set_tag(trans, "TIPO_VEHICULO", tipo)
    raw_itv_code = clean_alnum(field_value(raw_vehicle, "claseVehiculo"))
    if raw_itv_code:
        set_tag(trans, "CODIGO_ITV_INDUSTRIA", raw_itv_code[:4])

    raw_cylinder = field_value(raw_vehicle, "cilindrada")
    if raw_cylinder:
        set_tag(trans, "CILINDRADA", scaled_number(raw_cylinder, 7, 100))
    raw_power = field_value(raw_vehicle, "potencia")
    if raw_power and clase not in {"M", "S"}:
        set_tag(trans, "POTENCIA", scaled_number(raw_power, 5, 100))
    elif clase in {"M", "S"}:
        set_tag(trans, "POTENCIA", "00000")

    devengo_node = first(trans, "FECHA_DEVENGO")
    anyo_valoracion_node = first(trans, "ANYOVALORACION")
    anyo_valoracion = text(anyo_valoracion_node.text if anyo_valoracion_node is not None else "")
    if not anyo_valoracion:
        anyo_valoracion = year_from_date(devengo_node.text if devengo_node is not None else "") or str(datetime.now().year)
    set_tag(trans, "CAVALORACION", "GC")
    set_tag(trans, "ANYOVALORACION", anyo_valoracion)
    selected_valuation = apply_valuation(trans, args.valoraciones_dir)

    set_tag(trans, "TIPO_TRANSFERENCIA", "1")
    set_tag(trans, "NOTIFICACION_PREVIA", "No")
    set_tag(trans, "NUMERO_EXPEDIENTE", "")
    set_tag(trans, "NUMERO_DOCUMENTO", doc_number)
    set_tag(trans, "NUMERO_PROFESIONAL", "00387")
    set_tag(trans, "FECHA_CREACION", today)
    set_tag(trans, "FECHA_PRESENTACION", today)
    set_tag(trans, "JEFATURA", "TF")
    set_tag(trans, "CAVALORACION", "GC")
    set_tag(trans, "TIPO_TASA", "4")
    set_tag(trans, "TASA", "")
    set_tag(trans, "IMPRESION_PERMISO_CIRCULACION", "No")
    set_tag(trans, "SUBTIPO_RELE", "STCCM")
    set_tag(trans, "MOTIVO_ITV", "P")
    set_tag(trans, "PROVINCIA_PRIMERA_MATRICULACION", "ND")
    set_tag(trans, "NUMERO_CILINDROS", "00")
    set_tag(trans, "MASA", "000000")
    set_tag(trans, "TARA", "000000")
    set_tag(trans, "PLAZAS", "000")
    set_tag(trans, "MODO_ADJUDICACION", "1")
    set_tag(trans, "SERVICIO_DESTINA", text(vehicle_norm.get("SERVICIO_DESTINA")) or "B00")
    set_tag(trans, "CAMBIO_SERVICIO", "No")
    set_tag(trans, "ID_AUTOLIQUIDACION", "No")
    set_tag(trans, "CODIGO_ELECTRONICO_TRANSFERENCIA", "")
    set_tag(trans, "IMPORTE_INGRESADO", "00000000000")
    set_tag(trans, "COMPLEMENTARIA", "No")
    set_tag(trans, "SUJETO_IGIC", "No")
    set_tag(trans, "VENDEDOR_HABITUAL", "No")
    set_tag(trans, "FORMA_PAGO", "I")
    set_tag(trans, "PROVINCIACET", "TF")
    set_tag(trans, "CET_ITP", "")

    procesar_620 = "1" if text((norm.get("ACREDITACION_FISCAL") or {}).get("MODELO_ITP")) or text((norm.get("DATOS_IMPUESTOS") or {}).get("BASE_IMPONIBLE")) else "0"
    trans.set("ProcesarTransmision", "1")
    trans.set("Procesar620", procesar_620)
    trans.set("Version", "1.0")

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    suffix = "_REVISION" if args.placeholder_obligatorios else ""
    output_path = output_dir / f"FORMATO_GA_EXP{expediente_id}_{clean_alnum(matricula) or 'SINMATRICULA'}_IA{suffix}.xml"
    ET.indent(tree, space="  ")
    tree.write(output_path, encoding="iso-8859-1", xml_declaration=True)

    control = consolidated.get("control") or {}
    summary = {
        "output": str(output_path),
        "expedienteId": expediente_id,
        "matricula": matricula,
        "numeroDocumento": doc_number,
        "fechaPresentacion": today,
        "tipoTransferencia": "1",
        "tipoTasa": "4",
        "tasaVacia": True,
        "caValoracion": "GC",
        "modeloCotejadoTablaVehiculos": bool(selected_valuation),
        "procesar620": procesar_620,
        "claseVehiculo": clase,
        "tipoVehiculo": tipo,
        "confianzaGlobal": control.get("confianzaGlobal"),
        "requiereRevisionHumana": control.get("requiereRevisionHumana"),
        "camposDudosos": len(control.get("camposDudosos") or []),
        "placeholdersObligatorios": bool(args.placeholder_obligatorios),
        "valoracionSeleccionada": {
            "marca": selected_valuation.get("brand"),
            "modelo": selected_valuation.get("model"),
            "valorFiscalPrimerAnyo": selected_valuation.get("first_year_value"),
            "codigo": selected_valuation.get("code"),
            "tabla": selected_valuation.get("source"),
        } if selected_valuation else None,
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
