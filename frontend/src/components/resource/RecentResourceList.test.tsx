import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import RecentResourceList from './RecentResourceList';
import type {RecentResourceDto} from '../../types/api';

const navigateMock = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {...actual, useNavigate: () => navigateMock};
});

function recent(
  id: number,
  name: string,
  mimeType: string,
  folderId: number,
  folderName: string,
): RecentResourceDto {
  return {
    folderName,
    resource: {
      id,
      folderId,
      originalName: name,
      storedName: 'stored',
      mimeType,
      size: 1,
      checksum: null,
      uploaderId: 1,
      storageType: 'LOCAL',
      fileDate: null,
      sortOrder: null,
      createdAt: '2026-08-27T10:00:00Z',
      updatedAt: '2026-08-27T10:00:00Z',
      metadata: null,
      thumbnailStatus: null,
      hasAnalyzableSubtitles: false,
    },
  };
}

beforeEach(() => navigateMock.mockClear());

describe('RecentResourceList', () => {
  it('lists each recent resource with its folder name', () => {
    render(
      <MemoryRouter>
        <RecentResourceList
          items={[
            recent(1, 'clip.mp4', 'video/mp4', 10, 'Projekte'),
            recent(2, 'notes.txt', 'text/plain', 11, 'Notizen'),
          ]}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText('clip.mp4')).toBeInTheDocument();
    expect(screen.getByText('Projekte')).toBeInTheDocument();
    expect(screen.getByText('notes.txt')).toBeInTheDocument();
    expect(screen.getByText('Notizen')).toBeInTheDocument();
  });

  it('opens the folder viewer when a media row is clicked', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <RecentResourceList items={[recent(5, 'clip.mp4', 'video/mp4', 10, 'Projekte')]}/>
      </MemoryRouter>,
    );

    await user.click(screen.getByText('clip.mp4'));

    expect(navigateMock).toHaveBeenCalledWith('/folders/10?view=5&type=video');
  });
});
