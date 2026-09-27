# `world_cities.tsv` provenance

`app/src/main/assets/world_cities.tsv` lists cities outside Indonesia for the offline location
search: every national capital, state/province capitals with at least 50k people, and every city of
300k or more, each with coordinates and its IANA time zone.

Source: [GeoNames](https://www.geonames.org/) `cities15000.zip` and `admin1CodesASCII.txt`, licensed
under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Credited in the app's Settings.

Regenerate with `tools/gen_world_cities.py <output>` run next to the two extracted GeoNames files.
A handful of curated aliases (Mecca, Mekah, Medina, Al-Quds...) are added so the names people
actually type find the right city. Indonesian places come from `cities.csv` instead.
