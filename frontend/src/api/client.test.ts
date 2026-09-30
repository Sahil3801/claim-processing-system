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

  it('turns field violations into readable sentences', () => {
    expect(errorMessage(withResponse(400, { violations: {
      claimAmount: 'claimAmount must be greater than zero',
      description: 'description is required',
    } }))).toBe('Claim amount must be greater than zero. Description is required.');
  });

  it('replaces developer-facing messages for known error codes', () => {
    expect(errorMessage(withResponse(404, { error: 'CLAIM_NOT_FOUND', message: 'Claim not found with id: 99' })))
      .toBe('This claim could not be found. Check the claim number and try again.');
    expect(errorMessage(withResponse(403, { error: 'UNAUTHORIZED_CLAIM_ACCESS', message: 'User alice is not authorized to access claim 5' })))
      .toBe('You do not have access to this claim.');
  });

  it('explains an empty 401 as an expired session', () => {
    expect(errorMessage(withResponse(401, ''))).toBe('Your session has expired. Please sign in again.');
  });

  it('replaces raw transport errors with plain language', () => {
    expect(errorMessage(new AxiosError('timeout of 15000ms exceeded', 'ECONNABORTED'))).toMatch(/taking too long/);
    expect(errorMessage(new AxiosError('Network Error', 'ERR_NETWORK'))).toMatch(/Could not reach/);
    expect(errorMessage(withResponse(502, ''))).toMatch(/unavailable right now/);
  });
});
