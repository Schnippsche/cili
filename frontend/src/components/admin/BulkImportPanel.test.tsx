import {render, screen} from '@testing-library/react';
import {describe, expect, test} from 'vitest';
import BulkImportPanel from './BulkImportPanel';
import type {BulkImportProgressItem} from '../../hooks/useBulkImportUpload';

const items: BulkImportProgressItem[] = [
  {id: 1, relativePath: 'Interviews/video1.mp4', status: 'DONE'},
  {
    id: 2,
    relativePath: 'notes.exe',
    status: 'SKIPPED',
    skipReason: 'Nicht unterstützter Dateityp: application/x-msdownload'
  },
  {id: 3, relativePath: 'Interviews/video2.mp4', status: 'FAILED', errorMessage: 'Netzwerkfehler'},
];

describe('BulkImportPanel', () => {
  test('renders every item with its relative path and status', () => {
    render(<BulkImportPanel items={items}/>);

    expect(screen.getByText('Interviews/video1.mp4')).toBeInTheDocument();
    expect(screen.getByText('notes.exe')).toBeInTheDocument();
    expect(screen.getByText(/Nicht unterstützter Dateityp/)).toBeInTheDocument();
    expect(screen.getByText('Interviews/video2.mp4')).toBeInTheDocument();
    expect(screen.getByText('Netzwerkfehler')).toBeInTheDocument();
  });

  test('renders a summary counter row', () => {
    render(<BulkImportPanel items={items}/>);
    expect(screen.getByText(/1 fertig/)).toBeInTheDocument();
    expect(screen.getByText(/1 übersprungen/)).toBeInTheDocument();
    expect(screen.getByText(/1 fehlgeschlagen/)).toBeInTheDocument();
  });
});
