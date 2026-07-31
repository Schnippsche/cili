import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PublicTestimonialCard from './PublicTestimonialCard';
import type { PublicTestimonialDto } from '../../types/api';

const base: PublicTestimonialDto = {
  id: 1, authorName: 'Erika Muster', tags: null,
  text: 'Sehr empfehlenswert!',
  human: true,
  animal: false,
  createdAt: '2026-06-01T10:00:00', updatedAt: '2026-06-01T10:00:00',
  attachments: [],
};

function renderCard(testimonial: PublicTestimonialDto) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <PublicTestimonialCard testimonial={testimonial} />
    </QueryClientProvider>
  );
}

describe('PublicTestimonialCard', () => {
  test('zeigt Autor und vollständigen Text', () => {
    renderCard(base);
    expect(screen.getByText('Erika Muster')).toBeInTheDocument();
    expect(screen.getByText('Sehr empfehlenswert!')).toBeInTheDocument();
  });

  test('zeigt Datum im deutschen Format', () => {
    renderCard(base);
    expect(screen.getByText(/01\.06\.2026/)).toBeInTheDocument();
  });

  test('zeigt Tags als Chips wenn vorhanden', () => {
    renderCard({ ...base, tags: 'Schule, Sport' });
    expect(screen.getByText('Schule')).toBeInTheDocument();
    expect(screen.getByText('Sport')).toBeInTheDocument();
  });

  test('kein Edit/Delete-Button vorhanden', () => {
    renderCard(base);
    expect(screen.queryByRole('button', { name: /bearbeiten/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /löschen/i })).not.toBeInTheDocument();
  });

  test('langer Text wird vollständig angezeigt ohne Kürzung', () => {
    const longText = 'x'.repeat(500);
    renderCard({ ...base, text: longText });
    expect(screen.getByText(longText)).toBeInTheDocument();
    expect(screen.queryByText(/mehr anzeigen/i)).not.toBeInTheDocument();
  });

  test('zeigt Mensch-Chip, aber keinen Tier-Chip, wenn nur human gesetzt ist', () => {
    renderCard(base);
    expect(screen.getByText('Mensch')).toBeInTheDocument();
    expect(screen.queryByText('Tier')).not.toBeInTheDocument();
  });

  test('zeigt Tier-Chip, aber keinen Mensch-Chip, wenn nur animal gesetzt ist', () => {
    renderCard({ ...base, human: false, animal: true });
    expect(screen.getByText('Tier')).toBeInTheDocument();
    expect(screen.queryByText('Mensch')).not.toBeInTheDocument();
  });

  test('zeigt beide Chips, wenn human und animal gesetzt sind', () => {
    renderCard({ ...base, human: true, animal: true });
    expect(screen.getByText('Mensch')).toBeInTheDocument();
    expect(screen.getByText('Tier')).toBeInTheDocument();
  });

  test('öffnet Lightbox beim Klick auf Bild', () => {
    const withImages: PublicTestimonialDto = {
      ...base,
      attachments: [{ id: 10, originalName: 'foto.jpg', mimeType: 'image/jpeg', size: 1000, createdAt: '2026-06-01T10:00:00', thumbnailStatus: null, storedName: null }],
    };
    renderCard(withImages);
    const img = screen.getByRole('img', { name: 'foto.jpg' });
    fireEvent.click(img);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
