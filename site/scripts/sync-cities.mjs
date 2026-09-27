// Regenerates public/cities.json from the app's city data, so the site builds from site/ alone.
// Run after changing data/cities.csv or app/src/main/assets/world_cities.tsv:  npm run sync-cities
// Output: { zones: IANA ids, regions: "CC|State", cities: [name, aliases, region, lat, lng, zone] }.
import { readFileSync, writeFileSync } from "node:fs";

const root = new URL("../../", import.meta.url);
const read = (path) => readFileSync(new URL(path, root), "utf-8").replace(/\r/g, "");

const lines = read("app/src/main/assets/world_cities.tsv").trimEnd().split("\n");
const zones = lines[0].split("\t").slice(1);
const regions = ["", ...lines[1].split("\t").slice(1)];
const index = (list, v) => (list.includes(v) ? list.indexOf(v) : list.push(v) - 1);
const INDONESIA = { 7: "Asia/Jakarta", 8: "Asia/Makassar", 9: "Asia/Jayapura" };

// Indonesia first: most visitors are there, so ties in search rank its cities higher.
const cities = read("data/cities.csv").trimEnd().split("\n").slice(1).map((line) => {
  const [name, province, lat, lng, tz] = line.split(",");
  return [name, "", index(regions, `ID|${province}`), Number(lat), Number(lng), index(zones, INDONESIA[tz])];
});
for (const line of lines.slice(2)) {
  const [name, aliases, region, lat, lng, zone] = line.split("\t");
  cities.push([name, aliases, Number(region), Number(lat), Number(lng), Number(zone)]);
}

writeFileSync(new URL("../public/cities.json", import.meta.url), JSON.stringify({ zones, regions, cities }));
console.log(`public/cities.json: ${cities.length} cities`);
