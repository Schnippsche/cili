import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import GroupTable from './GroupTable';
import type {AclEntryDto, GroupDto, PageResponse} from '../../types/api';
import * as adminApi from '../../api/admin';

vi.mock('../../api/admin');
vi.mock('../../hooks/useIsMobile', () => ({useIsMobile: () => false}));

const mockGroup: GroupDto = {
  id: 1, name: 'Testgruppe', description: null, system: false,
  memberCount: 0, createdAt: '2026-01-01T00:00:00Z',
};

const mockGroupPage: PageResponse<GroupDto> = {
  content: [mockGroup],
  page: 0, size: 20, totalElements: 1, totalPages: 1,
};

const mockFolders: adminApi.FolderItem[] = [
  {id: 10, name: 'Apple', path: '/Apple'},
  {id: 20, name: 'Zebra', path: '/Zebra'},
];

// Folder 10 (Apple) has 2 entries, Folder 20 (Zebra) has 1
const mockEntries: AclEntryDto[] = [
  {
    id: 1,
    subjectType: 'GROUP',
    subjectId: 1,
    resourceType: 'FOLDER',
    resourceId: 20,
    permission: 'READ',
    grantType: 'ALLOW',
    inheritable: true
  },
  {
    id: 2,
    subjectType: 'GROUP',
    subjectId: 1,
    resourceType: 'FOLDER',
    resourceId: 10,
    permission: 'WRITE',
    grantType: 'ALLOW',
    inheritable: false
  },
  {
    id: 3,
    subjectType: 'GROUP',
    subjectId: 1,
    resourceType: 'FOLDER',
    resourceId: 10,
    permission: 'READ',
    grantType: 'ALLOW',
    inheritable: true
  },
];

function renderGroupTable() {
  const qc = new QueryClient({defaultOptions: {queries: {retry: false}}});
  return render(<QueryClientProvider client={qc}><GroupTable/></QueryClientProvider>);
}

async function openPermissionsDialog() {
  vi.mocked(adminApi.listGroups).mockResolvedValue(mockGroupPage);
  vi.mocked(adminApi.listGroupAclEntries).mockResolvedValue(mockEntries);
  vi.mocked(adminApi.listAllFolders).mockResolvedValue(mockFolders);
  renderGroupTable();
  await screen.findByText('Testgruppe');
  await userEvent.click(screen.getByLabelText('Rechte'));
  await screen.findByText(/Apple/);
}

describe('PermissionsDialog — grouped table', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows one row per folder, not one per entry', async () => {
    await openPermissionsDialog();
    // 3 entries across 2 folders → 2 data rows + 1 header row = 3
    const rows = screen.getAllByRole('row');
    expect(rows).toHaveLength(3);
  });

  it('sorts folder rows alphabetically by folder name', async () => {
    await openPermissionsDialog();
    const rows = screen.getAllByRole('row');
    // rows[0] = header, rows[1] = Apple (first alphabetically), rows[2] = Zebra
    expect(within(rows[1]).getByText(/Apple/)).toBeInTheDocument();
    expect(within(rows[2]).getByText(/Zebra/)).toBeInTheDocument();
  });

  it('appends ↓ to chip label when inheritable is true', async () => {
    await openPermissionsDialog();
    expect(screen.getAllByText('READ ↓').length).toBeGreaterThan(0);
  });

  it('does not append ↓ to chip label when inheritable is false', async () => {
    await openPermissionsDialog();
    expect(screen.getByText('WRITE')).toBeInTheDocument();
    expect(screen.queryByText('WRITE ↓')).not.toBeInTheDocument();
  });

  it('does not render a Typ column header', async () => {
    await openPermissionsDialog();
    expect(screen.queryByText('Typ')).not.toBeInTheDocument();
  });

  it('does not render a grantType selector in the add form', async () => {
    await openPermissionsDialog();
    expect(screen.queryByLabelText('Typ')).not.toBeInTheDocument();
  });
});
