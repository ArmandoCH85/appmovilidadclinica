/// <reference types="vite/client" />

// Variable opcional para apuntar el SPA a otro backend que no sea
// http://localhost:8080/api (default en api/client.ts). No hay .env en el
// repo: el default alcanza para dev local (mismo puerto que backend/cmd/server).
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
}
interface ImportMeta {
  readonly env: ImportMetaEnv
}
