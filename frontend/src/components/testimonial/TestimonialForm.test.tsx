import { render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TestimonialForm, { isVideoLikeFile } from './TestimonialForm';
import { fireEvent, waitFor } from '@testing-library/react';

vi.mock('../../hooks/useAuthenticatedUrl', () => ({
  useAuthenticatedUrl: () => null,
}));
vi.mock('../../api/upload');

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
    expect(input.accept).toBe('image/jpeg,image/png,image/gif,image/webp,image/bmp,video/*,audio/*');
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

  test('rendert Kacheln gruppiert: bestehende Anhänge, dann neue Bilder, dann neue Video/Audio', async () => {
    const initial = {
      id: 1, authorName: 'Anna', tags: null, text: 'Super Erfahrung!', userId: 1,
      source: 'Mensch', createdAt: '2026-05-31T10:00:00', updatedAt: '2026-05-31T10:00:00',
      attachments: [
        { id: 10, originalName: 'bestand.jpg', mimeType: 'image/jpeg', size: 100, createdAt: '2026-05-31T10:00:00', thumbnailStatus: null, storedName: null },
        { id: 11, originalName: 'bestand.mp4', mimeType: 'video/mp4', size: 100, createdAt: '2026-05-31T10:00:00', thumbnailStatus: 'DONE' as const, storedName: 'stored.mp4' },
      ],
    };
    renderForm({ open: true, initial, onSave: vi.fn().mockResolvedValue(initial), onClose: vi.fn() });

    const input = screen.getByTestId('attachment-input') as HTMLInputElement;
    const image = new File(['x'], 'neu.jpg', { type: 'image/jpeg' });
    const video = new File(['x'], 'neu.mp4', { type: 'video/mp4' });
    fireEvent.change(input, { target: { files: [image, video] } });

    await screen.findByAltText('Neu 1');
    // MUI Tooltip setzt aria-label statt title auf dem Kind-Element — daher werden Video/Audio-Kacheln
    // über aria-label statt Bild-alt-Text identifiziert.
    // Wichtig: alle vier relevanten Kachel-Bezeichner müssen aus EINER einzigen, Dokument-Reihenfolge-
    // erhaltenden Abfrage stammen. Zwei separate getAllBy*-Aufrufe (z. B. getAllByRole('img') und
    // getAllByLabelText(...)) anschließend zu concat()en zerstört die echte DOM-Reihenfolge, weil jede
    // Teilliste für sich in Dokumentreihenfolge sortiert ist, aber die Reihenfolge zwischen den beiden
    // Teillisten beim Zusammenfügen verloren geht — ein Test darauf würde "neue Video/Audio-Kachel nach
    // neuen Bildern" nie wirklich prüfen (verifiziert: JSX-Reihenfolge im TestimonialForm testweise
    // vertauscht — newMediaFiles zuerst — und der ursprüngliche concat()-Test lief trotzdem grün durch).
    // document.body.querySelectorAll(...) in einem einzigen Aufruf lieferte echte DOM-Reihenfolge.
    // Query-Root ist document.body statt RTL-container, weil MUI Dialog per Portal dorthin rendert
    // (siehe bereits bestehender Kommentar weiter oben in dieser Datei).
    const relevantNames = ['bestand.jpg', 'Neu 1', 'neu.mp4', 'bestand.mp4'];
    const tiles = Array.from(document.body.querySelectorAll('img[alt], [aria-label]'))
        .map(el => el.getAttribute('alt') ?? el.getAttribute('aria-label'))
        .filter((name): name is string => name !== null && relevantNames.includes(name));

    // Bestehende Anhänge zuerst (Reihenfolge wie von initial.attachments geliefert),
    // danach neue Bilder, danach neue Video/Audio-Kacheln.
    const bestandImgIdx = tiles.findIndex(t => t === 'bestand.jpg');
    const neuBildIdx = tiles.findIndex(t => t === 'Neu 1');
    const neuVideoIdx = tiles.findIndex(t => t === 'neu.mp4');
    expect(bestandImgIdx).toBeGreaterThanOrEqual(0);
    expect(neuBildIdx).toBeGreaterThan(bestandImgIdx);
    expect(neuVideoIdx).toBeGreaterThan(neuBildIdx);
  });

  test('NewMediaFileTile zeigt Fortschritt während des Uploads und blendet ihn nach Abschluss aus', async () => {
    const uploadApi = await import('../../api/upload');
    // initUpload löst über einen echten Timer (statt eines sofort erfüllten Promise) auf: uploadMediaFile
    // setzt den 'uploading'-Status synchron *vor* dem ersten await, aber wenn alle drei Mocks
    // (initUpload/uploadChunk/completeUpload) im selben Mikrotask-Batch durchlaufen, fasst React die
    // Zwischenzustände zusammen und committed direkt den finalen 'done'-Zustand — der Progress-Balken
    // wäre dann nie im DOM beobachtbar. Ein echter Netzwerk-Request hat immer eine Makrotask-Grenze;
    // das setTimeout hier bildet das nach, sodass 'uploading' tatsächlich gerendert wird, bevor es
    // wieder verschwindet (siehe Debug-Verifikation während der Testerstellung).
    vi.spyOn(uploadApi, 'initUpload').mockImplementation(() => new Promise(resolve => setTimeout(() => resolve({ jobId: 'job1', chunksTotal: 1, chunksReceived: 0, status: 'INITIATED' }), 10)));
    vi.spyOn(uploadApi, 'uploadChunk').mockResolvedValue({ jobId: 'job1', chunksTotal: 1, chunksReceived: 1, status: 'IN_PROGRESS' });
    vi.spyOn(uploadApi, 'completeUpload').mockResolvedValue({ resourceId: 200 });

    const savedTestimonial = {
      id: 5, authorName: 'Anna', tags: null, text: 'Super Erfahrung!', userId: 1,
      source: 'Mensch', createdAt: '2026-05-31T10:00:00', updatedAt: '2026-05-31T10:00:00', attachments: [],
    };
    const onSave = vi.fn().mockResolvedValue(savedTestimonial);
    renderForm({
      open: true,
      initial: { ...savedTestimonial, authorName: 'Anna', text: 'Super Erfahrung! 1234567890' },
      onSave, onClose: vi.fn(),
    });

    const input = screen.getByTestId('attachment-input') as HTMLInputElement;
    const video = new File(['x'], 'clip.mp4', { type: 'video/mp4' });
    fireEvent.change(input, { target: { files: [video] } });
    await screen.findByLabelText('clip.mp4');

    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));

    await waitFor(() => expect(screen.getByLabelText('clip.mp4').querySelector('[role="progressbar"]')).toBeInTheDocument());
    await waitFor(() => expect(screen.getByLabelText('clip.mp4').querySelector('[role="progressbar"]')).not.toBeInTheDocument());
  });

  test('NewMediaFileTile zeigt Fehlerzustand mit rotem Rahmen und Fehlermeldung im Tooltip', async () => {
    const uploadApi = await import('../../api/upload');
    vi.spyOn(uploadApi, 'initUpload').mockRejectedValue(new Error('Upload fehlgeschlagen'));

    const savedTestimonial = {
      id: 6, authorName: 'Anna', tags: null, text: 'Super Erfahrung! 1234567890', userId: 1,
      source: 'Mensch', createdAt: '2026-05-31T10:00:00', updatedAt: '2026-05-31T10:00:00', attachments: [],
    };
    const onSave = vi.fn().mockResolvedValue(savedTestimonial);
    renderForm({ open: true, initial: savedTestimonial, onSave, onClose: vi.fn() });

    const input = screen.getByTestId('attachment-input') as HTMLInputElement;
    const video = new File(['x'], 'clip.mp4', { type: 'video/mp4' });
    fireEvent.change(input, { target: { files: [video] } });
    await screen.findByLabelText('clip.mp4');

    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));

    // getByLabelText greift hier den äußeren Box, auf den die Tooltip-Komponente das aria-label klont —
    // genau dieser Box trägt in NewMediaFileTile auch border/borderColor für den Fehlerzustand
    // (border: isError ? '1px solid' : 'none', borderColor: isError ? 'error.main' : undefined).
    // error.main löst im Test-Theme zu rgb(211, 47, 47) auf.
    const tile = await waitFor(() => screen.getByLabelText(/clip\.mp4 — Upload fehlgeschlagen/));
    expect(tile).toBeInTheDocument();
    expect(tile).toHaveStyle({ borderColor: 'rgb(211, 47, 47)' });
  });
});
