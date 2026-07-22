// Bei responseType: 'blob' liefert axios auch Fehlerantworten als Blob statt als
// geparstes JSON - der Fehlertext muss erst asynchron aus dem Blob gelesen werden.
export async function extractBlobErrorMessage(err: unknown): Promise<string> {
  if (err && typeof err === 'object' && 'response' in err) {
    const data = (err as { response?: { data?: unknown } }).response?.data;
    if (data instanceof Blob) {
      try {
        const parsed = JSON.parse(await data.text()) as { message?: string };
        if (parsed.message) return parsed.message;
      } catch { /* keine JSON-Fehlerantwort */ }
    }
  }
  return 'Ein unbekannter Fehler ist aufgetreten.';
}
