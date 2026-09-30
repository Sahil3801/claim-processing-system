import { AxiosError, type AxiosResponse } from 'axios';
import { describe, expect, it } from 'vitest';
import { errorMessage } from './client';

const withResponse = (status: number, data: unknown) =>
  new AxiosError('raw', String(status), undefined, undefined, { status, data } as AxiosResponse);

describe('errorMessage', () => {
  it('keeps the API message for client errors', () => {
    expect(errorMessage(withResponse(409, { message: 'Claim was changed by another request' })))
      .toBe('Claim was changed by another request');
  });

  it('joins field violations', () => {
    expect(errorMessage(withResponse(400, { violations: { claimAmount: 'must be positive' } }))).toBe('must be positive');
  });

  it('explains an empty 401 as a credentials problem', () => {
    expect(errorMessage(withResponse(401, ''))).toMatch(/credentials are incorrect/);
  });

  it('replaces raw transport errors with plain language', () => {
    expect(errorMessage(new AxiosError('timeout of 15000ms exceeded', 'ECONNABORTED'))).toMatch(/taking too long/);
    expect(errorMessage(new AxiosError('Network Error', 'ERR_NETWORK'))).toMatch(/Could not reach/);
    expect(errorMessage(withResponse(502, ''))).toMatch(/unavailable right now/);
  });
});
