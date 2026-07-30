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
    public Page<Testimonial> searchLike(List<String> terms, String source, Pageable pageable) {
        String where = buildWhere(terms, source);
        Query dataQ = em.createNativeQuery(
            "SELECT * FROM testimonials " + where + " ORDER BY created_at DESC",
            Testimonial.class);
        Query countQ = em.createNativeQuery(
            "SELECT COUNT(*) FROM testimonials " + where);
        bindParams(dataQ, terms, source);
        bindParams(countQ, terms, source);
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
            "SELECT * FROM testimonials " + buildWhere(terms, null) + " ORDER BY created_at DESC",
            Testimonial.class);
        bindParams(query, terms, null);
        query.setMaxResults(max);
        return query.getResultList();
    }

    private String buildWhere(List<String> terms, String source) {
        List<String> clauses = new java.util.ArrayList<>();
        for (int i = 0; i < terms.size(); i++) {
            int base = i * 4 + 1;
            clauses.add("(LOWER(author_name) LIKE ?" + base
                + " OR LOWER(text) LIKE ?" + (base + 1)
                + " OR LOWER(tags) LIKE ?" + (base + 2)
                + " OR EXISTS (SELECT 1 FROM resources res JOIN subtitle_tracks st ON st.resource_id = res.id "
                + "            WHERE res.testimonial_id = testimonials.id AND LOWER(st.text_content) LIKE ?" + (base + 3) + ")"
                + ")");
        }
        if (source != null && !source.isBlank()) {
            clauses.add("source = ?" + (terms.size() * 4 + 1));
        }
        if (clauses.isEmpty()) return "";
        return "WHERE " + String.join(" AND ", clauses);
    }

    private void bindParams(Query query, List<String> terms, String source) {
        for (int i = 0; i < terms.size(); i++) {
            String pattern = "%" + terms.get(i).toLowerCase() + "%";
            int base = i * 4 + 1;
            query.setParameter(base,     pattern);
            query.setParameter(base + 1, pattern);
            query.setParameter(base + 2, pattern);
            query.setParameter(base + 3, pattern);
        }
        if (source != null && !source.isBlank()) {
            query.setParameter(terms.size() * 4 + 1, source);
        }
    }
}
