import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {vi} from 'vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import ShareLinkDialog from './ShareLinkDialog';
import * as shareApi from '../../api/share';

vi.mock('../../api/share');

function renderDialog(onClose = vi.fn()) {
  vi.mocked(shareApi.getShare).mockResolvedValue(null);
  vi.mocked(shareApi.getShareConfig).mockResolvedValue({validityDays: 90});
  const qc = new QueryClient({defaultOptions: {queries: {retry: false}}});
  render(
      <QueryClientProvider client={qc}>
        <ShareLinkDialog resourceId={42} title="doc.pdf" onClose={onClose}/>
      </QueryClientProvider>
  );
  return onClose;
}

describe('ShareLinkDialog', () => {
  test('renders title, resource name and delegates to ShareSection', async () => {
    renderDialog();
    expect(screen.getByText('Freigabelink')).toBeInTheDocument();
    expect(screen.getByText('doc.pdf')).toBeInTheDocument();
    await waitFor(() => expect(shareApi.getShare).toHaveBeenCalledWith(42));
    await waitFor(() => expect(screen.getByRole('button', {name: /link generieren/i})).toBeInTheDocument());
  });

  test('close button calls onClose', () => {
    const onClose = renderDialog();
    fireEvent.click(screen.getByRole('button', {name: /schließen/i}));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
