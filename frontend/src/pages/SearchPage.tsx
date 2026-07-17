import { useState } from 'react';
import { Alert, Box, CircularProgress, IconButton, List, ListItem, ListItemButton, ListItemText, Tooltip, Typography } from '@mui/material';
import Chip from '@mui/material/Chip';
import BookmarkAddOutlinedIcon from '@mui/icons-material/BookmarkAddOutlined';
import PlaylistAddIcon from '@mui/icons-material/PlaylistAdd';
import AppShell from '../components/layout/AppShell';
import SearchBar from '../components/search/SearchBar';
import AddToCollectionDialog from '../components/resource/AddToCollectionDialog';
import BulkAddToCollectionDialog from '../components/resource/BulkAddToCollectionDialog';
import { useSearch } from '../hooks/useSearch';
import { useNavigate } from 'react-router-dom';
import type { TestimonialSearchHitDto } from '../types/api';

function viewerType(mimeType: string): string | null {
  if (mimeType.startsWith('video/')) return 'video';
  if (mimeType.startsWith('audio/')) return 'audio';
  if (mimeType.startsWith('image/')) return 'image';
  if (mimeType === 'application/pdf' ||
      mimeType.startsWith('application/vnd.openxmlformats') ||
      mimeType.startsWith('application/vnd.ms-') ||
      mimeType.startsWith('application/vnd.oasis')) return 'pdf';
  if (mimeType.startsWith('text/') || mimeType === 'application/json') return 'text';
  return null;
}

