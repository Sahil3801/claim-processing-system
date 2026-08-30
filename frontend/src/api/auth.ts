import { api } from './client';
import type { AuthTokenResponse, User } from '../types';

export async function login(username: string, password: string): Promise<AuthTokenResponse> {
  const { data } = await api.post<AuthTokenResponse>('/auth/login', { username, password });
  return data;
}

export async function register(username: string, email: string, password: string): Promise<User> {
  const { data } = await api.post<User>('/auth/register', { username, email, password });
  return data;
}
