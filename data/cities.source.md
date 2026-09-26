# `cities.csv` provenance

One row per Indonesian second-level administrative area (kota / kabupaten), used as the app's
offline location table. Coordinates are the administrative **seat** of the area, because that is
what published Kemenag prayer schedules are keyed to.

## Sources

| Field | Source |
|---|---|
| entity set, Indonesian name | Wikidata SPARQL — items with `P17` (country) = `Q252` (Indonesia) and `P31` subclass of kota (`Q192611`) / kabupaten (`Q6465`) / third-level administrative area (`Q15078489`), excluding provinces (`Q5098`) |
| province | Wikidata `P131` (located in administrative territorial entity), restricted to `P31` = `Q5098` (province of Indonesia) |
| raw coordinates | Wikidata `P625` (coordinate location) |
| seat correction | Wikidata `P36` (capital) → that item's `P625`; where a seat point was absent the regency centroid from `P625` was retained |
| seat cross-check | OpenStreetMap Overpass, `admin_level=5` relations for Indonesia (517 relations), compared against the Wikidata seat points |
| DKI Jakarta special case | the five `Kota Administrasi` plus `Kepulauan Seribu`, merged in separately as Wikidata models DKI at province level |

Endpoints:

- `https://query.wikidata.org/sparql`
- `https://overpass-api.de/api/interpreter` (and the `overpass.kumi.systems` mirror)

## Reproducing

The rows were harvested by SPARQL queries (entity + province + coordinates, then a `P36` seat pass
in QID-sized chunks), cross-checked against the OSM `admin_level=5` extraction, then normalised,
validated and written. The queries are recorded in this file rather than as a checked-in script
because the harvest is a one-off: the CSV is now the source of truth and `core`'s
`generateCityTable` task compiles it into the app.

## Normalisation applied to this file

1. `Kota `, `Kabupaten `, `Kab. `, `Kotamadya ` prefixes stripped.
2. Any name that repeated its own province in parentheses (for example `Bandung (Jawa Barat)`)
   lost the suffix, and the resulting kota/kabupaten pair under the same province was reduced to a
   single row. The two are 1–40 km apart and differ by at most two minutes, so one entry per name
   per province is enough for a schedule. This is asserted by
   `CityTest.namesCarryNoDisambiguatingSuffix` and `CityTest.cityNamesAreUniqueWithinAProvince`.
3. `tz` assigned per province, not by longitude band: the bands do not separate
   Kalimantan Barat/Tengah (WIB) from Kalimantan Selatan/Timur/Utara (WITA), which the table now
   gets right.

## Shape

`name,province,lat,lng,tz` — no BOM, LF line endings, `tz` is a whole-hour UTC offset
(7 = WIB, 8 = WITA, 9 = WIT).

## Validation

Checked by `CityTest` on every build:

- `>= 400` rows, and every row has a non-blank name and province
- `tz` in `7..9`
- no duplicate `(name, province)`
- every point is `City.isServiceable`, i.e. within 250 km of itself, and the whole table stays
  inside Indonesia (lat −10.73..5.89, lng 95.32..140.96)

Prayer times computed from these coordinates were compared against Aladhan and the published
Kemenag schedule in `PrayerCalculatorTest`, and agree to the published minute.
