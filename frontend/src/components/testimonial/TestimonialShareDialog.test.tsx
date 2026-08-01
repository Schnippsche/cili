import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {vi} from 'vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import TestimonialShareDialog from './TestimonialShareDialog';
import * as shareApi from '../../api/share';

vi.mock('../../api/share');

function renderDialog(onClose = vi.fn()) {
  vi.mocked(shareApi.getShareConfig).mockResolvedValue({validityDays: 90});
  const qc = new QueryClient({defaultOptions: {queries: {retry: false}}});
  return render(
      <QueryClientProvider client={qc}>
        <TestimonialShareDialog testimonialId={42} authorName="Anna" onClose={onClose}/>
      </QueryClientProvider>
  );
}

test('zeigt den Link mit der Testimonial-ID an', async () => {
  renderDialog();

  await waitFor(() => {
    const input = screen.getByLabelText('Öffentlicher Link') as HTMLInputElement;
    expect(input.value).toContain('/erfahrungsberichte/42');
  });
});

test('kopiert den Link und zeigt eine Bestätigung', async () => {
  document.execCommand = vi.fn().mockReturnValue(true);
  renderDialog();

  await waitFor(() => screen.getByLabelText('Öffentlicher Link'));
  fireEvent.click(screen.getByRole('button', {name: /link kopieren/i}));

  await waitFor(() => expect(screen.getByText('Link kopiert!')).toBeInTheDocument());
  expect(document.execCommand).toHaveBeenCalledWith('copy');
});

test('ruft onClose auf, wenn Schließen geklickt wird', async () => {
  const onClose = vi.fn();
  renderDialog(onClose);

  await waitFor(() => screen.getByLabelText('Öffentlicher Link'));
  fireEvent.click(screen.getByRole('button', {name: /^schließen$/i}));

  expect(onClose).toHaveBeenCalled();
});