export default function SearchPage() {
  const { query, setQuery, results, isLoading } = useSearch();
  const navigate = useNavigate();
  const [collectionTarget, setCollectionTarget] = useState<{ id: number; type: 'resource' | 'testimonial' } | null>(null);
  const [bulkDialogOpen, setBulkDialogOpen] = useState(false);

  return (
    <AppShell>
      <Typography variant="h5" gutterBottom>Suche in Dateien und Erfahrungsberichten</Typography>
      <Alert severity="info" sx={{ mb: 2 }}>
        Hier werden <strong>Dateien</strong> (Videos inkl. Untertitel, Dokumente, Bilder) sowie
        textuelle <strong>Erfahrungsberichte</strong> durchsucht.
        Für eine gezielte Suche nur in Erfahrungsberichten steht die separate Erfahrungsberichte-Seite zur Verfügung.
      </Alert>
      <Box sx={{ mb: 3 }}>
        <SearchBar value={query} onChange={setQuery} />
        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
          Mehrere Begriffe werden mit UND verknüpft – alle Begriffe müssen enthalten sein. Präfixsuche: „erfahr" findet auch „Erfahrung".
        </Typography>
      </Box>
      <Box>
        {query.trim().length > 0 && query.trim().length < 2 && (
          <Typography color="text.secondary">Bitte mindestens 2 Zeichen eingeben…</Typography>
        )}
        {isLoading && <CircularProgress />}
        {results && !isLoading && (
            <>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                <Typography variant="body2" color="text.secondary">
                  {results.totalHits} Treffer
                </Typography>
                <Box sx={{ flexGrow: 1 }} />
                {(results.hits.length > 0 || results.testimonialHits.length > 0) && (
                  <Tooltip title="Alle Treffer zur Sammlung hinzufügen">
                    <IconButton size="small" onClick={() => setBulkDialogOpen(true)}>
                      <PlaylistAddIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
              </Box>
              <List disablePadding>
                {results.hits.map(hit => (
                  <ListItem
                    key={hit.resourceId}
                    divider
                    disablePadding
                    secondaryAction={
                      <Tooltip title="Zur Sammlung hinzufügen">
                        <IconButton edge="end" size="small" onClick={() => setCollectionTarget({ id: hit.resourceId, type: 'resource' })}>
                          <BookmarkAddOutlinedIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    }
                  >
                    <ListItemButton sx={{ pr: 6 }} onClick={() => {
                      const type = viewerType(hit.mimeType);
                      const firstSnippet = hit.snippets[0];
                      const qParam = query ? `&q=${encodeURIComponent(query)}` : '';
                      const langParam = firstSnippet?.language ? `&lang=${encodeURIComponent(firstSnippet.language)}` : '';
                      if (type === 'text') { navigate(`/resources/${hit.resourceId}/edit`); return; }
                      const target = type
                        ? `/folders/${hit.folderId}?view=${hit.resourceId}&type=${type}${qParam}${langParam}`
                        : `/folders/${hit.folderId}`;
                      navigate(target);
                    }}>
                      <ListItemText
                        primary={hit.name}
                        secondary={
                          <>
                            <span>{hit.mimeType} · {(hit.size / 1024).toFixed(1)} KB · {hit.folderPath ?? ''}</span>
                            {hit.snippets.map((s, i) => {
                              const type = viewerType(hit.mimeType);
                              const tParam = s.timestampSeconds == null ? '' : `&t=${s.timestampSeconds}`;
                              const langParam = s.language ? `&lang=${encodeURIComponent(s.language)}` : '';
                              const qParam = query ? `&q=${encodeURIComponent(query)}` : '';
                              const snippetTarget = type === 'text'
                                ? `/resources/${hit.resourceId}/edit`
                                : type
                                ? `/folders/${hit.folderId}?view=${hit.resourceId}&type=${type}${qParam}${langParam}${tParam}`
                                : null;
                              return (
                                <span
                                  key={i}
                                  onClick={snippetTarget ? (e) => { e.stopPropagation(); navigate(snippetTarget); } : undefined}
                                  style={{
                                    display: 'block', marginTop: 2, fontStyle: 'italic',
                                    cursor: snippetTarget ? 'pointer' : 'default',
                                    textDecoration: snippetTarget ? 'underline dotted' : 'none',
                                  }}
                                >
                                  {s.timestamp && (
                                    <span style={{ fontStyle: 'normal', fontWeight: 600, marginRight: 6 }}>
                                      [{s.timestamp}]
                                    </span>
                                  )}
                                  {s.text}
                                </span>
                              );
                            })}
                          </>
                        }
                      />
                    </ListItemButton>
                  </ListItem>
                ))}
              </List>
              {results.testimonialHits.length > 0 && (
                <>
                  <Typography variant="subtitle2" color="text.secondary" sx={{ mt: 3, mb: 1 }}>
                    Erfahrungsberichte
                  </Typography>
                  <List disablePadding>
                    {results.testimonialHits.map((t: TestimonialSearchHitDto) => (
                      <ListItem
                        key={t.id}
                        divider
                        disablePadding
                        secondaryAction={
                          <Tooltip title="Zur Sammlung hinzufügen">
                            <IconButton edge="end" size="small" onClick={() => setCollectionTarget({ id: t.id, type: 'testimonial' })}>
                              <BookmarkAddOutlinedIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        }
                      >
                        <ListItemButton sx={{ pr: 6 }} onClick={() => navigate(`/testimonials?id=${t.id}`)}>
                          <ListItemText
                            primary={
                              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                                <span>{t.authorName}</span>
                                <Chip label="Testimonial" size="small" variant="outlined" color="secondary" />
                                {t.tags?.split(',').map(tag => tag.trim()).filter(Boolean).map(tag => (
                                  <Chip key={tag} label={tag} size="small" color="info" sx={{ opacity: 0.85 }} />
                                ))}
                              </Box>
                            }
                            secondary={t.text.length > 150 ? t.text.slice(0, 150) + '…' : t.text}
                          />
                        </ListItemButton>
                      </ListItem>
                    ))}
                  </List>
                </>
              )}
              {results.hits.length === 0 && results.testimonialHits.length === 0 && query && (
                <Typography color="text.secondary">Keine Ergebnisse für „{query}"</Typography>
              )}
            </>
          )}
      </Box>
      {collectionTarget && (
        <AddToCollectionDialog
          open
          itemId={collectionTarget.id}
          itemType={collectionTarget.type}
          onClose={() => setCollectionTarget(null)}
        />
      )}
      {bulkDialogOpen && results && (
        <BulkAddToCollectionDialog
          open
          resourceIds={results.hits.map(h => h.resourceId)}
          testimonialIds={results.testimonialHits.map(t => t.id)}
          onClose={() => setBulkDialogOpen(false)}
        />
      )}
    </AppShell>
  );
}
