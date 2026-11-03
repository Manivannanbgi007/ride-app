# MySQL vs Elasticsearch benchmark

Measures the application's real query shapes on **throwaway containers** (never the development data):
MySQL on port 3399 (`bench-mysql`) and Elasticsearch on port 9299 (`bench-es`), loaded with the same
generated rows (300k restaurants, 500k users) and the production analyzers and mappings of search-service.

```bash
node gen.cjs                       # writes ./data (about 320 MB, git-ignored; delete it afterwards)
# start the two containers (mysql:8.4 loaded with schema.sql, elasticsearch with the production analysis settings)
node loades.cjs                    # creates the indexes and bulk-loads them
node bench.cjs                     # text search, filters, sorts: median and p95 per query shape
node bench2.cjs ; node bench2c.cjs # missing-index sorts, MySQL FULLTEXT (ngram)
node bench3.cjs                    # how the text search grows with the number of rows
docker rm -f bench-mysql bench-es
```

Re-run it when row counts grow, to decide whether a MySQL list should move to Elasticsearch.
