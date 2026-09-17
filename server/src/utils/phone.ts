/**
 * Normalizes Indonesian mobile / WhatsApp number into standard format: 628xxxxxxxxxx
 */
export function normalizeIndonesianPhone(phone: string): { valid: boolean; normalized: string; error?: string } {
  if (!phone || typeof phone !== 'string') {
    return { valid: false, normalized: '', error: 'Nomor WhatsApp wajib diisi.' };
  }

  // Remove all whitespace, dashes, plus signs, brackets, dots
  let cleaned = phone.replace(/[\s\-\+\(\)\.]/g, '').trim();

  // Handle common Indonesian prefixes:
  // 08xx -> 628xx
  if (cleaned.startsWith('08')) {
    cleaned = '628' + cleaned.slice(2);
  } else if (cleaned.startsWith('8')) {
    cleaned = '628' + cleaned.slice(1);
  } else if (cleaned.startsWith('6208')) {
    cleaned = '628' + cleaned.slice(4);
  }

  // Indonesian mobile numbers: 628 followed by 8 to 12 digits (total length 11 to 15 digits)
  const indonesianMobileRegex = /^628\d{8,12}$/;
  if (!indonesianMobileRegex.test(cleaned)) {
    return {
      valid: false,
      normalized: '',
      error: 'Nomor WhatsApp tidak valid. Masukkan nomor HP/WhatsApp Indonesia yang aktif (contoh: 081234567890).'
    };
  }

  return { valid: true, normalized: cleaned };
}
