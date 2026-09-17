/**
 * Masks customer name for public display:
 * "Budi Santoso" -> "Budi S******"
 * "Andi" -> "An**"
 */
export function maskCustomerName(name: string): string {
  if (!name) return '';
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) {
    const single = parts[0];
    if (single.length <= 2) return single + '*';
    return single.slice(0, 2) + '*'.repeat(Math.max(single.length - 2, 2));
  }
  const firstWord = parts[0];
  const remaining = parts.slice(1).map((p) => {
    if (p.length === 0) return '';
    return p[0] + '*'.repeat(Math.max(p.length - 1, 3));
  }).join(' ');
  return `${firstWord} ${remaining}`;
}

/**
 * Masks phone number for public display:
 * "6285157056604" -> "6285****6604"
 */
export function maskPhone(phone: string): string {
  if (!phone) return '';
  const digits = phone.replace(/\D/g, '');
  if (digits.length < 8) return digits.slice(0, 2) + '****';
  const prefix = digits.slice(0, 4);
  const suffix = digits.slice(-4);
  return `${prefix}****${suffix}`;
}

/**
 * Masks email for public display:
 * "budi.santoso@warung.id" -> "b***@warung.id"
 */
export function maskEmail(email: string): string {
  if (!email || !email.includes('@')) return '';
  const [user, domain] = email.split('@');
  if (user.length <= 1) return `${user}***@${domain}`;
  return `${user[0]}***@${domain}`;
}
