package de.toengi.cili.repository;

import de.toengi.cili.model.entity.Testimonial;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestimonialRepositoryQueryTest {

    @Test
    void findTopNByCreatedAtDesc_method_exists() throws NoSuchMethodException {
        Method m = TestimonialRepository.class
            .getMethod("findTopNByCreatedAtDesc", int.class);
        assertThat(m.getReturnType()).isEqualTo(java.util.List.class);
    }

    @Test
    void searchLikeTop_method_exists() throws NoSuchMethodException {
        Method m = TestimonialRepository.class
            .getMethod("searchLikeTop", List.class, int.class);
        assertThat(m.getReturnType()).isEqualTo(java.util.List.class);
    }

    @Test
    void searchLike_method_exists() throws NoSuchMethodException {
        Method m = TestimonialRepository.class
            .getMethod("searchLike", List.class, String.class, org.springframework.data.domain.Pageable.class);
        assertThat(m).isNotNull();
    }

    @Test
    void findByIsHumanTrueOrderByCreatedAtDesc_method_exists() throws NoSuchMethodException {
        Method m = TestimonialRepository.class
            .getMethod("findByIsHumanTrueOrderByCreatedAtDesc",
                org.springframework.data.domain.Pageable.class);
        assertThat(m.getReturnType()).isEqualTo(org.springframework.data.domain.Page.class);
    }

    @Test
    void findByIsAnimalTrueOrderByCreatedAtDesc_method_exists() throws NoSuchMethodException {
        Method m = TestimonialRepository.class
            .getMethod("findByIsAnimalTrueOrderByCreatedAtDesc",
                org.springframework.data.domain.Pageable.class);
        assertThat(m.getReturnType()).isEqualTo(org.springframework.data.domain.Page.class);
    }
}
