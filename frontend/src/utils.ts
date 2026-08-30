export const formatCurrency = (value: number) => new Intl.NumberFormat('en-US', {
  style: 'currency', currency: 'USD', maximumFractionDigits: 2,
}).format(value);

export const formatDate = (value: string) => new Intl.DateTimeFormat('en-US', {
  dateStyle: 'medium', timeStyle: 'short',
}).format(new Date(value));

export const isoDate = (date: Date) => date.toISOString().slice(0, 10);
