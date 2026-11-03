package com.fooddelivery.searchservice.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.searchservice.document.CategoryDocument;
import com.fooddelivery.searchservice.document.FoodDocument;
import com.fooddelivery.searchservice.document.RestaurantDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.stereotype.Component;

/**
 * Creates the three indexes (restaurant, food, category) with their settings and mappings when they do not
 * exist yet. Runs while the application starts, before the Kafka listeners: an event never lands in an index
 * that Elasticsearch would otherwise create with guessed field types.
 * <p>
 * An index that already exists is kept with all its documents. Fields added to the mapping files after it was created
 * (for example the ratings) are added to it with a put-mapping call; that only ever adds fields, so nothing is
 * reindexed and nothing is lost. The mappings are "strict": without this step a document with a new field would be
 * rejected by an old index.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexes implements InitializingBean {

    public static final List<Class<?>> DOCUMENT_TYPES =
            List.of(RestaurantDocument.class, FoodDocument.class, CategoryDocument.class);

    private static final String PROPERTIES = "properties";

    private final ElasticsearchOperations operations;

    @Override
    public void afterPropertiesSet() {
        for (Class<?> documentType : DOCUMENT_TYPES) {
            IndexOperations index = operations.indexOps(documentType);
            if (!index.exists()) {
                index.createWithMapping();
                log.info("Search index created. index={}", index.getIndexCoordinates().getIndexName());
            } else {
                addMissingFields(index, documentType);
            }
        }
    }

    /**
     * Adds the fields of the document type's mapping file that the existing index does not have yet. Existing fields
     * are never touched (changing a field's type needs a new index), so this is safe to run on every start.
     *
     * @return the names of the fields that were added
     */
    List<String> addMissingFields(IndexOperations index, Class<?> documentType) {
        Map<String, Object> existing = propertiesOf(index.getMapping());
        Map<String, Object> missing = new LinkedHashMap<>();
        propertiesOf(index.createMapping(documentType)).forEach((name, definition) -> {
            if (!existing.containsKey(name)) {
                missing.put(name, definition);
            }
        });
        if (!missing.isEmpty()) {
            index.putMapping(Document.from(Map.of(PROPERTIES, missing)));
            log.info("Fields added to the mapping of an existing search index. index={} fields={}",
                    index.getIndexCoordinates().getIndexName(), missing.keySet());
        }
        return List.copyOf(missing.keySet());
    }

    private static Map<String, Object> propertiesOf(Map<String, Object> mapping) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (mapping.get(PROPERTIES) instanceof Map<?, ?> fields) {
            fields.forEach((name, definition) -> properties.put(String.valueOf(name), definition));
        }
        return properties;
    }
}
