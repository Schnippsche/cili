import type {ReactNode} from 'react';
import {Children} from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  Box,
  Container,
  Divider,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import AppShell from '../components/layout/AppShell';
import handbuchContent from '../../../docs/HANDBUCH.md?raw';

export function slugify(text: string): string {
  // GitHub-Flavored-Markdown-Slug-Konvention: jedes einzelne Leerzeichen wird zu einem
  // Bindestrich, OHNE aufeinanderfolgende Bindestriche zusammenzufassen. Das ist wichtig,
  // weil ein entferntes Sonderzeichen (z.B. "&") die Leerzeichen davor/danach nicht
  // "mitnimmt" — daraus entsteht ein doppelter Bindestrich, den die handgeschriebenen
  // Anker-Links in docs/HANDBUCH.md exakt so erwarten (siehe HelpPage.test.ts).
  return text
  .toLowerCase()
  .replace(/[^\p{L}\p{N}\s-]/gu, '')
  .trim()
  .replace(/\s/g, '-');
}

function headingId(children: ReactNode): string {
  const text = Children.toArray(children)
  .map(child => (typeof child === 'string' ? child : ''))
  .join('');
  return slugify(text);
}

export default function HelpPage() {
  return (
      <AppShell>
        <Container maxWidth="md" sx={{py: 4}}>
          <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                h1: ({children}) => (
                    <Typography id={headingId(children)} variant="h4" fontWeight="bold" gutterBottom
                                sx={{mt: 2}}>
                      {children}
                    </Typography>
                ),
                h2: ({children}) => (
                    <Typography id={headingId(children)} variant="h5" fontWeight="bold" gutterBottom
                                sx={{mt: 4, mb: 1}}>
                      {children}
                    </Typography>
                ),
                h3: ({children}) => (
                    <Typography id={headingId(children)} variant="h6" fontWeight="bold" gutterBottom
                                sx={{mt: 3}}>
                      {children}
                    </Typography>
                ),
                h4: ({children}) => (
                    <Typography id={headingId(children)} variant="subtitle1" fontWeight="bold"
                                gutterBottom sx={{mt: 2}}>
                      {children}
                    </Typography>
                ),
                p: ({children}) => (
                    <Typography variant="body1" paragraph sx={{lineHeight: 1.8}}>
                      {children}
                    </Typography>
                ),
                li: ({children}) => (
                    <Box component="li" sx={{mb: 0.5}}>
                      <Typography variant="body1" component="span">{children}</Typography>
                    </Box>
                ),
                ul: ({children}) => (
                    <Box component="ul" sx={{pl: 3, mb: 2}}>{children}</Box>
                ),
                ol: ({children}) => (
                    <Box component="ol" sx={{pl: 3, mb: 2}}>{children}</Box>
                ),
                hr: () => <Divider sx={{my: 3}}/>,
                blockquote: ({children}) => (
                    <Box
                        sx={{
                          borderLeft: 4, borderColor: 'primary.main', pl: 2, ml: 0, my: 2,
                          bgcolor: 'action.hover', py: 1, borderRadius: '0 4px 4px 0',
                        }}
                    >
                      {children}
                    </Box>
                ),
                code: ({children, className}) => {
                  const isBlock = className != null;
                  return isBlock ? (
                      <Box
                          component="pre"
                          sx={{
                            bgcolor: 'action.hover', p: 2, borderRadius: 1, overflowX: 'auto',
                            fontFamily: 'monospace', fontSize: '0.875rem', my: 2,
                          }}
                      >
                        <code>{children}</code>
                      </Box>
                  ) : (
                      <Box
                          component="code"
                          sx={{
                            bgcolor: 'action.hover', px: 0.75, py: 0.25, borderRadius: 0.5,
                            fontFamily: 'monospace', fontSize: '0.875em',
                          }}
                      >
                        {children}
                      </Box>
                  );
                },
                table: ({children}) => (
                    <TableContainer component={Paper} variant="outlined" sx={{my: 2}}>
                      <Table size="small">{children}</Table>
                    </TableContainer>
                ),
                thead: ({children}) => <TableHead
                    sx={{bgcolor: 'action.hover'}}>{children}</TableHead>,
                tbody: ({children}) => <TableBody>{children}</TableBody>,
                tr: ({children}) => <TableRow>{children}</TableRow>,
                th: ({children}) => (
                    <TableCell sx={{fontWeight: 'bold'}}>{children}</TableCell>
                ),
                td: ({children}) => <TableCell>{children}</TableCell>,
                a: ({href, children}) => (
                    <Box
                        component="a"
                        href={href}
                        onClick={href?.startsWith('#') ? (e) => {
                          e.preventDefault();
                          document.getElementById(href.slice(1))?.scrollIntoView({behavior: 'smooth'});
                        } : undefined}
                        sx={{color: 'primary.main', textDecoration: 'underline', cursor: 'pointer'}}
                    >
                      {children}
                    </Box>
                ),
              }}
          >
            {handbuchContent}
          </ReactMarkdown>
        </Container>
      </AppShell>
  );
}
