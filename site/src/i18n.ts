// All page copy, in Indonesian (default, served at /) and English (served at /en/).
export type Lang = "id" | "en";

const id = {
  htmlTitle: "Waktu Sholat: Jadwal Sholat Offline untuk Android",
  metaDescription:
    "Jadwal sholat yang dihitung langsung di HP, tanpa internet dan tanpa iklan. Metode Kemenag, arah kiblat akurat, lima widget, dan bisa dipakai di mana pun di dunia. Gratis.",
  ogDescription: "Jadwal sholat yang dihitung di HP, tanpa internet dan tanpa iklan. Metode Kemenag, arah kiblat akurat, dan lima widget. Gratis.",
  ogAlt: "Waktu Sholat: layar utama dengan langit siang, siluet masjid, dan sholat berikutnya.",
  skip: "Langsung ke unduhan",
  navDay: "Cara kerja",
  navFeatures: "Fitur",
  navDownload: "Unduh",
  langSwitch: "EN",
  langSwitchLabel: "Switch to English",

  headline: "Jadwal sholat yang tepat, tanpa internet.",
  lede: "Waktu Sholat menghitung jadwal langsung di HP dengan metode Kemenag. Tanpa iklan, tanpa akun, dan ringan.",
  download: "Unduh untuk Android",
  minAndroid: "Android 7.0 atau lebih baru",
  latest: "terbaru",

  prayers: ["Imsak", "Subuh", "Terbit", "Dzuhur", "Ashar", "Maghrib", "Isya"],
  weekdays: ["Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"],
  months: ["Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember"],
  changePlace: "Ganti lokasi",
  placeTitle: "Pilih lokasi",
  searchLabel: "Cari kota",
  searchPlaceholder: "Cari kota, mis. Bandung atau Makkah",
  useMyLocation: "Pakai lokasi saya",
  locating: "Mencari lokasimu...",
  locationFailed: "Lokasi tidak bisa didapat. Cari kotamu di atas.",
  noResults: "Tidak ada kota yang cocok.",
  myLocation: "Lokasi saya",
  close: "Tutup",
  method: "Metode Kemenag, sama seperti bawaan aplikasi.",
  todayTitle: "Jadwal hari ini",
  nextPrayer: "Sholat berikutnya",
  inTime: "{t} lagi",
  hours: "{h} jam {m} menit",
  hoursOnly: "{h} jam",
  minutes: "{m} menit",
  lessMinute: "kurang dari 1 menit",

  dayTitle: "Satu hari, lima waktu",
  dayBody:
    "Waktu Sholat membagi harimu menurut jadwal sholat di lokasimu, dan langit di aplikasi ikut berganti: subuh, pagi, siang, sore, senja, dan malam. Ini hari ini di {city}.",
  now: "Sekarang",

  featuresTitle: "Dibuat untuk dipakai lima kali sehari, bertahun-tahun.",
  f1Title: "Sekali lihat, langsung tahu",
  f1Body:
    "Sholat berikutnya dan sisa waktunya tertulis jelas, misalnya \"1 jam 15 menit lagi\". Latar langitnya ikut berubah dari subuh sampai malam, dan jadwal hari itu ada tepat di bawahnya.",
  f1Points: ["Sama dengan jadwal Kemenag yang diterbitkan", "Tanggal Hijriah, jadwal bulanan, dan tema gelap", "Notifikasi tepat saat waktu sholat masuk, dengan pengingat opsional"],
  f2Title: "Arah kiblat yang bisa dipercaya",
  f2Body:
    "Kompas meminta kalibrasi dulu sebelum menunjukkan arah. Sensor HP menunjuk ke utara magnetis, jadi selisihnya dengan utara sebenarnya dikoreksi otomatis sesuai lokasimu.",
  f2Points: ["Peringatan saat ada gangguan magnet atau HP dipegang miring", "Getar sekali saat HP pas menghadap kiblat"],
  f3Title: "Lima widget untuk layar utama",
  f3Body:
    "Pilih yang cocok: sholat berikutnya, jadwal sehari dalam satu baris, jadwal lengkap, hitung mundur, atau teks minimalis langsung di atas wallpaper. Warnanya mengikuti wallpaper di Android 12 ke atas.",
  f3Body2: "Widget diperbarui tiap menit tanpa membangunkan HP, jadi tidak menguras baterai.",
  altToday: "Layar utama Waktu Sholat: langit siang, sholat berikutnya Ashar, dan jadwal hari ini.",
  altLight: "Tema terang",
  altDark: "Tema gelap",
  altQibla: "Kompas arah kiblat 295 derajat dari Jakarta, dengan Kakbah di tepi kompas dan koreksi deklinasi magnetik.",
  altWidgets: "Layar utama dengan widget Jadwal hari ini dan widget Minimalis.",

  facts: [
    ["Di mana saja", "±490 kota di Indonesia dan 3.000+ kota dunia tersimpan di HP. Bisa juga pakai GPS atau cari tempat mana pun."],
    ["Dua bahasa", "Bahasa Indonesia dan English, bisa diganti di Pengaturan."],
    ["Privasi", "Tanpa iklan, akun, atau pelacakan. Internet hanya untuk nama tempat dan cek pembaruan."],
    ["Ringan", "Kurang dari 300 KB, tanpa proses latar yang berjalan terus. Android 7.0 atau lebih baru."],
  ],

  dlTitle: "Unduh Waktu Sholat",
  dlLede: "Gratis dan terbuka. Setelah terpasang, aplikasi memberi tahu saat ada versi baru dan bisa memperbarui dirinya sendiri.",
  downloadApk: "Unduh APK",
  mVersion: "Versi",
  mSize: "Ukuran",
  mDate: "Dirilis",
  allReleases: "Semua versi dan catatan rilis",
  stepsTitle: "Cara memasang",
  steps: [
    ["Unduh file APK", "Buka halaman ini di HP Android lalu ketuk tombol unduh."],
    ["Izinkan pemasangan", "Saat diminta, izinkan browser atau Files untuk memasang aplikasi. Ini hanya sekali."],
    ["Pilih lokasi dan nyalakan notifikasi", "Cari kotamu atau pakai lokasi HP, lalu nyalakan notifikasi waktu sholat di Pengaturan."],
  ],

  faqTitle: "Pertanyaan",
  faqMore: "Ada pertanyaan lain atau menemukan masalah?",
  faq: [
    ["Seberapa akurat jadwalnya?", "Jadwal dihitung dengan metode Kemenag, termasuk pembulatan dan tambahan menit kehati-hatian (ihtiyati) yang dipakai jadwal resmi. Hasilnya diuji terhadap jadwal yang diterbitkan dan cocok sampai ke menitnya. Di luar negeri kamu bisa memilih metode lain seperti Umm Al-Qura atau MWL."],
    ["Kenapa tidak ada di Play Store?", "Aplikasi ini dibagikan langsung lewat GitHub. Setiap rilis ditandatangani dengan kunci yang sama, dan aplikasi memeriksa checksum SHA-256 sebelum memasang pembaruan."],
    ["Notifikasi tidak muncul, apa yang harus dicek?", "Buka Pengaturan di aplikasi. Kalau ada yang menghalangi, misalnya izin notifikasi atau penghemat baterai, akan muncul baris berwarna oranye dengan tombol untuk memperbaikinya. Gunakan \"Kirim notifikasi uji\" untuk memastikan."],
    ["Apakah ada versi iPhone?", "Belum. Saat ini Waktu Sholat hanya tersedia untuk Android 7.0 atau lebih baru."],
  ],
  releases: "Rilis",
  report: "Laporkan masalah",
  credit: "Data kota dunia dari GeoNames (CC BY 4.0).",
};

