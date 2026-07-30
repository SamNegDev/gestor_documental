(() => {
  "use strict";

  const PAYLOAD_KIND = "GESTOR_DOCUMENTAL_JUSTIFICANTE_V1";
  const PANEL_ID = "gestor-documental-thempus";
  const AUTO_FLAG = "gestor-documental-autofill";
  const CREATE_HASH = "#gestor-documental=nuevo";
  const STORAGE_KEY = "gestorDocumentalThempusPayload";

  const normalize = (value) => String(value || "").trim().toLocaleUpperCase("es-ES");

  if (location.hostname === "app.gestoriacn.com") {
    window.addEventListener("gestor-documental:prepare-thempus", async (event) => {
      const { requestId, payload } = event.detail || {};
      if (!requestId || payload?.kind !== PAYLOAD_KIND) return;
      try {
        await chrome.storage.local.set({ [STORAGE_KEY]: payload });
        window.dispatchEvent(new CustomEvent("gestor-documental:thempus-ready", { detail: { requestId, ok: true } }));
      } catch (error) {
        window.dispatchEvent(new CustomEvent("gestor-documental:thempus-ready", { detail: { requestId, ok: false, message: error?.message } }));
      }
    });
    return;
  }

  async function readPayload() {
    const stored = await chrome.storage.local.get(STORAGE_KEY);
    let payload = stored[STORAGE_KEY];
    if (!payload) payload = JSON.parse(await navigator.clipboard.readText());
    if (payload?.kind !== PAYLOAD_KIND) {
      throw new Error("El portapapeles no contiene datos de un justificante.");
    }
    return payload;
  }

  async function hasFreshPayload(maxAgeMs = 10 * 60 * 1000) {
    const stored = await chrome.storage.local.get(STORAGE_KEY);
    const payload = stored[STORAGE_KEY];
    if (payload?.kind !== PAYLOAD_KIND) return false;
    const createdAt = Date.parse(payload.createdAt);
    return Number.isFinite(createdAt) && Date.now() - createdAt <= maxAgeMs;
  }

  function createPanel(message, withButton = false) {
    document.getElementById(PANEL_ID)?.remove();
    const panel = document.createElement("aside");
    panel.id = PANEL_ID;
    panel.innerHTML = `<strong>Gestor documental</strong><span>${message}</span>${withButton ? '<button type="button">Pegar y rellenar</button>' : ""}<small aria-live="polite"></small>`;
    document.body.appendChild(panel);
    return {
      panel,
      button: panel.querySelector("button"),
      status: panel.querySelector("small"),
    };
  }

  function setValue(name, value) {
    const input = document.querySelector(`[name="${CSS.escape(name)}"]`);
    if (!input || value === undefined || value === null || value === "") return false;
    input.value = value;
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.dispatchEvent(new KeyboardEvent("keyup", { bubbles: true, key: value.slice(-1) }));
    input.dispatchEvent(new Event("change", { bubbles: true }));
    input.dispatchEvent(new Event("blur", { bubbles: true }));
    return true;
  }

  function identityValueFor(input, nif) {
    const normalized = normalize(nif).replace(/[\s.-]/g, "");
    if (!input || !normalized) return normalized;
    const maxLength = Number(input.maxLength);
    return maxLength > 0 && normalized.length > maxLength ? normalized.slice(0, maxLength) : normalized;
  }

  function setIdentityValue(nif) {
    const input = document.querySelector('[name="nif_adquirente"]');
    const value = identityValueFor(input, nif);
    if (!input || !value) return false;
    input.focus();
    input.value = "";
    [...value].forEach((character, index) => {
      input.value = value.slice(0, index + 1);
      input.dispatchEvent(new InputEvent("input", { bubbles: true, data: character, inputType: "insertText" }));
      input.dispatchEvent(new KeyboardEvent("keyup", { bubbles: true, key: character }));
    });
    input.dispatchEvent(new Event("change", { bubbles: true }));
    return true;
  }

  function personalValues() {
    const names = [
      "apellido1_adquirente", "apellido2_adquirente", "nombre_adquirente",
      "provincia_adquirente", "municipio_adquirente", "pueblo_adquirente",
      "cp_adquirente", "calle_adquirente",
    ];
    return Object.fromEntries(names.map((name) => [
      name,
      document.querySelector(`[name="${CSS.escape(name)}"]`)?.value || "",
    ]));
  }

  async function waitForPersonAutofill(previous, timeout = 2500) {
    const startedAt = Date.now();
    while (Date.now() - startedAt < timeout) {
      const current = personalValues();
      if (Object.keys(current).some((name) => current[name] && current[name] !== previous[name])) return true;
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
    return false;
  }

  function selectByText(name, text) {
    const select = document.querySelector(`select[name="${CSS.escape(name)}"]`);
    if (!select || !text) return false;
    const wanted = normalize(text);
    const option = [...select.options].find((candidate) => {
      const label = normalize(candidate.textContent);
      const suffix = label.split("-").pop().trim();
      return Boolean(label) && (label === wanted || label.includes(wanted) || (Boolean(suffix) && wanted.includes(suffix)));
    });
    if (!option) return false;
    select.value = option.value;
    select.dispatchEvent(new Event("change", { bubbles: true }));
    return true;
  }

  async function waitForMunicipality(name, timeout = 5000) {
    const startedAt = Date.now();
    while (Date.now() - startedAt < timeout) {
      if (selectByText("municipio_adquirente", name)) return true;
      await new Promise((resolve) => setTimeout(resolve, 150));
    }
    return false;
  }

  async function fill(payload) {
    const buyer = payload.adquirente || {};
    const vehicle = payload.vehiculo || {};
    const company = buyer.razonSocial;
    const beforeLookup = personalValues();
    setIdentityValue(buyer.nif);
    const existingPerson = await waitForPersonAutofill(beforeLookup, 5000);
    document.querySelector('[name="nif_adquirente"]')?.dispatchEvent(new Event("blur", { bubbles: true }));
    let municipalityOk = true;
    let roadTypeOk = true;
    if (!existingPerson) {
      setValue("apellido1_adquirente", company || buyer.apellido1);
      setValue("apellido2_adquirente", company ? "" : buyer.apellido2);
      setValue("nombre_adquirente", company ? "" : buyer.nombre);
      selectByText("provincia_adquirente", buyer.provincia);
      municipalityOk = await waitForMunicipality(buyer.municipio);
      setValue("pueblo_adquirente", buyer.localidad);
      setValue("cp_adquirente", buyer.codigoPostal);
      roadTypeOk = selectByText("siglas_adquirente", buyer.tipoVia);
      setValue("calle_adquirente", buyer.nombreVia);
      setValue("num_adquirente", buyer.numeroVia);
      setValue("bloque_adquirente", buyer.bloque);
      setValue("escalera_adquirente", buyer.escalera);
      setValue("piso_adquirente", buyer.piso);
      setValue("puerta_adquirente", buyer.puerta);
    }
    setValue("matricula", vehicle.matricula);
    setValue("bastidor", vehicle.bastidor);
    setValue("marca", vehicle.marca);
    setValue("modelo", vehicle.modelo);
    return [...(!municipalityOk ? ["municipio"] : []), ...(!roadTypeOk ? ["tipo de vía"] : [])];
  }

  async function fillWithStatus(status, button) {
    if (button) button.disabled = true;
    status.textContent = "Leyendo y rellenando datos…";
    try {
      const warnings = await fill(await readPayload());
      status.textContent = warnings.length
        ? `Datos rellenados. Revisa manualmente: ${warnings.join(", ")}.`
        : "Datos rellenados. Revisa los campos y completa Documento y Motivo antes de enviar.";
      await chrome.storage.local.remove(STORAGE_KEY);
      status.closest("aside")?.classList.toggle("gestor-warning", warnings.length > 0);
    } catch (error) {
      status.textContent = error instanceof Error ? error.message : "No se pudieron pegar los datos.";
      status.closest("aside")?.classList.add("gestor-warning");
    } finally {
      if (button) button.disabled = false;
    }
  }

  async function createDraftIfRequested() {
    if (location.hash !== CREATE_HASH || document.getElementById("form_matricula")) return false;
    const ui = createPanel("Creando un justificante nuevo…");
    try {
      await readPayload();
      const action = [...document.querySelectorAll('input[name="oak_action"]')]
        .find((input) => input.value === "nuevo");
      const form = action?.form;
      if (!form) throw new Error("No se encontró la acción para crear el justificante.");
      sessionStorage.setItem(AUTO_FLAG, "1");
      history.replaceState(null, "", `${location.pathname}${location.search}`);
      form.target = "_self";
      form.requestSubmit();
    } catch (error) {
      sessionStorage.removeItem(AUTO_FLAG);
      ui.status.textContent = error instanceof Error ? error.message : "No se pudo crear el justificante.";
      ui.panel.classList.add("gestor-warning");
    }
    return true;
  }

  async function initializeEditor() {
    if (!document.getElementById("form_matricula")) return;
    const ui = createPanel("Rellena este borrador con los datos copiados desde la solicitud.", true);
    ui.button.addEventListener("click", () => fillWithStatus(ui.status, ui.button));
    const shouldAutofill = sessionStorage.getItem(AUTO_FLAG) === "1" || await hasFreshPayload();
    sessionStorage.removeItem(AUTO_FLAG);
    if (shouldAutofill) await fillWithStatus(ui.status, ui.button);
  }

  const style = document.createElement("style");
  style.textContent = `
    #${PANEL_ID}{position:fixed;right:20px;bottom:76px;z-index:2147483647;width:300px;padding:14px;border:1px solid #b9d8d1;border-radius:10px;background:#f3fbf9;color:#173b35;box-shadow:0 10px 28px rgba(0,0,0,.18);font:14px/1.35 system-ui,sans-serif}
    #${PANEL_ID} strong,#${PANEL_ID} span,#${PANEL_ID} small{display:block} #${PANEL_ID} span{margin:5px 0 10px;color:#46645f}
    #${PANEL_ID} button{width:100%;padding:8px 12px;border:0;border-radius:6px;background:#167765;color:white;font-weight:700;cursor:pointer}
    #${PANEL_ID} button:disabled{opacity:.6;cursor:wait} #${PANEL_ID} small{margin-top:9px}
    #${PANEL_ID}.gestor-warning{border-color:#d79c40;background:#fff8eb}`;
  document.head.appendChild(style);

  createDraftIfRequested().then((handled) => {
    if (!handled) initializeEditor();
  });
})();
