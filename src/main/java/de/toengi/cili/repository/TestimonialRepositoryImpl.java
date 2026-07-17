package de.toengi.cili.repository;

import de.toengi.cili.model.entity.Testimonial;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

public class TestimonialRepositoryImpl implements TestimonialRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public Page<Testimonial> searchLike(List<String> terms, Pageable pageable) {
        String where = buildWhere(terms);
        Query dataQ = em.createNativeQuery(
            "SELECT * FROM testimonials " + where + " ORDER BY created_at DESC",
            Testimonial.class);
        Query countQ = em.createNativeQuery(
            "SELECT COUNT(*) FROM testimonials " + where);
        bindParams(dataQ, terms);
        bindParams(countQ, terms);
        dataQ.setFirstResult((int) pageable.getOffset());
        dataQ.setMaxResults(pageable.getPageSize());
        List<Testimonial> rows = dataQ.getResultList();
        long total = ((Number) countQ.getSingleResult()).longValue();
        return new PageImpl<>(rows, pageable, total);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Testimonial> searchLikeTop(List<String> terms, int max) {
        Query query = em.createNativeQuery(
            "SELECT * FROM testimonials " + buildWhere(terms) + " ORDER BY created_at DESC",
            Testimonial.class);
        bindParams(query, terms);
        query.setMaxResults(max);
        return query.getResultList();
    }

    private String buildWhere(List<String> terms) {
        if (terms.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("WHERE ");
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) sb.append(" AND ");
            int base = i * 3 + 1;
            sb.append("(LOWER(author_name) LIKE ?").append(base)
              .append(" OR LOWER(text) LIKE ?").append(base + 1)
              .append(" OR LOWER(tags) LIKE ?").append(base + 2).append(")");
        }
        return sb.toString();
    }

    private void bindParams(Query query, List<String> terms) {
        for (int i = 0; i < terms.size(); i++) {
            String pattern = "%" + terms.get(i).toLowerCase() + "%";
            int base = i * 3 + 1;
            query.setParameter(base,     pattern);
            query.setParameter(base + 1, pattern);
            query.setParameter(base + 2, pattern);
        }
    }
}
