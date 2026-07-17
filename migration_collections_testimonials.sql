-- migration_collections_testimonials.sql
ALTER TABLE collection_item
  MODIFY COLUMN resource_id BIGINT NULL,
  ADD COLUMN testimonial_id BIGINT NULL AFTER resource_id,
  ADD CONSTRAINT fk_ci_testimonial FOREIGN KEY (testimonial_id) REFERENCES testimonials (id) ON DELETE CASCADE,
  ADD UNIQUE KEY uq_collection_testimonial (collection_id, testimonial_id),
  ADD CONSTRAINT chk_ci_item_type CHECK (
    (resource_id IS NOT NULL AND testimonial_id IS NULL) OR
    (resource_id IS NULL AND testimonial_id IS NOT NULL)
  );
