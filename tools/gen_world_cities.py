"""Builds app/src/main/assets/world_cities.tsv from GeoNames (CC BY 4.0).

Keeps cities outside Indonesia with population >= 300k, national capitals and state capitals
(>= 50k), with a few curated aliases people actually type (Makkah, Madinah...). Format:
  #tz <tab-separated IANA zones>
  #rg <tab-separated "CC|State"> (index 0 = none)
  name <tab> aliases(|) <tab> region index <tab> lat <tab> lng <tab> zone index
"""
import io, sys
admin = {}
for line in open('admin1.txt', encoding='utf-8'):
    p = line.rstrip('\n').split('\t')
    if len(p) >= 3:
        admin[p[0]] = p[2]
ALIASES = {'Makkah': 'Mecca|Mekah|Mekkah', 'Madinah': 'Medina|Madinah al-Munawwarah', 'Jeddah': 'Jiddah|Jedah',
           'Jerusalem': 'Al-Quds|Baitul Maqdis', 'Cairo': 'Kairo|Kaherah', 'Riyadh': 'Riyad', 'Singapore': 'Singapura',
           'Bangkok': 'Krung Thep', 'Ta’if': 'Taif|Thaif', 'Tokyo': 'Tokio', 'Seoul': 'Seul', 'Moscow': 'Moskow|Moskva',
           'Beijing': 'Peking'}
rows = []; tzs = {}; regions = {'': 0}
for line in open('cities15000.txt', encoding='utf-8'):
    c = line.rstrip('\n').split('\t')
    name, lat, lng, code, cc, a1, pop, tz = c[1], float(c[4]), float(c[5]), c[7], c[8], c[10], int(c[14] or 0), c[17]
    if cc == 'ID' or not tz:
        continue
    if not (pop >= 300000 or code == 'PPLC' or (code == 'PPLA' and pop >= 50000) or name in ALIASES):
        continue
    state = admin.get(f'{cc}.{a1}', '')
    if state == name:
        state = ''
    key = f'{cc}|{state}'
    ri = regions.setdefault(key, len(regions))
    ti = tzs.setdefault(tz, len(tzs))
    fmt = lambda v: f'{v:.3f}'.rstrip('0').rstrip('.')
    rows.append((pop, name, ALIASES.get(name, ''), ri, fmt(lat), fmt(lng), ti))
rows.sort(key=lambda r: -r[0])
seen = set(); out = io.StringIO()
out.write('#tz\t' + '\t'.join(sorted(tzs, key=tzs.get)) + '\n')
out.write('#rg\t' + '\t'.join(k for k in sorted(regions, key=regions.get) if k) + '\n')
n = 0
for pop, name, al, ri, lat, lng, ti in rows:
    if (name, ri) in seen:
        continue
    seen.add((name, ri)); n += 1
    out.write(f'{name}\t{al}\t{ri}\t{lat}\t{lng}\t{ti}\n')
data = out.getvalue()
open(sys.argv[1], 'w', encoding='utf-8', newline='\n').write(data)
import zlib
print(n, 'rows', len(data) // 1024, 'KB raw', len(zlib.compress(data.encode(), 9)) // 1024, 'KB deflate')
