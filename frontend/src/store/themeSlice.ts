import { createSlice } from '@reduxjs/toolkit';

type ThemeMode = 'light' | 'dark';

const stored = localStorage.getItem('themeMode') as ThemeMode | null;

const themeSlice = createSlice({
  name: 'theme',
  initialState: { mode: (stored ?? 'light') as ThemeMode },
  reducers: {
    toggleTheme(state) {
      state.mode = state.mode === 'light' ? 'dark' : 'light';
      localStorage.setItem('themeMode', state.mode);
    },
  },
});

export const { toggleTheme } = themeSlice.actions;
export default themeSlice.reducer;
