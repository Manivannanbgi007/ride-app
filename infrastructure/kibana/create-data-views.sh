#!/bin/sh
# Kibana data views for search-service's indexes, so Discover can show them right away.
# Run by the kibana-setup container once Kibana is up; safe to run again (existing views are replaced).
set -e
KIBANA=http://kibana:5601

for index in restaurant food category; do
  curl -fsS -X POST "$KIBANA/api/data_views/data_view" \
    -H "kbn-xsrf: true" \
    -H "Content-Type: application/json" \
    -d "{\"data_view\": {\"id\": \"$index\", \"title\": \"$index\", \"name\": \"$index (search-service)\"}, \"override\": true}" \
    > /dev/null
  echo "Kibana data view '$index' ready"
done
