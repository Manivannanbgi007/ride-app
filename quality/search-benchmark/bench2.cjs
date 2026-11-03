// Round 2: how text search scales with table size, the default browse page, and MySQL-side alternatives.
const { execFileSync } = require('child_process');
const RUNS = 15;
const WARM = 3;
const median = (a) => [...a].sort((x, y) => x - y)[Math.floor(a.length / 2)];
const mysql = (sql) => execFileSync('docker', ['exec', '-i', 'bench-mysql', 'mysql', '-uroot', '-pbench', '-N', '-B', 'bench'], { input: sql, maxBuffer: 1 << 30, encoding: 'utf8' });

function time(label, sql) {
  const lines = [];
  for (let i = 0; i < RUNS + WARM; i++) lines.push(`SET @t=NOW(6); ${sql}; SELECT 'T', ${i}, TIMESTAMPDIFF(MICROSECOND,@t,NOW(6));`);
  const t = mysql(lines.join('\n')).split('\n').filter((l) => l.startsWith('T\t')).map((l) => l.split('\t')).filter((p) => Number(p[1]) >= WARM).map((p) => Number(p[2]) / 1000);
  return { label, ms: median(t) };
}
const show = (rows) => rows.forEach((r) => console.log('  ' + r.label.padEnd(76) + r.ms.toFixed(2).padStart(9) + ' ms'));

// ---- A. scale: the same text search on 10k / 50k / 100k / 300k restaurants (page + count)
console.log('\nA. "contains" text search (page + total count) by table size, MySQL');
mysql(`DROP TABLE IF EXISTS r10k, r50k, r100k; CREATE TABLE r10k LIKE restaurants; CREATE TABLE r50k LIKE restaurants; CREATE TABLE r100k LIKE restaurants;
INSERT INTO r10k SELECT * FROM restaurants WHERE id<=10000; INSERT INTO r50k SELECT * FROM restaurants WHERE id<=50000; INSERT INTO r100k SELECT * FROM restaurants WHERE id<=100000;`);
for (const [table, rows] of [['r10k', '10,000'], ['r50k', '50,000'], ['r100k', '100,000'], ['restaurants', '300,000']]) {
  const page = time('', `SELECT * FROM ${table} WHERE status='ACTIVE' AND LOWER(name) LIKE '%kitchen%' ORDER BY name LIMIT 20`);
  const count = time('', `SELECT COUNT(*) FROM ${table} WHERE status='ACTIVE' AND LOWER(name) LIKE '%kitchen%'`);
  show([{ label: `${rows.padStart(7)} rows: page ${page.ms.toFixed(1)} ms + count ${count.ms.toFixed(1)} ms`, ms: page.ms + count.ms }]);
}

// ---- B. the default browse page of every visitor (no filter, sorted by name) and the index that fixes it
console.log('\nB. default browse (ACTIVE restaurants, A-Z), 300,000 rows');
show([
  time('page: ORDER BY name LIMIT 20, no index on (status, name)', `SELECT * FROM restaurants WHERE status='ACTIVE' ORDER BY name LIMIT 20`),
  time('page 50 (offset 1000)', `SELECT * FROM restaurants WHERE status='ACTIVE' ORDER BY name LIMIT 20 OFFSET 1000`),
  time('count: WHERE status=ACTIVE', `SELECT COUNT(*) FROM restaurants WHERE status='ACTIVE'`),
]);
mysql(`ALTER TABLE restaurants ADD KEY idx_restaurants_status_name (status, name); ANALYZE TABLE restaurants;`);
show([
  time('page with the new index (status, name)', `SELECT * FROM restaurants WHERE status='ACTIVE' ORDER BY name LIMIT 20`),
  time('page 50 (offset 1000) with the new index', `SELECT * FROM restaurants WHERE status='ACTIVE' ORDER BY name LIMIT 20 OFFSET 1000`),
  time('count with the new index', `SELECT COUNT(*) FROM restaurants WHERE status='ACTIVE'`),
]);

// ---- C. MySQL-side text alternatives: FULLTEXT with the ngram parser
console.log('\nC. MySQL alternatives for "contains" search, 300,000 restaurants / 500,000 users');
const t0 = Date.now();
mysql(`ALTER TABLE restaurants ADD FULLTEXT KEY ft_restaurants_name (name) WITH PARSER ngram; ALTER TABLE users ADD FULLTEXT KEY ft_users_text (full_name, email) WITH PARSER ngram;`);
console.log(`  (building the two FULLTEXT ngram indexes took ${((Date.now() - t0) / 1000).toFixed(0)} s)`);
mysql('SET GLOBAL innodb_ft_min_token_size=2;');
show([
  time('LIKE (today): "kitchen", page', `SELECT * FROM restaurants WHERE status='ACTIVE' AND LOWER(name) LIKE '%kitchen%' ORDER BY name LIMIT 20`),
  time('FULLTEXT ngram BOOLEAN MODE: "kitchen", page', `SELECT * FROM restaurants WHERE status='ACTIVE' AND MATCH(name) AGAINST('"kitchen"' IN BOOLEAN MODE) ORDER BY name LIMIT 20`),
  time('FULLTEXT ngram: count', `SELECT COUNT(*) FROM restaurants WHERE status='ACTIVE' AND MATCH(name) AGAINST('"kitchen"' IN BOOLEAN MODE)`),
  time('LIKE (today): users "priya" count', `SELECT COUNT(*) FROM users WHERE LOWER(full_name) LIKE '%priya%' OR LOWER(email) LIKE '%priya%' OR phone LIKE '%priya%'`),
  time('FULLTEXT ngram: users "priya" count (name+email; phone stays LIKE)', `SELECT COUNT(*) FROM users WHERE MATCH(full_name, email) AGAINST('"priya"' IN BOOLEAN MODE) OR phone LIKE '%priya%'`),
]);
console.log(mysql(`EXPLAIN SELECT * FROM restaurants WHERE status='ACTIVE' AND MATCH(name) AGAINST('"kitchen"' IN BOOLEAN MODE) ORDER BY name LIMIT 20`).trim().split('\n').map((l) => '  plan: ' + l.replace(/\t/g, ' | ')).join('\n'));
console.log('\nplan today (LIKE):');
console.log(mysql(`EXPLAIN SELECT * FROM restaurants WHERE status='ACTIVE' AND LOWER(name) LIKE '%kitchen%' ORDER BY name LIMIT 20`).trim().split('\n').map((l) => '  ' + l.replace(/\t/g, ' | ')).join('\n'));
