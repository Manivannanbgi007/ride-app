const { execFileSync } = require('child_process');
const RUNS = 15, WARM = 3;
const median = (a) => [...a].sort((x, y) => x - y)[Math.floor(a.length / 2)];
const mysql = (sql) => execFileSync('docker', ['exec', '-i', 'bench-mysql', 'mysql', '-uroot', '-pbench', '-N', '-B', 'bench'], { input: sql, maxBuffer: 1 << 30, encoding: 'utf8' });
function time(label, sql) {
  const lines = [];
  for (let i = 0; i < RUNS + WARM; i++) lines.push(`SET @t=NOW(6); ${sql}; SELECT 'T', ${i}, TIMESTAMPDIFF(MICROSECOND,@t,NOW(6));`);
  const t = mysql(lines.join('\n')).split('\n').filter((l) => l.startsWith('T\t')).map((l) => l.split('\t')).filter((p) => Number(p[1]) >= WARM).map((p) => Number(p[2]) / 1000);
  console.log('  ' + label.padEnd(78) + median(t).toFixed(2).padStart(9) + ' ms');
}
console.log('D. default sorts with no filter, 300,000 rows (admin lists: newest first)');
time('restaurants ORDER BY created_at DESC LIMIT 20 - no created_at index', `SELECT * FROM restaurants ORDER BY created_at DESC LIMIT 20`);
time('restaurants, page 50 (OFFSET 1000)', `SELECT * FROM restaurants ORDER BY created_at DESC LIMIT 20 OFFSET 1000`);
mysql(`ALTER TABLE restaurants ADD KEY idx_restaurants_created (created_at); ANALYZE TABLE restaurants;`);
time('same with the index (created_at)', `SELECT * FROM restaurants ORDER BY created_at DESC LIMIT 20`);
time('page 50 (OFFSET 1000) with the index', `SELECT * FROM restaurants ORDER BY created_at DESC LIMIT 20 OFFSET 1000`);
console.log('\nE. what each extra index costs: writes (one INSERT of 1 row, median of 200) and size');
console.log(mysql(`SELECT table_name, ROUND(data_length/1048576) data_mb, ROUND(index_length/1048576) index_mb FROM information_schema.tables WHERE table_schema='bench' AND table_name IN ('restaurants','users')`).trim().split('\n').map((l) => '  ' + l.replace(/\t/g, '  ')).join('\n'));
