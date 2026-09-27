// Waktu Sholat landing page: the hero sky follows the visitor's local time, the page switches
// between Indonesian and English, and the download section shows the real latest release.
(() => {
  const REPO = "rinci-labs/waktu-sholat";

  // ---- Language ---------------------------------------------------------------------------------

  const en = {
    skip: "Skip to download",
    nav_features: "Features",
    nav_day: "How it works",
    day_title: "One day, five prayers",
    day_body: "Waktu Sholat divides your day by the prayer times at your location, and the sky in the app follows along: dawn, morning, midday, afternoon, dusk and night. An example for Jakarta:",
    now_short: "Now",
    p_fajr: "Fajr",
    p_sunrise: "Sunrise",
    p_dhuhr: "Dhuhr",
    p_asr: "Asr",
    p_maghrib: "Maghrib",
    p_isha: "Isha",
    nav_download: "Download",
    summary_title: "Summary",
    h1_t: "Right to the minute",
    h1_b: "Kemenag method with the official rounding, calculated on the phone without internet.",
    h2_t: "A calibrated Qibla",
    h2_b: "The compass is calibrated first and corrected to true north.",
    h3_t: "Anywhere in the world",
    h3_b: "Pick a city, use GPS, or search any place, and the time zone follows.",
    headline: "Accurate prayer times, no internet needed.",
    lede: "Waktu Sholat calculates the schedule on your phone using the Kemenag method. No ads, no account, and it is only about 200 KB.",
    download: "Download for Android",
    features_title: "Made to be used five times a day, for years.",
    features_lede: "Everything that matters, nothing that gets in the way. Times come from the sun's position at your location, so it keeps working without signal.",
    f1_title: "One glance is enough",
    f1_body: "The next prayer and how long until it are written plainly, like \"in 1 h 15 min\". The sky behind it changes from dawn to night, with the whole day's schedule right below.",
    f1_a: "Matches the schedule Kemenag publishes",
    f1_b: "Hijri date, monthly timetable and a dark theme",
    f1_c: "Adhan notifications on the minute, with an optional reminder",
    f2_title: "A Qibla direction you can trust",
    f2_body: "The compass asks for calibration before it points anywhere. Phone sensors point to magnetic north, so the difference from true north is corrected for your location.",
    f2_a: "Warnings for magnetic interference or a tilted phone",
    f2_b: "A single vibration when the phone faces the Qibla",
    f3_title: "Five home-screen widgets",
    f3_body: "Pick what fits: next prayer, the day in one row, the full schedule, a countdown, or minimal text right on your wallpaper. Colours follow your wallpaper on Android 12 and later.",
    f3_body2: "Widgets refresh every minute without waking the phone, so they don't drain the battery.",
    facts_title: "Details",
    k_where: "Anywhere",
    v_where: "About 490 Indonesian cities are stored on the phone. Abroad, use GPS or search any place in the world.",
    k_lang: "Two languages",
    v_lang: "Bahasa Indonesia and English, switchable in Settings.",
    k_privacy: "Privacy",
    v_privacy: "No ads, accounts or tracking. The internet is used only for place names and update checks.",
    k_small: "Light",
    v_small: "About 200 KB, with nothing running in the background. Android 7.0 or later.",
    dl_title: "Download Waktu Sholat",
    dl_lede: "Free and open. Once installed, the app tells you when a new version is out and can update itself.",
    download_apk: "Download APK",
    m_version: "Version",
    m_size: "Size",
    m_date: "Released",
    all_releases: "All versions and release notes",
    steps_title: "How to install",
    s1_t: "Download the APK",
    s1_b: "Open this page on your Android phone and tap the download button.",
    s2_t: "Allow the install",
    s2_b: "When asked, allow your browser or Files to install apps. You only do this once.",
    s3_t: "Choose a location and turn on the adhan",
    s3_b: "Pick your city or use your phone's location, then turn on adhan notifications in Settings.",
    faq_title: "Questions",
    q1: "How accurate are the times?",
    a1: "Times use the Kemenag method, including the rounding and precautionary minutes (ihtiyati) used by the official schedule. They are tested against the published schedule and match to the minute. Abroad you can choose another method such as Umm Al-Qura or MWL.",
    q2: "Why isn't it on the Play Store?",
    a2: "The app is shared directly through GitHub. Every release is signed with the same key, and the app checks the SHA-256 checksum before installing an update.",
    q3: "The adhan doesn't appear. What should I check?",
    a3: "Open Settings in the app. If something is blocking it, such as the notification permission or battery saving, an orange row appears with a button to fix it. Use \"Send a test notification\" to make sure.",
    q4: "Is there an iPhone version?",
    a4: "Not yet. Waktu Sholat is available for Android 7.0 or later only.",
    releases: "Releases",
    report: "Report a problem",
    alt_today: "Waktu Sholat home screen: a midday sky with a mosque skyline, next prayer Asr at 14:54, and today's schedule.",
    alt_light: "Light theme",
    alt_dark: "Dark theme",
    alt_qibla: "Qibla compass pointing 295 degrees from Jakarta, corrected for magnetic declination.",
    alt_widgets: "Home screen with the Today's times and Minimal widgets.",
    min_android: "Android 7.0 or later",
    latest: "latest",
  };

  const periodsText = {
    id: { subuh: "Sekarang subuh di tempatmu", pagi: "Sekarang pagi di tempatmu", siang: "Sekarang siang di tempatmu", sore: "Sekarang sore di tempatmu", senja: "Sekarang senja di tempatmu", malam: "Sekarang malam di tempatmu" },
    en: { subuh: "It's dawn where you are", pagi: "It's morning where you are", siang: "It's midday where you are", sore: "It's afternoon where you are", senja: "It's dusk where you are", malam: "It's night where you are" },
  };

  // Indonesian is the page's own markup; capture it once so switching back is lossless.
  const id = {};
  document.querySelectorAll("[data-i18n]").forEach((el) => (id[el.dataset.i18n] = el.textContent));
  document.querySelectorAll("[data-i18n-alt]").forEach((el) => (id[el.dataset.i18nAlt] = el.alt));
  id.min_android = "Android 7.0 atau lebih baru";
  id.latest = "terbaru";

  let lang = "id";
  try {
    lang = localStorage.getItem("lang") || (navigator.language.toLowerCase().startsWith("id") ? "id" : "en");
  } catch {
    lang = navigator.language.toLowerCase().startsWith("id") ? "id" : "en";
  }

  const t = (key) => (lang === "en" ? en : id)[key] ?? id[key] ?? "";

  function applyLanguage() {
    document.documentElement.lang = lang;
    document.querySelectorAll("[data-i18n]").forEach((el) => {
      if (el.id === "period-label") return;
      el.textContent = t(el.dataset.i18n);
    });
    document.querySelectorAll("[data-i18n-alt]").forEach((el) => (el.alt = t(el.dataset.i18nAlt)));
    const button = document.getElementById("lang");
    button.textContent = lang === "en" ? "ID" : "EN";
    button.setAttribute("aria-label", lang === "en" ? "Ganti ke Bahasa Indonesia" : "Switch to English");
    updateSky();
    renderRelease();
  }

  document.getElementById("lang").addEventListener("click", () => {
    lang = lang === "en" ? "id" : "en";
    try { localStorage.setItem("lang", lang); } catch {}
    applyLanguage();
  });

  // ---- Sky ----------------------------------------------------------------------------------------

  const sky = document.querySelector(".sky");
  const body = sky.querySelector(".sky-body");
  const disc = body.querySelector(".disc");
  const glow = body.querySelector(".halo");

  // Approximate period boundaries by local clock; the app itself uses the real prayer times.
  function periodAt(date) {
    const h = date.getHours() + date.getMinutes() / 60;
    if (h >= 4.3 && h < 5.6) return "subuh";
    if (h >= 5.6 && h < 11.8) return "pagi";
    if (h >= 11.8 && h < 15) return "siang";
    if (h >= 15 && h < 17.9) return "sore";
    if (h >= 17.9 && h < 19) return "senja";
    return "malam";
  }

  function updateSky() {
    const now = new Date();
    const period = periodAt(now);
    sky.dataset.period = period;
    document.getElementById("period-label").textContent = periodsText[lang][period];

    // Sun from 05:40 to 17:55, moon from 17:55 to 04:20, on the same shallow arc as in the app.
    const h = now.getHours() + now.getMinutes() / 60;
    const isMoon = h < 5.66 || h >= 17.9;
    const p = isMoon ? ((h >= 17.9 ? h - 17.9 : h + 24 - 17.9) / 10.4) : (h - 5.66) / 12.24;
    // Wide screens: a low arc just above the hills, under the centred text.
    // Phones: a small arc in the top-right corner, clear of the headline.
    const q = Math.min(Math.max(p, 0), 1);
    const wide = window.matchMedia("(min-width: 1024px)").matches;
    if (wide) {
      // Just above the far ridge, whatever height the landscape takes on this screen.
      const land = sky.querySelector(".landscape").getBoundingClientRect().height;
      body.style.top = "auto";
      body.style.left = `${8 + 84 * q}%`;
      body.style.bottom = `${land * 0.55 + 20 + 120 * Math.sin(Math.PI * q)}px`;
    } else {
      body.style.bottom = "auto";
      body.style.left = `${82 + 8 * q}%`;
      body.style.top = `${9 - 2 * Math.sin(Math.PI * q)}%`;
    }

    // The "now" marker on the day strip, at the visitor's local time.
    const marker = document.querySelector(".day-now");
    if (marker) {
      marker.style.left = `${(h / 24) * 100}%`;
      marker.classList.remove("hidden");
    }

    const warm = period === "subuh" || period === "sore" || period === "senja";
    if (isMoon) {
      disc.style.background = "#f3f1e4";
      disc.style.webkitMaskImage = disc.style.maskImage = "radial-gradient(circle at 78% 30%, transparent 44%, #000 45%)";
      glow.style.background = "radial-gradient(circle, rgb(221 230 255 / .30), transparent 65%)";
    } else {
      disc.style.background = warm ? "#ffd08a" : "#fff4d6";
      disc.style.webkitMaskImage = disc.style.maskImage = "none";
      glow.style.background = "radial-gradient(circle, rgb(255 226 168 / .45), transparent 65%)";
    }
  }

  // Stars: generated once, each with its own twinkle pace.
  const stars = sky.querySelector(".sky-stars");
  const ns = "http://www.w3.org/2000/svg";
  let seed = 7;
  const rand = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
  for (let i = 0; i < 70; i++) {
    const c = document.createElementNS(ns, "circle");
    c.setAttribute("cx", `${rand() * 100}%`);
    c.setAttribute("cy", `${rand() * 55}%`);
    c.setAttribute("r", (0.6 + rand() * rand() * 1.6).toFixed(2));
    c.setAttribute("fill", "#fff");
    c.style.setProperty("--d", `${2 + rand() * 4}s`);
    c.style.setProperty("--delay", `${-rand() * 6}s`);
    stars.appendChild(c);
  }

  setInterval(updateSky, 60_000);
  window.addEventListener("resize", () => requestAnimationFrame(updateSky), { passive: true });

  // ---- Latest release -----------------------------------------------------------------------------

  let release = null;

  function renderRelease() {
    const line = document.querySelector("[data-release-line]");
    if (!release) {
      line.textContent = t("min_android");
      document.querySelector('[data-release="version"]').textContent = t("latest");
      return;
    }
    const size = release.size ? `${Math.round(release.size / 1024)} KB` : "±200 KB";
    const date = new Date(release.date).toLocaleDateString(lang === "en" ? "en-GB" : "id-ID", { day: "numeric", month: "long", year: "numeric" });
    line.textContent = `v${release.version} · ${size} · ${t("min_android")}`;
    document.querySelector('[data-release="version"]').textContent = release.version;
    document.querySelector('[data-release="size"]').textContent = size;
    document.querySelector('[data-release="date"]').textContent = date;
    document.querySelector('[data-release="sha"]').textContent = release.sha || "—";
  }

  fetch(`https://api.github.com/repos/${REPO}/releases/latest`, { headers: { Accept: "application/vnd.github+json" } })
    .then((r) => (r.ok ? r.json() : Promise.reject(r.status)))
    .then((json) => {
      const assets = json.assets || [];
      const apk = assets.find((a) => a.name === "waktu-sholat.apk") || assets.find((a) => a.name.endsWith(".apk"));
      if (!apk) return;
      release = {
        version: (json.tag_name || "").replace(/^v/, ""),
        date: json.published_at,
        size: apk.size,
        sha: (apk.digest || "").replace(/^sha256:/, ""),
      };
      document.querySelectorAll("[data-download]").forEach((a) => (a.href = apk.browser_download_url));
      renderRelease();
    })
    .catch(() => {});

  applyLanguage();
})();
