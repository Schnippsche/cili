import {useState} from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Divider,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
} from '@mui/material';
import {useQueryClient} from '@tanstack/react-query';
import {uploadSubtitle} from '../../api/resources';
import {useLanguageOptions} from '../../hooks/useLanguageOptions';

interface Props {
  resourceId: number;
}

export default function SubtitleUploadSection({resourceId}: Readonly<Props>) {
  const qc = useQueryClient();
  const {data: languages = [], isLoading: langsLoading, isError: langsError} = useLanguageOptions();

  const [langCode, setLangCode] = useState('');
  const [customLang, setCustomLang] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploadKey, setUploadKey] = useState(0);

  const effectiveLang = langCode === '__custom' ? customLang.trim() : langCode;

  const detectFormat = (f: File): 'SRT' | 'VTT' | null => {
    const ext = f.name.split('.').pop()?.toLowerCase();
    if (ext === 'srt') return 'SRT';
    if (ext === 'vtt') return 'VTT';
    return null;
  };

  const handleUpload = async () => {
    if (!file || !effectiveLang) return;
    const format = detectFormat(file);
    if (!format) {
      setUploadError('Unbekanntes Format. Bitte eine .srt oder .vtt Datei wählen.');
      return;
    }
    setUploading(true);
    setUploadError(null);
    try {
      await uploadSubtitle(resourceId, effectiveLang, null, format, file);
      setFile(null);
      setLangCode('');
      setCustomLang('');
      setUploadKey(prev => prev + 1);
      void qc.invalidateQueries({queryKey: ['subtitles', resourceId]});
    } catch (err: unknown) {
      const msg = (err as { response?: { status?: number } })?.response?.status === 409
          ? `Für die Sprache "${effectiveLang}" existiert bereits ein Untertitel.`
          : 'Upload fehlgeschlagen.';
      setUploadError(msg);
    } finally {
      setUploading(false);
    }
  };

  return (
      <>
        <Divider sx={{my: 1}}/>
        <Typography variant="caption" color="text.secondary">Untertitel hinzufügen</Typography>
        <Box sx={{display: 'flex', flexDirection: 'column', gap: 1, mt: 1}}>
          {langsError && (
              <Alert severity="error" sx={{mb: 1}}>Sprachen konnten nicht geladen werden.</Alert>
          )}
          <FormControl size="small" fullWidth>
            <InputLabel>Sprache</InputLabel>
            <Select value={langCode} label="Sprache" onChange={e => setLangCode(e.target.value)}
                    disabled={langsLoading}>
              {languages.map(l => (
                  <MenuItem key={l.code} value={l.code}>{l.label}</MenuItem>
              ))}
              <MenuItem value="__custom">Andere…</MenuItem>
            </Select>
          </FormControl>

          {langCode === '__custom' && (
              <TextField
                  size="small" fullWidth label="Sprachcode (z.B. nl, ru)"
                  value={customLang} onChange={e => setCustomLang(e.target.value)}
              />
          )}

          <Button variant="outlined" component="label" size="small" fullWidth>
            {file ? file.name : 'Datei wählen…'}
            <input key={uploadKey} type="file" hidden accept=".srt,.vtt"
                   onChange={e => setFile(e.target.files?.[0] ?? null)}/>
          </Button>

          {uploadError && <Alert severity="error" sx={{py: 0}}>{uploadError}</Alert>}

          <Button variant="contained" size="small"
                  disabled={!file || !effectiveLang || uploading} onClick={handleUpload}>
            {uploading ? <CircularProgress size={16}/> : 'Hochladen'}
          </Button>
        </Box>
      </>
  );
}
