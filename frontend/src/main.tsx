import {StrictMode, useMemo} from 'react';
import ReactDOM from 'react-dom/client';
import {Provider, useSelector} from 'react-redux';
import {BrowserRouter} from 'react-router-dom';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {createTheme, CssBaseline, ThemeProvider} from '@mui/material';
import App from './App';
import type {RootState} from './store/store';
import {store} from './store/store';

const queryClient = new QueryClient({defaultOptions: {queries: {retry: 1, staleTime: 30_000}}});

function ThemedApp() {
  const mode = useSelector((s: RootState) => s.theme.mode);
  const theme = useMemo(() => createTheme({
    palette: {mode, primary: {main: '#1976d2'}},
    components: {
      MuiDialogContent: {
        styleOverrides: {
          root: {
            // MUI v6 sets paddingTop:0 when a DialogTitle is present, which clips
            // the floating label of the first TextField. Force consistent top padding.
            paddingTop: '20px !important',
          },
        },
      },
    },
  }), [mode]);

  return (
      <ThemeProvider theme={theme}>
        <CssBaseline/>
        {/* basename = import.meta.env.BASE_URL: '/' in Dev, '/cili/' im Production-Build */}
        <BrowserRouter basename={import.meta.env.BASE_URL}>
          <App/>
        </BrowserRouter>
      </ThemeProvider>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <Provider store={store}>
        <QueryClientProvider client={queryClient}>
          <ThemedApp/>
        </QueryClientProvider>
      </Provider>
    </StrictMode>
);
