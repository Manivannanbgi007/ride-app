package com.fooddelivery.adminservice.repository;

import java.util.List;

import com.fooddelivery.adminservice.entity.AuditRecord;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.support.PageableExecutionUtils;

/** One page of matching records plus the total count, with JPA Criteria (sorting fields are validated by the caller). */
class AuditRecordSearchImpl implements AuditRecordSearch {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<AuditRecord> search(Specification<AuditRecord> specification, Pageable pageable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();

        CriteriaQuery<AuditRecord> query = builder.createQuery(AuditRecord.class);
        Root<AuditRecord> root = query.from(AuditRecord.class);
        query.where(specification.toPredicate(root, query, builder));
        query.orderBy(pageable.getSort().stream()
                .map(order -> toOrder(order, root, builder))
                .toList());
        List<AuditRecord> content = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        return PageableExecutionUtils.getPage(content, pageable, () -> {
            CriteriaQuery<Long> count = builder.createQuery(Long.class);
            Root<AuditRecord> countRoot = count.from(AuditRecord.class);
            count.select(builder.count(countRoot)).where(specification.toPredicate(countRoot, count, builder));
            return entityManager.createQuery(count).getSingleResult();
        });
    }

    private static Order toOrder(Sort.Order order, Root<AuditRecord> root, CriteriaBuilder builder) {
        return order.isAscending() ? builder.asc(root.get(order.getProperty())) : builder.desc(root.get(order.getProperty()));
    }
}
