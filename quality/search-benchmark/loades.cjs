// Creates the scratch indexes with the PRODUCTION analyzers/mapping and bulk-loads the generated data.
const fs = require('fs');
const path = require('path');
const ES = process.env.ES || 'http://localhost:9299';
const RES = 'D:/Drive E/dev/app/search-service/src/main/resources/elasticsearch';
const data = path.join(__dirname, 'data');

async function call(method, url, body, type = 'application/json') {
  const r = await fetch(ES + url, { method, headers: { 'content-type': type }, body });
  const text = await r.text();
  if (!r.ok) throw new Error(method + ' ' + url + ' ' + r.status + ' ' + text.slice(0, 300));
  return text ? JSON.parse(text) : {};
}

(async () => {
  const settings = JSON.parse(fs.readFileSync(path.join(RES, 'settings.json'), 'utf8'));
  const restaurantMapping = JSON.parse(fs.readFileSync(path.join(RES, 'restaurant-mapping.json'), 'utf8'));
  // The production settings, with refresh off while loading (restored below)
  const loading = JSON.parse(JSON.stringify(settings));
  loading.index.refresh_interval = '-1';
  restaurantMapping.properties.createdAt = { type: 'date' };

  // Users: the same analyzers; a name searched as words and as word beginnings (like restaurants), email and phone as keywords + beginnings
  const userMapping = {
    dynamic: 'strict',
    properties: {
      id: { type: 'long' },
      fullName: { type: 'text', analyzer: 'folded', fields: { autocomplete: { type: 'text', analyzer: 'autocomplete', search_analyzer: 'folded' }, sort: { type: 'keyword', normalizer: 'folded' } } },
      email: { type: 'text', analyzer: 'folded', fields: { autocomplete: { type: 'text', analyzer: 'autocomplete', search_analyzer: 'folded' }, keyword: { type: 'keyword', normalizer: 'folded' } } },
      phone: { type: 'keyword' },
      role: { type: 'keyword' },
      status: { type: 'keyword' },
      createdAt: { type: 'date' },
    },
  };

  for (const [index, mapping] of [['bench_restaurant', restaurantMapping], ['bench_user', userMapping]]) {
    try { await call('DELETE', '/' + index); } catch { /* not there */ }
    await call('PUT', '/' + index, JSON.stringify({ settings: loading, mappings: mapping }));
  }

  const t0 = Date.now();
  for (const file of fs.readdirSync(data).filter((f) => f.endsWith('.ndjson')).sort()) {
    const res = await call('POST', '/_bulk', fs.readFileSync(path.join(data, file)), 'application/x-ndjson');
    if (res.errors) throw new Error('bulk errors in ' + file + ': ' + JSON.stringify(res.items.find((i) => i.index.error)).slice(0, 300));
  }
  for (const index of ['bench_restaurant', 'bench_user']) {
    await call('PUT', '/' + index + '/_settings', JSON.stringify({ index: { refresh_interval: '1s' } }));
    await call('POST', `/${index}/_forcemerge?max_num_segments=1`);
    await call('POST', `/${index}/_refresh`);
  }
  const counts = await call('GET', '/_cat/indices/bench_*?format=json&h=index,docs.count,store.size');
  console.log('loaded in', ((Date.now() - t0) / 1000).toFixed(0), 's', JSON.stringify(counts));
})().catch((e) => { console.error(e.message); process.exit(1); });
