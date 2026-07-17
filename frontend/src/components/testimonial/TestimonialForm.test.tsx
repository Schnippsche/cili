import { render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import TestimonialForm from './TestimonialForm';

test('renders form with empty fields when no initial value', () => {
  render(<TestimonialForm open={true} onSave={vi.fn()} onClose={vi.fn()} />);
  expect(screen.getByLabelText('Name')).toHaveValue('');
  expect(screen.getByLabelText('Testimonial')).toHaveValue('');
});

test('pre-fills fields when editing an existing entry', () => {
  const initial = {
    id: 1, authorName: 'Anna', tags: null, text: 'Super Erfahrung!', userId: 1,
    createdAt: '2026-05-31T10:00:00', updatedAt: '2026-05-31T10:00:00', images: [],
  };
  render(<TestimonialForm open={true} initial={initial} onSave={vi.fn()} onClose={vi.fn()} />);
  expect(screen.getByLabelText('Name')).toHaveValue('Anna');
  expect(screen.getByLabelText('Testimonial')).toHaveValue('Super Erfahrung!');
});
