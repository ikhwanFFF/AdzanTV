# Jadwal Adzan (KHGT Muhammadiyah)

Aplikasi Android native (Kotlin + Jetpack Compose) yang memakai API
https://api-khgt.muhammadiyah.or.id

## Cara build
1. Buka folder ini di Android Studio (Koala / 2024.1 atau lebih baru), tunggu Gradle sync.
2. Colok HP / jalankan emulator, tekan Run.
3. APK: Build > Build Bundle(s) / APK(s) > Build APK(s).

## Fitur
- Jadwal hari ini + hitung mundur adzan berikutnya
- Jadwal sebulan
- Cari kota (POST /api/sholat/search)
- Notifikasi adzan (AlarmManager), dijadwal ulang otomatis setelah reboot

## Catatan
- Parser respons dibuat toleran karena dokumentasi API tidak merinci nama field.
  Jika ada kolom kosong, sesuaikan KEYS di Api.kt.
- Lokasi bawaan: Cikarang (ubah di Store.place() pada Alarm.kt).
