import { render, screen, fireEvent } from '@testing-library/react';
import { vi } from 'vitest';
import ImageLightbox from './ImageLightbox';
import type { ResourceDto } from '../../types/api';

vi.mock('../../hooks/useAuthenticatedUrl', () => ({
  useAuthenticatedUrl: vi.fn(() => 'blob:http://localhost/fake'),
}));

const makeImage = (id: number): ResourceDto => ({
  id,
  folderId: 1,
  originalName: `photo${id}.jpg`,
  storedName: `${id}-stored.jpg`,
  mimeType: 'image/jpeg',
  size: 1000,
  checksum: null,
  uploaderId: 1,
  storageType: 'LOCAL',
  fileDate: null,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
  metadata: null,
  sortOrder: null,
  thumbnailStatus: 'DONE',
  hasAnalyzableSubtitles: false,
});

const images = [makeImage(1), makeImage(2), makeImage(3)];

function renderLightbox(currentId: number, onNavigate = vi.fn(), onClose = vi.fn()) {
  return { onNavigate, onClose, ...render(
    <ImageLightbox images={images} currentId={currentId} onClose={onClose} onNavigate={onNavigate} />
  )};
}

describe('ImageLightbox', () => {
  it('renders the current image', () => {
    renderLightbox(2);
    expect(screen.getByRole('img')).toHaveAttribute('src', 'blob:http://localhost/fake');
  });

  it('disables left button at first image', () => {
    renderLightbox(1);
    expect(screen.getByLabelText('vorheriges Bild')).toBeDisabled();
    expect(screen.getByLabelText('nächstes Bild')).not.toBeDisabled();
  });

  it('disables right button at last image', () => {
    renderLightbox(3);
    expect(screen.getByLabelText('nächstes Bild')).toBeDisabled();
    expect(screen.getByLabelText('vorheriges Bild')).not.toBeDisabled();
  });

  it('calls onNavigate with previous id when left button clicked', () => {
    const { onNavigate } = renderLightbox(2);
    fireEvent.click(screen.getByLabelText('vorheriges Bild'));
    expect(onNavigate).toHaveBeenCalledWith(1);
  });

  it('calls onNavigate with next id when right button clicked', () => {
    const { onNavigate } = renderLightbox(2);
    fireEvent.click(screen.getByLabelText('nächstes Bild'));
    expect(onNavigate).toHaveBeenCalledWith(3);
  });

  it('calls onClose when close button clicked', () => {
    const { onClose } = renderLightbox(2);
    fireEvent.click(screen.getByLabelText('schließen'));
    expect(onClose).toHaveBeenCalled();
  });

  it('calls onClose when Escape key pressed', () => {
    const { onClose } = renderLightbox(2);
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
  });

  it('calls onNavigate with next id when ArrowRight pressed', () => {
    const { onNavigate } = renderLightbox(2);
    fireEvent.keyDown(window, { key: 'ArrowRight' });
    expect(onNavigate).toHaveBeenCalledWith(3);
  });

  it('calls onNavigate with prev id when ArrowLeft pressed', () => {
    const { onNavigate } = renderLightbox(2);
    fireEvent.keyDown(window, { key: 'ArrowLeft' });
    expect(onNavigate).toHaveBeenCalledWith(1);
  });

  it('does not call onNavigate on ArrowLeft at first image', () => {
    const { onNavigate } = renderLightbox(1);
    fireEvent.keyDown(window, { key: 'ArrowLeft' });
    expect(onNavigate).not.toHaveBeenCalled();
  });

  it('does not call onNavigate on ArrowRight at last image', () => {
    const { onNavigate } = renderLightbox(3);
    fireEvent.keyDown(window, { key: 'ArrowRight' });
    expect(onNavigate).not.toHaveBeenCalled();
  });

  it('shows the resource title from metadata', () => {
    const withMeta = makeImage(2);
    withMeta.metadata = { title: 'Mein Titel', description: null, tags: null,
      categories: null, language: null };
    render(
      <ImageLightbox
        images={[withMeta]}
        currentId={2}
        onClose={vi.fn()}
        onNavigate={vi.fn()}
      />
    );
    expect(screen.getByText('Mein Titel')).toBeInTheDocument();
  });

  it('falls back to originalName when metadata is null', () => {
    renderLightbox(2);
    expect(screen.getByText('photo2.jpg')).toBeInTheDocument();
  });
});
