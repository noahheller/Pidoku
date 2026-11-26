/**
 * The Ko-fi page tips go to, e.g. 'https://ko-fi.com/yourname'. Null hides the "Support Pidoku"
 * card on /me until it's set; a supporter still sees their badge. Ko-fi's webhook for this page
 * must point at /api/kofi/webhook (see docs/DEVELOPMENT.md, "Ko-fi supporters").
 */
export const KOFI_URL: string | null = 'https://ko-fi.com/noahheller';
