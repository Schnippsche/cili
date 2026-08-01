import {useMemo} from 'react';
import {Box, Button, CircularProgress, Container, Typography} from '@mui/material';
import {useParams} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {getShareInfo, getShareSubtitleUrl, ShareExpiredError} from '../api/share';
import {BASE} from '../api/base';
import VideoPlayer from '../components/viewer/VideoPlayer';
import PdfViewer from '../components/viewer/PdfViewer';

export default function SharePage() {
  const {token} = useParams<{ token: string }>();

  const {data: info, isLoading, isError, error} = useQuery({
    queryKey: ['shareInfo', token],
    queryFn: () => getShareInfo(token!),
    retry: false,
    enabled: !!token,
  });

  const subtitles = useMemo(
      () => (info?.subtitles ?? []).map(t => ({
        src: getShareSubtitleUrl(token!, t.id),
        label: t.label ?? t.languageCode,
        language: t.languageCode,
      })),
      [info, token],
  );

  if (isLoading) {
    return (
        <Box sx={{display: 'flex', justifyContent: 'center', mt: 10}}>
          <CircularProgress/>
        </Box>
    );
  }

  if (isError || !info) {
    const expired = error instanceof ShareExpiredError;
    return (
        <Container maxWidth="sm" sx={{mt: 10, textAlign: 'center'}}>
          <Typography variant="h5" gutterBottom>
            {expired ? 'Link abgelaufen' : 'Link ungültig'}
          </Typography>
          <Typography color="text.secondary">
            {expired
                ? 'Dieser Freigabe-Link ist abgelaufen. Bitte fordere einen neuen Link an.'
                : 'Dieser Link ist ungültig oder wurde widerrufen.'}
          </Typography>
        </Container>
    );
  }

  const streamUrl = `${BASE}/api/share/${token}`;
  const isVideo = info.mimeType.startsWith('video/');
  const isAudio = info.mimeType.startsWith('audio/');
  const isImage = info.mimeType.startsWith('image/');
  const isDocument =
      info.mimeType === 'application/pdf' ||
      info.mimeType.includes('officedocument') ||
      info.mimeType.includes('opendocument');

  return (
      <Container maxWidth="lg" sx={{mt: 4}}>
        <Typography variant="h6" gutterBottom sx={{mb: 2}}>
          {info.originalName}
        </Typography>
        {(isVideo || isAudio) && (
            <VideoPlayer
                src={streamUrl}
                mimeType={info.mimeType}
                audioOnly={isAudio}
                subtitles={subtitles}
            />
        )}
        {isDocument && <PdfViewer src={streamUrl}/>}
        {isImage && (
            <Box sx={{textAlign: 'center'}}>
              <Box
                  component="img"
                  src={streamUrl}
                  alt={info.originalName}
                  sx={{maxWidth: '100%', maxHeight: '80vh', objectFit: 'contain'}}
              />
            </Box>
        )}
        {!isVideo && !isAudio && !isDocument && !isImage && (
            <Button
                variant="contained"
                component="a"
                href={streamUrl}
                download={info.originalName}
            >
              Herunterladen
            </Button>
        )}
      </Container>
  );
}
