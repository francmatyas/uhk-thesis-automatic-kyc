import axios from "axios";

const BASE_PATH =
  import.meta.env.VITE_API_BASE_PATH ?? "/flow/verify/v1";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "",
  timeout: 10000,
  xsrfCookieName: "", // vypne vestavěný XSRF v axiosu - čte syrové UUID z cookie, které Spring odmítne
  xsrfHeaderName: "", // maskovaný token z odpovědi /auth/csrf nastavujeme ručně
});

// Spring Security 6 XorCsrfTokenRequestAttributeHandler po každém úspěšném
// požadavku otočí maskovaný CSRF token. Před každým mutačním požadavkem načti nový z /auth/csrf.
api.interceptors.request.use(async (config) => {
  if (config.method !== "get") {
    const { data: csrf } = await api.get("/auth/csrf");
    config.headers[csrf.headerName] = csrf.token;
  }
  return config;
});

// Kanonické pořadí a klasifikace kroků
const STEP_ORDER = [
  "PERSONAL_INFO",
  "EMAIL_VERIFICATION",
  "PHONE_VERIFICATION",
  "DOCUMENT_IDENTITY",
  "LIVENESS_CHECK",
  "AML_QUESTIONNAIRE",
];

const MANDATORY_STEP_TYPES = new Set([
  "PERSONAL_INFO",
  "DOCUMENT_IDENTITY",
  "LIVENESS_CHECK",
]);

const STEP_ID_MAP = {
  PERSONAL_INFO: "personalInfo",
  EMAIL_VERIFICATION: "email",
  PHONE_VERIFICATION: "phone",
  DOCUMENT_IDENTITY: "document",
  LIVENESS_CHECK: "liveness",
  AML_QUESTIONNAIRE: "aml",
};

const API_TO_UI_DOCUMENT_TYPE = {
  CZECH_ID: "id_card",
  PASSPORT: "passport",
};

function buildSteps(journeyConfig, serverSteps) {
  const optionalSteps = new Set(journeyConfig?.optionalSteps ?? []);
  const allowedDocumentTypes = (journeyConfig?.allowedDocumentTypes ?? ["CZECH_ID", "PASSPORT"])
    .map((t) => API_TO_UI_DOCUMENT_TYPE[t])
    .filter(Boolean);

  // Indexuj stavy ze serveru podle stepType (backend kroky jako DOC_OCR se ignorují)
  const serverStatus = Object.fromEntries(
    (serverSteps ?? []).map((s) => [s.stepType, s.status]),
  );

  const steps = STEP_ORDER
    .filter((type) => MANDATORY_STEP_TYPES.has(type) || optionalSteps.has(type))
    .map((type) => ({
      id: STEP_ID_MAP[type],
      type,
      status: serverStatus[type] ?? "PENDING",
      required: true,
      config: type === "DOCUMENT_IDENTITY" ? { allowedDocumentTypes } : null,
    }));

  // Označ první nedokončený krok jako IN_PROGRESS
  const firstPending = steps.find((s) => s.status !== "COMPLETED");
  if (firstPending) firstPending.status = "IN_PROGRESS";

  return steps;
}

function normalizeFlow(data, token) {
  return {
    id: data.verificationId ?? data.id,
    token,
    status: data.status ?? "IN_PROGRESS",
    expiresAt: data.expiresAt || null,
    steps: buildSteps(data.journeyConfig, data.steps),
  };
}

export async function fetchVerificationFlow(token) {
  const { data } = await api.get(`${BASE_PATH}/${encodeURIComponent(token)}`);
  return normalizeFlow(data, token);
}

export async function sendEmailVerificationCode(token, email) {
  const { data } = await api.post(
    `${BASE_PATH}/${encodeURIComponent(token)}/email/send-code`,
    { contact: email },
  );
  return data;
}

export async function verifyEmailCode(token, code) {
  const { data } = await api.post(
    `${BASE_PATH}/${encodeURIComponent(token)}/email/verify-code`,
    { code },
  );
  return data;
}

