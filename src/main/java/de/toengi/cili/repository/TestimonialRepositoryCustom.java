package de.toengi.cili.repository;

import de.toengi.cili.model.entity.Testimonial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TestimonialRepositoryCustom {
    Page<Testimonial> searchLike(List<String> terms, String source, Pageable pageable);
    List<Testimonial> searchLikeTop(List<String> terms, int max);
}
