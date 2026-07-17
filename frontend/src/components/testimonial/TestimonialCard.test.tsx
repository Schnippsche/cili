import { render, screen, fireEvent } from '@testing-library/react';
import { vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TestimonialCard from './TestimonialCard';
import type { TestimonialDto } from '../../types/api';
import * as collectionsApi from '../../api/collections';

vi.mock('../../api/collections');

const mockTestimonial: TestimonialDto = {
  id: 1,
  authorName: 'Max Mustermann',
  tags: null,
  text: 'Das war eine tolle Erfahrung.',
  userId: 1,
  createdAt: '2026-05-31T10:00:00',
  updatedAt: '2026-05-31T10:00:00',
  images: [],
};

function renderCard(props: Partial<Parameters<typeof TestimonialCard>[0]> = {}) {
  vi.mocked(collectionsApi.getCollections).mockResolvedValue([]);
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <TestimonialCard
        testimonial={mockTestimonial}
        currentUserId={1}
        isAdmin={false}
        canWrite={true}
        canDelete={true}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
        {...props}
      />
    </QueryClientProvider>
  );
}

describe('TestimonialCard', () => {
  test('renders author name and text', () => {
    renderCard();
    expect(screen.getByText('Max Mustermann')).toBeInTheDocument();
    expect(screen.getByText('Das war eine tolle Erfahrung.')).toBeInTheDocument();
  });

  test('shows edit and delete buttons for owner with canWrite+canDelete', () => {
    renderCard();
    expect(screen.getByRole('button', { name: /bearbeiten/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /löschen/i })).toBeInTheDocument();
  });

  test('hides action buttons for non-owner non-admin', () => {
    renderCard({ currentUserId: 2 });
    expect(screen.queryByRole('button', { name: /bearbeiten/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /löschen/i })).not.toBeInTheDocument();
  });

  test('shows action buttons for admin even if not owner', () => {
    renderCard({ currentUserId: 2, isAdmin: true });
    expect(screen.getByRole('button', { name: /bearbeiten/i })).toBeInTheDocument();
  });

  test('hides edit button for owner when canWrite is false', () => {
    renderCard({ canWrite: false });
    expect(screen.queryByRole('button', { name: /bearbeiten/i })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /löschen/i })).toBeInTheDocument();
  });

  test('hides delete button for owner when canDelete is false', () => {
    renderCard({ canDelete: false });
    expect(screen.getByRole('button', { name: /bearbeiten/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /löschen/i })).not.toBeInTheDocument();
  });

  test('shows mehr-anzeigen toggle when text exceeds 200 chars', () => {
    const longText = 'a'.repeat(250);
    renderCard({ testimonial: { ...mockTestimonial, text: longText } });
    expect(screen.getByText('Mehr anzeigen')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Mehr anzeigen'));
    expect(screen.getByText('Weniger anzeigen')).toBeInTheDocument();
  });

  test('shows add-to-collection button by default', () => {
    renderCard();
    expect(screen.getByRole('button', { name: /zur sammlung hinzufügen/i })).toBeInTheDocument();
  });

  test('hides add-to-collection button and shows remove button when onRemoveFromCollection is provided', () => {
    const onRemove = vi.fn();
    renderCard({ canWrite: false, canDelete: false, onRemoveFromCollection: onRemove });

    expect(screen.queryByRole('button', { name: /zur sammlung hinzufügen/i })).not.toBeInTheDocument();
    const removeButton = screen.getByRole('button', { name: /aus sammlung entfernen/i });
    fireEvent.click(removeButton);
    expect(onRemove).toHaveBeenCalledWith(1);
  });
});
