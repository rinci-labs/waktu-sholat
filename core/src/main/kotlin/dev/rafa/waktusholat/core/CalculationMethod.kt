package dev.rafa.waktusholat.core

/**
 * Built-in calculation methods, numbered to match the Aladhan/PrayTimes convention. The app
 * defaults to [KEMENAG], which is what Indonesian users expect, and keeps the rest for travel.
 */
enum class CalculationMethod(
    val id: Int,
    val label: String,
    val region: String,
    val config: CalculationConfig,
) {
    KEMENAG(20, "Kementerian Agama RI", "Indonesia", CalculationConfig(fajrAngle = 20.0, ishaAngle = 18.0)),
    SINGAPORE(11, "Majlis Ugama Islam Singapura", "Singapura", CalculationConfig(fajrAngle = 20.0, ishaAngle = 18.0)),
    JAKIM(17, "JAKIM", "Malaysia", CalculationConfig(fajrAngle = 20.0, ishaAngle = 18.0)),
    MAKKAH(4, "Umm Al-Qura", "Arab Saudi", CalculationConfig(fajrAngle = 18.5, ishaIntervalMinutes = 90)),
    MWL(3, "Muslim World League", "Internasional", CalculationConfig(fajrAngle = 18.0, ishaAngle = 17.0)),
    ISNA(2, "ISNA", "Amerika Utara", CalculationConfig(fajrAngle = 15.0, ishaAngle = 15.0)),
    EGYPT(5, "Egyptian General Authority", "Afrika, Timur Tengah", CalculationConfig(fajrAngle = 19.5, ishaAngle = 17.5)),
    KARACHI(1, "University of Karachi", "Pakistan, India", CalculationConfig(fajrAngle = 18.0, ishaAngle = 18.0)),
    TURKEY(13, "Diyanet", "Turki", CalculationConfig(fajrAngle = 18.0, ishaAngle = 17.0)),
    TEHRAN(7, "University of Tehran", "Iran", CalculationConfig(fajrAngle = 17.7, ishaAngle = 14.0, maghribAngle = 4.5)),
    JAFARI(0, "Shia Ithna-Ashari", "Syiah", CalculationConfig(fajrAngle = 16.0, ishaAngle = 14.0, maghribAngle = 4.0));

    companion object {
        val DEFAULT = KEMENAG

        fun fromId(id: Int): CalculationMethod = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Madhab choice, which only changes Asr (the shadow-length factor). */
enum class Madhab(val label: String, val asrFactor: Double) {
    SHAFII("Syafi'i", 1.0),
    HANAFI("Hanafi", 2.0);

    companion object {
        val DEFAULT = SHAFII

        fun fromId(id: Int): Madhab = entries.firstOrNull { it.ordinal == id } ?: DEFAULT
    }
}
