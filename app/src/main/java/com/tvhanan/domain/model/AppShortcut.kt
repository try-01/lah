package com.tvhanan.domain.model

/**
 * App ID Samsung Smart Hub. ID ini TIDAK resmi didokumentasikan Samsung
 * dan bisa berubah/berbeda per region & firmware — nilai di sini sudah
 * diverifikasi LANGSUNG dari TV (lewat ed.installedApp.get) untuk unit
 * Samsung UA32N4300 (N-series 2020, Tizen 5.0).
 *
 * Cara launch app TERBUKTI bekerja di firmware ini lewat REST API:
 *   POST  http://{ip}:8001/api/v2/applications/{appId}   -> buka app
 *   DELETE http://{ip}:8001/api/v2/applications/{appId}  -> tutup app
 * (lihat AppLauncher.kt)
 *
 * Catatan riset tambahan (belum dipakai, untuk referensi masa depan):
 * Launch app JUGA bisa lewat WebSocket ms.channel.emit/ed.apps.launch,
 * TAPI action_type harus disesuaikan dengan app_type masing-masing app
 * (didapat dari ed.installedApp.get):
 *   app_type 2 (kebanyakan app streaming: Netflix, YouTube, Prime, dst)
 *     -> action_type harus "DEEP_LINK"
 *   app_type 4 (system app seperti browser)
 *     -> action_type harus "NATIVE_LAUNCH"
 * Salah pasangan action_type/app_type menyebabkan TV diam tanpa respons
 * (bukan error, cuma diabaikan) — ini sumber kebingungan utama saat
 * awal mengembangkan fitur ini.
 */
enum class AppShortcut(val appId: String, val label: String) {
    NETFLIX("11101200001", "Netflix"),
    PRIME_VIDEO("3201512006785", "Prime Video"),
    YOUTUBE("111299001912", "YouTube")
}