import { render, screen, fireEvent } from '@testing-library/react';
import PublicTestimonialCard from './PublicTestimonialCard';
import type { PublicTestimonialDto } from '../../types/api';

const base: PublicTestimonialDto = {
  id: 1, authorName: 'Erika Muster', tags: null,
  text: 'Sehr empfehlenswert!',
  source: 'Mensch',
  createdAt: '2026-06-01T10:00:00', updatedAt: '2026-06-01T10:00:00',
  attachments: [],
};

describe('PublicTestimonialCard', () => {
  test('zeigt Autor und vollständigen Text', () => {
    render(<PublicTestimonialCard testimonial={base} />);
    expect(screen.getByText('Erika Muster')).toBeInTheDocument();
    expect(screen.getByText('Sehr empfehlenswert!')).toBeInTheDocument();
  });

  test('zeigt Datum im deutschen Format', () => {
    render(<PublicTestimonialCard testimonial={base} />);
    expect(screen.getByText(/01\.06\.2026/)).toBeInTheDocument();
  });

  test('zeigt Tags als Chips wenn vorhanden', () => {
    render(<PublicTestimonialCard testimonial={{ ...base, tags: 'Schule, Sport' }} />);
    expect(screen.getByText('Schule')).toBeInTheDocument();
    expect(screen.getByText('Sport')).toBeInTheDocument();
  });

  test('kein Edit/Delete-Button vorhanden', () => {
    render(<PublicTestimonialCard testimonial={base} />);
    expect(screen.queryByRole('button', { name: /bearbeiten/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /löschen/i })).not.toBeInTheDocument();
  });

  test('langer Text wird vollständig angezeigt ohne Kürzung', () => {
    const longText = 'x'.repeat(500);
    render(<PublicTestimonialCard testimonial={{ ...base, text: longText }} />);
    expect(screen.getByText(longText)).toBeInTheDocument();
    expect(screen.queryByText(/mehr anzeigen/i)).not.toBeInTheDocument();
  });

  test('öffnet Lightbox beim Klick auf Bild', () => {
    const withImages: PublicTestimonialDto = {
      ...base,
      attachments: [{ id: 10, originalName: 'foto.jpg', mimeType: 'image/jpeg', size: 1000, createdAt: '2026-06-01T10:00:00' }],
    };
    render(<PublicTestimonialCard testimonial={withImages} />);
    const img = screen.getByRole('img', { name: 'foto.jpg' });
    fireEvent.click(img);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