export async function sendPhoneVerificationCode(token, dialCode, contact) {
  const { data } = await api.post(
    `${BASE_PATH}/${encodeURIComponent(token)}/phone/send-code`,
    { dialCode, contact },
  );
  return data;
}

export async function verifyPhoneCode(token, code) {
  const { data } = await api.post(
    `${BASE_PATH}/${encodeURIComponent(token)}/phone/verify-code`,
    { code },
  );
  return data;
}

export async function submitPersonalInfo(token, personalInfo) {
  const { data } = await api.post(
    `${BASE_PATH}/${encodeURIComponent(token)}/personal-info`,
    personalInfo,
  );
  return data;
}

// ── Pomocné funkce pro presign dokumentů ─────────────────────────────────────

async function presignDocument(token, filename, contentType, category) {
  const { data } = await api.post(
    `${BASE_PATH}/${encodeURIComponent(token)}/documents/presign`,
    { filename, contentType, category },
  );
  return data; // { documentId, uploadUrl, storageKey, publicUrl, uploadExpiresAt }
}

async function uploadToPresignedUrl(uploadUrl, file) {
  const response = await fetch(uploadUrl, {
    method: "PUT",
    body: file,
    headers: { "Content-Type": file.type },
  });
  if (!response.ok) {
    throw new Error(`Presigned upload failed with status ${response.status}`);
  }
}

async function completeDocument(token, documentId) {
  const { data } = await api.post(
    `${BASE_PATH}/${encodeURIComponent(token)}/documents/${documentId}/complete`,
  );
  return data;
}

async function presignAndUpload(token, file, category) {
  const presign = await presignDocument(token, file.name, file.type, category);
  await uploadToPresignedUrl(presign.uploadUrl, file);
  await completeDocument(token, presign.documentId);
  return presign.documentId;
}

// ── Ověření identity dokumentem ──────────────────────────────────────────────

const DOCUMENT_TYPE_MAP = {
  id_card: "CZECH_ID",
  passport: "PASSPORT",
};

// soubory: { front: File, back?: File }
export async function uploadIdentityDocument(token, documentType, files) {
  const frontDocumentId = await presignAndUpload(
    token,
    files.front,
    "DOCUMENT_FRONT",
  );
  const backDocumentId =
    documentType === "id_card"
      ? await presignAndUpload(token, files.back, "DOCUMENT_BACK")
      : undefined;

  const { data } = await api.post(
    `${BASE_PATH}/${encodeURIComponent(token)}/id-document`,
    {
      documentType: DOCUMENT_TYPE_MAP[documentType] ?? documentType,
      frontDocumentId,
      ...(backDocumentId ? { backDocumentId } : {}),
    },
  );
  return data;
}

// ── Test živosti (liveness) ──────────────────────────────────────────────────

const LIVENESS_POSITION_MAP = {
  front: "center",
  left: "left",
  right: "right",
  up: "up",
};

// frames: File[] v pořadí odpovídajícím POSITIONS (front, left, right, up)
export async function uploadLivenessFrames(token, frames) {
  const POSITIONS = ["front", "left", "right", "up"];
  const images = [];
  for (let i = 0; i < frames.length; i++) {
    const file = frames[i];
    if (!file) continue;
    const documentId = await presignAndUpload(token, file, "LIVENESS");
    images.push({ documentId, position: LIVENESS_POSITION_MAP[POSITIONS[i]] });
  }

  const { data } = await api.post(
    `${BASE_PATH}/${encodeURIComponent(token)}/liveness`,
    { images },
  );
  return data;
}

// ── AML dotazník ──────────────────────────────────────────────────────────────

export async function submitAmlQuestionnaire(token, answers) {
  const { data } = await api.post(
    `${BASE_PATH}/${encodeURIComponent(token)}/aml`,
    answers,
  );
  return data;
}

// ── Finalizace ───────────────────────────────────────────────────────────────

export async function finalizeVerification(token) {
  const { data } = await api.post(
    `${BASE_PATH}/${encodeURIComponent(token)}/finalize`,
  );
  return data;
}
