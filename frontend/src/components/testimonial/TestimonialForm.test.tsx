import { render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TestimonialForm, { isVideoLikeFile } from './TestimonialForm';
import { fireEvent, waitFor } from '@testing-library/react';

vi.mock('../../hooks/useAuthenticatedUrl', () => ({
  useAuthenticatedUrl: () => null,
}));

function renderForm(props: Parameters<typeof TestimonialForm>[0]) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <TestimonialForm {...props} />
    </QueryClientProvider>
  );
}

test('renders form with empty fields when no initial value', () => {
  const mockSave = vi.fn().mockResolvedValue({
    id: 1, authorName: '', tags: null, text: '', userId: 1,
    source: 'Mensch', createdAt: '2026-05-31T10:00:00', updatedAt: '2026-05-31T10:00:00', attachments: [],
  });
  renderForm({ open: true, onSave: mockSave, onClose: vi.fn() });
  expect(screen.getByLabelText('Name')).toHaveValue('');
  expect(screen.getByLabelText('Testimonial')).toHaveValue('');
});

test('pre-fills fields when editing an existing entry', () => {
  const initial = {
    id: 1, authorName: 'Anna', tags: null, text: 'Super Erfahrung!', userId: 1,
    source: 'Mensch', createdAt: '2026-05-31T10:00:00', updatedAt: '2026-05-31T10:00:00', attachments: [],
  };
  const mockSave = vi.fn().mockResolvedValue(initial);
  renderForm({ open: true, initial, onSave: mockSave, onClose: vi.fn() });
  expect(screen.getByLabelText('Name')).toHaveValue('Anna');
  expect(screen.getByLabelText('Testimonial')).toHaveValue('Super Erfahrung!');
});

describe('isVideoLikeFile', () => {
  test('erkennt Video- und Audio-MIME-Typen direkt', () => {
    expect(isVideoLikeFile(new File(['x'], 'clip.mp4', { type: 'video/mp4' }))).toBe(true);
    expect(isVideoLikeFile(new File(['x'], 'song.mp3', { type: 'audio/mpeg' }))).toBe(false);
  });

  test('ordnet Extension-Fallback-Dateien ohne MIME-Typ korrekt zu', () => {
    expect(isVideoLikeFile(new File(['x'], 'clip.ogv', { type: '' }))).toBe(true);
    expect(isVideoLikeFile(new File(['x'], 'clip.mkv', { type: '' }))).toBe(true);
    expect(isVideoLikeFile(new File(['x'], 'clip.3gp', { type: '' }))).toBe(true);
    expect(isVideoLikeFile(new File(['x'], 'song.oga', { type: '' }))).toBe(false);
    expect(isVideoLikeFile(new File(['x'], 'song.flac', { type: '' }))).toBe(false);
    expect(isVideoLikeFile(new File(['x'], 'song.opus', { type: '' }))).toBe(false);
    expect(isVideoLikeFile(new File(['x'], 'song.wma', { type: '' }))).toBe(false);
    expect(isVideoLikeFile(new File(['x'], 'song.m4a', { type: '' }))).toBe(false);
  });
});

describe('Anhänge-Upload (vereinheitlicht)', () => {
  function baseSave() {
    return vi.fn().mockResolvedValue({
      id: 1, authorName: '', tags: null, text: '', userId: 1,
      source: 'Mensch', createdAt: '2026-05-31T10:00:00', updatedAt: '2026-05-31T10:00:00', attachments: [],
    });
  }

  test('zeigt genau einen Anhänge-Button mit kombiniertem accept-Filter', () => {
    renderForm({ open: true, onSave: baseSave(), onClose: vi.fn() });
    // Statt auf die alten (nicht mehr existierenden) Tooltip-Texte zu prüfen — MUI Tooltip setzt
    // ohnehin kein title-Attribut, eine reine .not.toBeInTheDocument()-Prüfung darauf wäre vakuos —
    // wird direkt gezählt, dass nur noch ein einziges Datei-Input existiert.
    // Hinweis: MUI Dialog rendert via Portal nach document.body, nicht in den RTL-`container` —
    // daher wird hier gegen document.body statt gegen `container` geprüft (siehe Debug-Verifikation).
    expect(document.body.querySelectorAll('input[type="file"]')).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Bilder, Video oder Audio hinzufügen' })).toBeInTheDocument();
    const input = screen.getByTestId('attachment-input') as HTMLInputElement;
    expect(input.accept).toBe('image/*,video/*,audio/*');
    expect(input.multiple).toBe(true);
  });

  test('sortiert eine gemischte Auswahl in Bild- und Media-Kacheln ein und meldet nicht unterstützte Dateien', async () => {
    renderForm({ open: true, onSave: baseSave(), onClose: vi.fn() });
    const input = screen.getByTestId('attachment-input') as HTMLInputElement;

    const image = new File(['x'], 'foto.jpg', { type: 'image/jpeg' });
    const video = new File(['x'], 'clip.mp4', { type: 'video/mp4' });
    const rejected = new File(['x'], 'dokument.pdf', { type: 'application/pdf' });

    fireEvent.change(input, { target: { files: [image, video, rejected] } });

    expect(await screen.findByAltText('Neu 1')).toBeInTheDocument();
    // MUI Tooltip setzt standardmäßig aria-label auf dem Kind-Element, kein title-Attribut —
    // daher getByLabelText statt getByTitle (siehe NewMediaFileTile in Step 4).
    expect(screen.getByLabelText('clip.mp4')).toBeInTheDocument();
    expect(await screen.findByText(/Nicht unterstützt und übersprungen: dokument\.pdf/)).toBeInTheDocument();
  });
});
