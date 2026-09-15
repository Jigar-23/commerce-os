import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export * from './tokens';
export * from './tailwind-preset';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
