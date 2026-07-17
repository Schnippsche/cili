package de.toengi.cili.repository;

import de.toengi.cili.model.entity.Testimonial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TestimonialRepository extends JpaRepository<Testimonial, Long>, TestimonialRepositoryCustom {

    Page<Testimonial> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Testimonial> findAllByOrderByCreatedAtDesc();

    @Query(value = "SELECT * FROM testimonials ORDER BY created_at DESC LIMIT :max",
           nativeQuery = true)
    List<Testimonial> findTopNByCreatedAtDesc(@Param("max") int max);
}
