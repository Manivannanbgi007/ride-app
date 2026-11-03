const { execFileSync } = require('child_process');
const RUNS = 15, WARM = 3;
const median = (a) => [...a].sort((x, y) => x - y)[Math.floor(a.length / 2)];
const mysql = (sql) => execFileSync('docker', ['exec', '-i', 'bench-mysql', 'mysql', '-uroot', '-pbench', '-N', '-B', 'bench'], { input: sql, maxBuffer: 1 << 30, encoding: 'utf8' });
function time(label, sql) {
  const lines = [];
  for (let i = 0; i < RUNS + WARM; i++) lines.push(`SET @t=NOW(6); ${sql}; SELECT 'T', ${i}, TIMESTAMPDIFF(MICROSECOND,@t,NOW(6));`);
  const t = mysql(lines.join('\n')).split('\n').filter((l) => l.startsWith('T\t')).map((l) => l.split('\t')).filter((p) => Number(p[1]) >= WARM).map((p) => Number(p[2]) / 1000);
  console.log('  ' + label.padEnd(76) + median(t).toFixed(2).padStart(9) + ' ms');
}
console.log('C. MySQL FULLTEXT (ngram) vs LIKE, 300,000 restaurants / 500,000 users');
time('LIKE (today): restaurants "kitchen", page', `SELECT * FROM restaurants WHERE status='ACTIVE' AND LOWER(name) LIKE '%kitchen%' ORDER BY name LIMIT 20`);
time('FULLTEXT ngram: restaurants "kitchen", page', `SELECT * FROM restaurants WHERE status='ACTIVE' AND MATCH(name) AGAINST('"kitchen"' IN BOOLEAN MODE) ORDER BY name LIMIT 20`);
time('FULLTEXT ngram: restaurants "kitchen", count', `SELECT COUNT(*) FROM restaurants WHERE status='ACTIVE' AND MATCH(name) AGAINST('"kitchen"' IN BOOLEAN MODE)`);
time('LIKE (today): users "priya", count', `SELECT COUNT(*) FROM users WHERE LOWER(full_name) LIKE '%priya%' OR LOWER(email) LIKE '%priya%' OR phone LIKE '%priya%'`);
time('FULLTEXT ngram: users "priya", count (name+email; phone still LIKE)', `SELECT COUNT(*) FROM users WHERE MATCH(full_name, email) AGAINST('"priya"' IN BOOLEAN MODE) OR phone LIKE '%priya%'`);
time('FULLTEXT ngram: users "priya", count (name+email only)', `SELECT COUNT(*) FROM users WHERE MATCH(full_name, email) AGAINST('"priya"' IN BOOLEAN MODE)`);
console.log('\nPlan of the FULLTEXT query:');
console.log(mysql(`EXPLAIN SELECT * FROM restaurants WHERE status='ACTIVE' AND MATCH(name) AGAINST('"kitchen"' IN BOOLEAN MODE) ORDER BY name LIMIT 20`).trim().split('\n').map((l) => '  ' + l.replace(/\t/g, ' | ')).join('\n'));
console.log('\nPlan today (LIKE):');
console.log(mysql(`EXPLAIN SELECT * FROM restaurants WHERE status='ACTIVE' AND LOWER(name) LIKE '%kitchen%' ORDER BY name LIMIT 20`).trim().split('\n').map((l) => '  ' + l.replace(/\t/g, ' | ')).join('\n'));
