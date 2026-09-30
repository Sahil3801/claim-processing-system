export const formatCurrency = (value: number) => new Intl.NumberFormat('en-US', {
  style: 'currency', currency: 'USD', maximumFractionDigits: 2,
}).format(value);

export const formatDate = (value: string) => new Intl.DateTimeFormat('en-US', {
  dateStyle: 'medium', timeStyle: 'short',
}).format(new Date(value));

export const formatDay = (value: string) => new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' }).format(new Date(value));

export const formatTime = (value: string) => new Intl.DateTimeFormat('en-US', { timeStyle: 'short' }).format(new Date(value));

/** A YYYY-MM-DD report date, read as a calendar day (no time-zone shift). */
export const formatReportDate = (value: string) => new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' })
  .format(new Date(`${value}T00:00:00`));

/** Display only: "MEDICAL" reads as "Medical"; mixed-case values are shown exactly as entered. */
export const formatClaimType = (value: string) => value === value.toUpperCase()
  ? value.toLowerCase().replace(/(^|[\s_-])(\p{L})/gu, (_, sep: string, letter: string) => sep + letter.toUpperCase())
  : value;

export const isoDate = (date: Date) => date.toISOString().slice(0, 10);
