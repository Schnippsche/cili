import {useCallback, useEffect} from 'react';
import {useDispatch, useSelector} from 'react-redux';
import {useNavigate} from 'react-router-dom';
import {useQueryClient} from '@tanstack/react-query';
import type {AppDispatch, RootState} from '../store/store';
import {clearCredentials, setCredentials} from '../store/authSlice';
import {login as loginApi, logout as logoutApi} from '../api/auth';
import type {LoginRequest} from '../types/api';

export function useAuth() {
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const {user, accessToken, isAuthenticated} = useSelector((s: RootState) => s.auth);

  useEffect(() => {
    const onLogout = () => {
      dispatch(clearCredentials());
      qc.clear();
      navigate('/login');
    };
    globalThis.addEventListener('auth:logout', onLogout);
    return () => globalThis.removeEventListener('auth:logout', onLogout);
  }, [dispatch, navigate, qc]);

  const login = useCallback(async (req: LoginRequest) => {
    const resp = await loginApi(req);
    qc.clear();
    dispatch(setCredentials({
      user: resp.user,
      accessToken: resp.accessToken,
      refreshToken: resp.refreshToken
    }));
    navigate('/');
  }, [dispatch, navigate, qc]);

  const logout = useCallback(async () => {
    const rt = localStorage.getItem('refreshToken') ?? '';
    try {
      await logoutApi(rt);
    } catch { /* ignore */
    }
    dispatch(clearCredentials());
    qc.clear();
    navigate('/login');
  }, [dispatch, navigate, qc]);

  return {user, accessToken, isAuthenticated, login, logout};
}
