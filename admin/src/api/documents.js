import axiosInstance from "@/api/axiosInstance";

export async function presignDocumentUpload({
  ownerType,
  ownerId,
  tenantId = null,
  category,
  filename,
  contentType,
}) {
  const response = await axiosInstance.post("/documents/presign-upload", {
    ownerType,
    ownerId,
    tenantId,
    category,
    filename,
    contentType,
  });
  return response.data;
}

export async function putDocumentToStorage({ uploadUrl, file, contentType }) {
  const uploadResponse = await fetch(uploadUrl, {
    method: "PUT",
    headers: { "Content-Type": contentType },
    body: file,
  });

  if (!uploadResponse.ok) {
    throw new Error("Failed to upload document to storage.");
  }
}

export async function completeDocumentUpload(documentId, payload) {
  const response = await axiosInstance.post(
    `/documents/${documentId}/complete`,
    payload,
  );
  return response.data;
}

export async function deleteDocument(documentId) {
  await axiosInstance.delete(`/documents/${documentId}`);
}

