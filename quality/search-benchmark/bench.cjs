// Compares the query shapes of the application on MySQL (as Spring Data generates them) with Elasticsearch (as
// search-service builds them), on identical data. Prints median and p95 in milliseconds.
const { execFileSync } = require('child_process');
const ES = process.env.ES || 'http://localhost:9299';
const RUNS = Number(process.env.RUNS || 25);
const WARM = 5;

const median = (a) => [...a].sort((x, y) => x - y)[Math.floor(a.length / 2)];
const p95 = (a) => [...a].sort((x, y) => x - y)[Math.min(a.length - 1, Math.floor(a.length * 0.95))];
const fmt = (n) => n.toFixed(n < 10 ? 2 : 1).padStart(8);

// ---------- MySQL: every query is timed inside one session (no client start-up in the numbers)
function mysqlBench(cases) {
  const lines = [];
  for (const c of cases) {
    for (let i = 0; i < RUNS + WARM; i++) {
      lines.push(`SET @t=NOW(6); ${c.sql}; SELECT 'T', '${c.label}', ${i}, TIMESTAMPDIFF(MICROSECOND,@t,NOW(6));`);
    }
  }
  const out = execFileSync('docker', ['exec', '-i', 'bench-mysql', 'mysql', '-uroot', '-pbench', '-N', '-B', 'bench'], {
    input: lines.join('\n'), maxBuffer: 1 << 30, encoding: 'utf8',
  });
  const times = {};
  for (const line of out.split('\n')) {
    const p = line.split('\t');
    if (p[0] === 'T') (times[p[1]] ??= []).push([Number(p[2]), Number(p[3]) / 1000]);
  }
  return Object.fromEntries(Object.entries(times).map(([k, v]) => [k, v.filter(([i]) => i >= WARM).map(([, t]) => t)]));
}

// ---------- Elasticsearch: server time (took) and wall time (HTTP + JSON) per search
async function esBench(cases) {
  const result = {};
  for (const c of cases) {
    const took = [];
    const wall = [];
    let total = null;
    for (let i = 0; i < RUNS + WARM; i++) {
      const t0 = performance.now();
      const r = await fetch(`${ES}/${c.index}/_search`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(c.body) });
      const j = await r.json();
      const ms = performance.now() - t0;
      if (!r.ok) throw new Error(c.label + ': ' + JSON.stringify(j).slice(0, 300));
      total = j.hits.total.value;
      if (i >= WARM) { took.push(j.took); wall.push(ms); }
    }
    result[c.label] = { took, wall, total };
  }
  return result;
}

const term = (field, value) => ({ term: { [field]: value } });
const restaurantText = (q, extra = []) => ({
  track_total_hits: true, size: 20,
  query: { bool: { filter: [term('status', 'ACTIVE'), ...extra], must: [{ bool: { minimum_should_match: 1, should: [
    { multi_match: { query: q, fields: ['name^3', 'description', 'cuisines.text'], fuzziness: 'AUTO', prefix_length: 1 } },
    { match: { 'name.autocomplete': { query: q, operator: 'and' } } },
  ] } }] } },
});
const userText = (q, extra = []) => ({
  track_total_hits: true, size: 20, sort: [{ createdAt: 'desc' }],
  query: { bool: { filter: extra, must: [{ bool: { minimum_should_match: 1, should: [
    { match: { 'fullName.autocomplete': { query: q, operator: 'and' } } },
    { match: { 'email.autocomplete': { query: q, operator: 'and' } } },
    { prefix: { phone: q } },
  ] } }] } },
});

