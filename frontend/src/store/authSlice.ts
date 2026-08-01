import {createSlice, PayloadAction} from '@reduxjs/toolkit';
import type {LoginUserInfo} from '../types/api';

interface AuthState {
  user: LoginUserInfo | null;
  accessToken: string | null;
  isAuthenticated: boolean;
}

function loadUser(): LoginUserInfo | null {
  try {
    const raw = localStorage.getItem('user');
    return raw ? (JSON.parse(raw) as LoginUserInfo) : null;
  } catch {
    return null;
  }
}

const initialState: AuthState = {
  user: loadUser(),
  accessToken: localStorage.getItem('accessToken'),
  isAuthenticated: !!localStorage.getItem('accessToken'),
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials(state, action: PayloadAction<{
      user: LoginUserInfo;
      accessToken: string;
      refreshToken: string
    }>) {
      state.user = action.payload.user;
      state.accessToken = action.payload.accessToken;
      state.isAuthenticated = true;
      localStorage.setItem('accessToken', action.payload.accessToken);
      localStorage.setItem('refreshToken', action.payload.refreshToken);
      localStorage.setItem('user', JSON.stringify(action.payload.user));
    },
    setAccessToken(state, action: PayloadAction<string>) {
      state.accessToken = action.payload;
      localStorage.setItem('accessToken', action.payload);
    },
    clearCredentials(state) {
      state.user = null;
      state.accessToken = null;
      state.isAuthenticated = false;
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('user');
    },
  },
});

export const {setCredentials, setAccessToken, clearCredentials} = authSlice.actions;
export default authSlice.reducer;