const en: typeof id = {
  htmlTitle: "Waktu Sholat: Offline Prayer Times for Android",
  metaDescription:
    "Prayer times calculated on your phone, with no internet and no ads. Kemenag method, an accurate Qibla compass, five widgets, and it works anywhere in the world. Free.",
  ogDescription: "Prayer times calculated on your phone, with no internet and no ads. Kemenag method, accurate Qibla, and five widgets. Free.",
  ogAlt: "Waktu Sholat: home screen with a midday sky, a mosque skyline and the next prayer.",
  skip: "Skip to download",
  navDay: "How it works",
  navFeatures: "Features",
  navDownload: "Download",
  langSwitch: "ID",
  langSwitchLabel: "Ganti ke Bahasa Indonesia",

  headline: "Accurate prayer times, no internet needed.",
  lede: "Waktu Sholat calculates the schedule on your phone using the Kemenag method. No ads, no account, and it stays light.",
  download: "Download for Android",
  minAndroid: "Android 7.0 or later",
  latest: "latest",

  prayers: ["Imsak", "Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"],
  weekdays: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"],
  months: ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"],
  changePlace: "Change location",
  placeTitle: "Choose a location",
  searchLabel: "Search cities",
  searchPlaceholder: "Search a city, e.g. London or Makkah",
  useMyLocation: "Use my location",
  locating: "Finding your location...",
  locationFailed: "Couldn't get your location. Search your city above.",
  noResults: "No matching cities.",
  myLocation: "My location",
  close: "Close",
  method: "Kemenag method, the app's default.",
  todayTitle: "Today's schedule",
  nextPrayer: "Next prayer",
  inTime: "in {t}",
  hours: "{h} h {m} min",
  hoursOnly: "{h} h",
  minutes: "{m} min",
  lessMinute: "less than a minute",

  dayTitle: "One day, five prayers",
  dayBody:
    "Waktu Sholat divides your day by the prayer times at your location, and the sky in the app follows along: dawn, morning, midday, afternoon, dusk and night. This is today in {city}.",
  now: "Now",

  featuresTitle: "Made to be used five times a day, for years.",
  f1Title: "One glance is enough",
  f1Body:
    "The next prayer and how long until it are written plainly, like \"in 1 h 15 min\". The sky behind it changes from dawn to night, with the whole day's schedule right below.",
  f1Points: ["Matches the schedule Kemenag publishes", "Hijri date, monthly timetable and a dark theme", "A notification the minute each prayer begins, with an optional reminder"],
  f2Title: "A Qibla direction you can trust",
  f2Body:
    "The compass asks for calibration before it points anywhere. Phone sensors point to magnetic north, so the difference from true north is corrected for your location.",
  f2Points: ["Warnings for magnetic interference or a tilted phone", "A single vibration when the phone faces the Qibla"],
  f3Title: "Five home-screen widgets",
  f3Body:
    "Pick what fits: next prayer, the day in one row, the full schedule, a countdown, or minimal text right on your wallpaper. Colours follow your wallpaper on Android 12 and later.",
  f3Body2: "Widgets refresh every minute without waking the phone, so they don't drain the battery.",
  altToday: "Waktu Sholat home screen: a midday sky, the next prayer Asr, and today's schedule.",
  altLight: "Light theme",
  altDark: "Dark theme",
  altQibla: "Qibla compass pointing 295 degrees from Jakarta, with the Kaaba on its rim, corrected for magnetic declination.",
  altWidgets: "Home screen with the Today's times and Minimal widgets.",

  facts: [
    ["Anywhere", "About 490 Indonesian cities and 3,000+ world cities are stored on the phone. You can also use GPS or search any place."],
    ["Two languages", "Bahasa Indonesia and English, switchable in Settings."],
    ["Privacy", "No ads, accounts or tracking. The internet is used only for place names and update checks."],
    ["Light", "Under 300 KB, with nothing running in the background. Android 7.0 or later."],
  ],

  dlTitle: "Download Waktu Sholat",
  dlLede: "Free and open. Once installed, the app tells you when a new version is out and can update itself.",
  downloadApk: "Download APK",
  mVersion: "Version",
  mSize: "Size",
  mDate: "Released",
  allReleases: "All versions and release notes",
  stepsTitle: "How to install",
  steps: [
    ["Download the APK", "Open this page on your Android phone and tap the download button."],
    ["Allow the install", "When asked, allow your browser or Files to install apps. You only do this once."],
    ["Choose a location and turn on notifications", "Search your city or use your phone's location, then turn on prayer notifications in Settings."],
  ],

  faqTitle: "Questions",
  faqMore: "Something else, or found a bug?",
  faq: [
    ["How accurate are the times?", "Times use the Kemenag method, including the rounding and precautionary minutes (ihtiyati) used by the official schedule. They are tested against the published schedule and match to the minute. Abroad you can choose another method such as Umm Al-Qura or MWL."],
    ["Why isn't it on the Play Store?", "The app is shared directly through GitHub. Every release is signed with the same key, and the app checks the SHA-256 checksum before installing an update."],
    ["Notifications don't appear. What should I check?", "Open Settings in the app. If something is blocking it, such as the notification permission or battery saving, an orange row appears with a button to fix it. Use \"Send a test notification\" to make sure."],
    ["Is there an iPhone version?", "Not yet. Waktu Sholat is available for Android 7.0 or later only."],
  ],
  releases: "Releases",
  report: "Report a problem",
  credit: "World city data from GeoNames (CC BY 4.0).",
};

export const strings = { id, en };
export type Strings = typeof id;
export const t = (lang: Lang): Strings => strings[lang];

export { REPO, APK_URL } from "./lib/links";