(async () => {
  // (label, MySQL as the application sends it, Elasticsearch as search-service sends it)
  const pairs = [
    ['R1 browse "kitchen" (common word, ~10% of rows), sort name',
      `SELECT * FROM restaurants WHERE status='ACTIVE' AND LOWER(name) LIKE '%kitchen%' ORDER BY name LIMIT 20`,
      { index: 'bench_restaurant', body: { ...restaurantText('kitchen'), sort: [{ 'name.sort': 'asc' }] } }],
    ['R2 browse "saffron" (rarer word), sort name',
      `SELECT * FROM restaurants WHERE status='ACTIVE' AND LOWER(name) LIKE '%saffron%' ORDER BY name LIMIT 20`,
      { index: 'bench_restaurant', body: { ...restaurantText('saffron'), sort: [{ 'name.sort': 'asc' }] } }],
    ['R3 browse cuisine "chinese" in one city, newest first',
      `SELECT * FROM restaurants WHERE status='ACTIVE' AND city='Bengaluru' AND LOWER(cuisines) LIKE '%chinese%' ORDER BY created_at DESC LIMIT 20`,
      { index: 'bench_restaurant', body: { track_total_hits: true, size: 20, sort: [{ createdAt: 'desc' }], query: { bool: { filter: [term('status', 'ACTIVE'), term('city', 'Bengaluru'), term('cuisines', 'Chinese')] } } } }],
    ['R4 browse by city only (an index exists), sort name',
      `SELECT * FROM restaurants WHERE status='ACTIVE' AND city='Mumbai' ORDER BY name LIMIT 20`,
      { index: 'bench_restaurant', body: { track_total_hits: true, size: 20, sort: [{ 'name.sort': 'asc' }], query: { bool: { filter: [term('status', 'ACTIVE'), term('city', 'Mumbai')] } } } }],
    ['R5 admin list, no text: newest first (index exists)',
      `SELECT * FROM restaurants WHERE status='ACTIVE' ORDER BY created_at DESC LIMIT 20`,
      { index: 'bench_restaurant', body: { track_total_hits: true, size: 20, sort: [{ createdAt: 'desc' }], query: { bool: { filter: [term('status', 'ACTIVE')] } } } }],
    ['R6 admin text "saffron" in name, legal name or city, newest first',
      `SELECT * FROM restaurants WHERE LOWER(name) LIKE '%saffron%' OR LOWER(legal_business_name) LIKE '%saffron%' OR LOWER(city) LIKE '%saffron%' ORDER BY created_at DESC LIMIT 20`,
      { index: 'bench_restaurant', body: { ...restaurantText('saffron').query ? { track_total_hits: true, size: 20, sort: [{ createdAt: 'desc' }], query: { multi_match: { query: 'saffron', fields: ['name', 'city'], fuzziness: 'AUTO' } } } : {} } }],
    ['U1 admin users "priya" (name, email or phone), newest first',
      `SELECT * FROM users WHERE LOWER(full_name) LIKE '%priya%' OR LOWER(email) LIKE '%priya%' OR phone LIKE '%priya%' ORDER BY created_at DESC LIMIT 20`,
      { index: 'bench_user', body: userText('priya') }],
    ['U2 admin users: an email fragment "sharma.12"',
      `SELECT * FROM users WHERE LOWER(full_name) LIKE '%sharma.12%' OR LOWER(email) LIKE '%sharma.12%' OR phone LIKE '%sharma.12%' ORDER BY created_at DESC LIMIT 20`,
      { index: 'bench_user', body: userText('sharma.12') }],
    ['U3 admin users: role + status, newest first (index exists)',
      `SELECT * FROM users WHERE role='CUSTOMER' AND status='ACTIVE' ORDER BY created_at DESC LIMIT 20`,
      { index: 'bench_user', body: { track_total_hits: true, size: 20, sort: [{ createdAt: 'desc' }], query: { bool: { filter: [term('role', 'CUSTOMER'), term('status', 'ACTIVE')] } } } }],
  ];
  // The page counts that go with every page of results ("Showing 1-20 of N"), and MySQL alternatives
  const counts = [
    ['R1c count for R1', `SELECT COUNT(*) FROM restaurants WHERE status='ACTIVE' AND LOWER(name) LIKE '%kitchen%'`],
    ['R3c count for R3', `SELECT COUNT(*) FROM restaurants WHERE status='ACTIVE' AND city='Bengaluru' AND LOWER(cuisines) LIKE '%chinese%'`],
    ['U1c count for U1', `SELECT COUNT(*) FROM users WHERE LOWER(full_name) LIKE '%priya%' OR LOWER(email) LIKE '%priya%' OR phone LIKE '%priya%'`],
    ['R1-nolower: R1 without LOWER() (the column is already case-insensitive)', `SELECT * FROM restaurants WHERE status='ACTIVE' AND name LIKE '%kitchen%' ORDER BY name LIMIT 20`],
    ['U1-nolower: U1 without LOWER()', `SELECT * FROM users WHERE full_name LIKE '%priya%' OR email LIKE '%priya%' OR phone LIKE '%priya%' ORDER BY created_at DESC LIMIT 20`],
  ];
  const mysql = mysqlBench([...pairs.map(([label, sql]) => ({ label, sql })), ...counts.map(([label, sql]) => ({ label, sql }))]);
  const es = await esBench(pairs.map(([label, , q]) => ({ label, ...q })));

  console.log(`\n${RUNS} runs each (after ${WARM} warm-up runs), milliseconds; ES = server "took" / wall incl. HTTP\n`);
  console.log('case'.padEnd(72) + 'MySQL med   p95  |  ES took med  p95   ES wall med | ES hits');
  for (const [label] of pairs) {
    const m = mysql[label];
    const e = es[label];
    console.log(label.slice(0, 70).padEnd(72) + `${fmt(median(m))} ${fmt(p95(m))}  |  ${fmt(median(e.took))} ${fmt(p95(e.took))}   ${fmt(median(e.wall))}   | ${e.total}`);
  }
  console.log('\nMySQL extras');
  for (const [label] of counts) console.log(label.slice(0, 70).padEnd(72) + `${fmt(median(mysql[label]))} ${fmt(p95(mysql[label]))}`);
})().catch((e) => { console.error(e); process.exit(1); });
